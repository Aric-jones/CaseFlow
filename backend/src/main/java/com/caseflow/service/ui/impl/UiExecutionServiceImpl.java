package com.caseflow.service.ui.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.caseflow.engine.ui.UiActionExecutor;
import com.caseflow.engine.ui.UiBrowserDriver;
import com.caseflow.engine.ui.UiDriverFactory;
import com.caseflow.entity.ui.*;
import com.caseflow.mapper.ui.*;
import com.caseflow.service.ui.UiEnvironmentService;
import com.caseflow.service.ui.UiExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UiExecutionServiceImpl extends ServiceImpl<UiExecutionMapper, UiExecution>
        implements UiExecutionService {

    private final UiDriverFactory driverFactory;
    private final UiActionExecutor actionExecutor;
    private final UiEnvironmentService envService;
    private final UiTestCaseMapper caseMapper;
    private final UiTestStepMapper stepMapper;
    private final UiElementMapper elementMapper;
    private final UiScenarioMapper scenarioMapper;
    private final UiScenarioCaseMapper scenarioCaseMapper;
    private final UiTestPlanMapper planMapper;
    private final UiPlanScenarioMapper planScenarioMapper;
    private final UiExecutionDetailMapper detailMapper;

    @Value("${ui.screenshot.dir:uploads/ui-screenshots}")
    private String screenshotDirRaw;

    private String screenshotDir;

    @jakarta.annotation.PostConstruct
    void initScreenshotDir() {
        Path p = Paths.get(screenshotDirRaw);
        screenshotDir = p.isAbsolute() ? p.toString() : p.toAbsolutePath().toString();
        log.info("UI screenshot dir: {}", screenshotDir);
    }

    @Async("uiExecutorPool")
    @Override
    public CompletableFuture<Void> executeCaseAsync(String executionId, String caseId) {
        UiExecution exec = getById(executionId);
        UiTestCase tc = caseMapper.selectById(caseId);
        if (exec == null || tc == null) return CompletableFuture.completedFuture(null);

        int expectedTotal = countEnabledSteps(caseId);
        exec.setTotalSteps(expectedTotal);
        updateById(exec);

        String driverType = tc.getDriverType() != null ? tc.getDriverType() : "PLAYWRIGHT";
        String browserType = tc.getBrowserType() != null ? tc.getBrowserType() : "CHROMIUM";
        boolean headless = tc.getHeadless() == null || tc.getHeadless() == 1;
        int w = tc.getWindowWidth() != null ? tc.getWindowWidth() : 1920;
        int h = tc.getWindowHeight() != null ? tc.getWindowHeight() : 1080;

        try (UiBrowserDriver driver = driverFactory.create(driverType, browserType, headless, w, h)) {
            doExecuteCase(driver, exec, tc, null, true);
        } catch (Exception e) {
            log.error("UI case execution failed: {}", e.getMessage(), e);
            exec.setStatus("ERROR");
            exec.setFinishedAt(LocalDateTime.now());
            updateById(exec);
        }
        return CompletableFuture.completedFuture(null);
    }

    private String resolveBaseUrl(UiExecution exec, UiTestCase tc) {
        if (exec.getEnvironmentId() != null) {
            UiEnvironment env = envService.getById(exec.getEnvironmentId());
            if (env != null && env.getBaseUrl() != null && !env.getBaseUrl().isBlank()) {
                return env.getBaseUrl();
            }
        }
        return tc.getBaseUrl();
    }

    private boolean doExecuteCase(UiBrowserDriver driver, UiExecution exec, UiTestCase tc, String scenarioId, boolean standalone) {
        return doExecuteCase(driver, exec, tc, scenarioId, standalone, null);
    }

    private boolean doExecuteCase(UiBrowserDriver driver, UiExecution exec, UiTestCase tc, String scenarioId, boolean standalone, int[] stepCounter) {
        List<UiTestStep> steps = stepMapper.selectList(
                new LambdaQueryWrapper<UiTestStep>()
                        .eq(UiTestStep::getCaseId, tc.getId())
                        .eq(UiTestStep::getEnabled, 1)
                        .orderByAsc(UiTestStep::getSortOrder));

        Set<String> elementIds = steps.stream()
                .map(UiTestStep::getElementId)
                .filter(eid -> eid != null && !eid.isBlank())
                .collect(Collectors.toSet());
        Map<String, UiElement> elemMap = elementIds.isEmpty() ? Map.of() :
                elementMapper.selectBatchIds(elementIds).stream()
                        .collect(Collectors.toMap(UiElement::getId, e -> e));

        String baseUrl = resolveBaseUrl(exec, tc);

        int passed = 0, failed = 0, errored = 0;
        boolean allPass = true;

        for (int i = 0; i < steps.size(); i++) {
            UiTestStep step = steps.get(i);
            UiElement element = step.getElementId() != null ? elemMap.get(step.getElementId()) : null;

            int order = (stepCounter != null) ? stepCounter[0]++ : i;

            long start = System.currentTimeMillis();
            UiActionExecutor.StepResult result = actionExecutor.execute(driver, step, element, baseUrl);
            long duration = System.currentTimeMillis() - start;

            String screenshotPath = saveScreenshot(result.screenshot(), exec.getId(), order);

            UiExecutionDetail detail = new UiExecutionDetail();
            detail.setExecutionId(exec.getId());
            detail.setScenarioId(scenarioId);
            detail.setCaseId(tc.getId());
            detail.setStepOrder(order);
            detail.setStepType(step.getStepType());
            detail.setElementName(element != null ? element.getName() : step.getLocatorValue());
            detail.setActionDesc("[" + (tc.getName() != null ? tc.getName() : "") + "] " + result.actionDesc());
            detail.setStatus(result.status());
            detail.setDurationMs(duration);
            detail.setScreenshotPath(screenshotPath);
            detail.setErrorMessage(result.errorMessage());
            try { detail.setPageUrl(driver.getCurrentUrl()); } catch (Exception ignored) {}
            detailMapper.insert(detail);

            switch (result.status()) {
                case "PASS": passed++; break;
                case "FAIL": failed++; break;
                default: errored++; break;
            }

            if (!"PASS".equals(result.status())) {
                allPass = false;
                break;
            }
        }

        if (standalone) {
            int total = steps.size();
            exec.setTotalSteps(total);
            exec.setPassedSteps(passed);
            exec.setFailedSteps(failed);
            exec.setErrorSteps(errored);
            exec.setSkippedSteps(total - passed - failed - errored);
            exec.setStatus(allPass ? "PASS" : "FAIL");
            exec.setFinishedAt(LocalDateTime.now());
            updateById(exec);
        }

        return allPass;
    }

    @Async("uiExecutorPool")
    @Override
    public CompletableFuture<Void> executeScenarioAsync(String executionId, String scenarioId) {
        UiExecution exec = getById(executionId);
        UiScenario scenario = scenarioMapper.selectById(scenarioId);
        if (exec == null || scenario == null) return CompletableFuture.completedFuture(null);

        List<UiScenarioCase> scenarioCases = scenarioCaseMapper.selectList(
                new LambdaQueryWrapper<UiScenarioCase>()
                        .eq(UiScenarioCase::getScenarioId, scenarioId)
                        .eq(UiScenarioCase::getEnabled, 1)
                        .orderByAsc(UiScenarioCase::getSortOrder));

        int expectedTotal = 0;
        for (UiScenarioCase sc : scenarioCases) {
            expectedTotal += countEnabledSteps(sc.getCaseId());
        }
        exec.setTotalSteps(expectedTotal);
        updateById(exec);

        UiTestCase firstCase = scenarioCases.isEmpty() ? null : caseMapper.selectById(scenarioCases.get(0).getCaseId());
        String driverType = firstCase != null && firstCase.getDriverType() != null ? firstCase.getDriverType() : "PLAYWRIGHT";
        String browserType = firstCase != null && firstCase.getBrowserType() != null ? firstCase.getBrowserType() : "CHROMIUM";
        boolean headless = firstCase == null || firstCase.getHeadless() == null || firstCase.getHeadless() == 1;
        int w = firstCase != null && firstCase.getWindowWidth() != null ? firstCase.getWindowWidth() : 1920;
        int h = firstCase != null && firstCase.getWindowHeight() != null ? firstCase.getWindowHeight() : 1080;

        try (UiBrowserDriver driver = driverFactory.create(driverType, browserType, headless, w, h)) {
            int[] stepCounter = {0};
            for (UiScenarioCase sc : scenarioCases) {
                UiTestCase tc = caseMapper.selectById(sc.getCaseId());
                if (tc == null) continue;
                boolean pass = doExecuteCase(driver, exec, tc, scenarioId, false, stepCounter);
                if (!pass && "STOP".equals(scenario.getFailStrategy())) {
                    break;
                }
            }
            finalizeExecution(exec);
        } catch (Exception e) {
            log.error("UI scenario execution failed: {}", e.getMessage(), e);
            exec.setStatus("ERROR");
            exec.setFinishedAt(LocalDateTime.now());
            updateById(exec);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Async("uiExecutorPool")
    @Override
    public CompletableFuture<Void> executePlanAsync(String executionId) {
        UiExecution exec = getById(executionId);
        if (exec == null) return CompletableFuture.completedFuture(null);
        UiTestPlan plan = planMapper.selectById(exec.getPlanId());
        if (plan == null) return CompletableFuture.completedFuture(null);

        List<UiPlanScenario> planScenarios = planScenarioMapper.selectList(
                new LambdaQueryWrapper<UiPlanScenario>()
                        .eq(UiPlanScenario::getPlanId, plan.getId())
                        .eq(UiPlanScenario::getEnabled, 1)
                        .orderByAsc(UiPlanScenario::getSortOrder));

        int expectedTotal = 0;
        for (UiPlanScenario ps : planScenarios) {
            List<UiScenarioCase> scs = scenarioCaseMapper.selectList(
                    new LambdaQueryWrapper<UiScenarioCase>()
                            .eq(UiScenarioCase::getScenarioId, ps.getScenarioId())
                            .eq(UiScenarioCase::getEnabled, 1));
            for (UiScenarioCase sc : scs) {
                expectedTotal += countEnabledSteps(sc.getCaseId());
            }
        }
        exec.setTotalSteps(expectedTotal);
        updateById(exec);

        String driverType = plan.getDriverType() != null ? plan.getDriverType() : "PLAYWRIGHT";
        String browserType = plan.getBrowserType() != null ? plan.getBrowserType() : "CHROMIUM";
        boolean headless = plan.getHeadless() == null || plan.getHeadless() == 1;

        try (UiBrowserDriver driver = driverFactory.create(driverType, browserType, headless, 1920, 1080)) {
            int[] stepCounter = {0};
            for (UiPlanScenario ps : planScenarios) {
                UiScenario scenario = scenarioMapper.selectById(ps.getScenarioId());
                if (scenario == null) continue;

                List<UiScenarioCase> scenarioCases = scenarioCaseMapper.selectList(
                        new LambdaQueryWrapper<UiScenarioCase>()
                                .eq(UiScenarioCase::getScenarioId, ps.getScenarioId())
                                .eq(UiScenarioCase::getEnabled, 1)
                                .orderByAsc(UiScenarioCase::getSortOrder));

                boolean scenarioPass = true;
                for (UiScenarioCase sc : scenarioCases) {
                    UiTestCase tc = caseMapper.selectById(sc.getCaseId());
                    if (tc == null) continue;
                    boolean pass = doExecuteCase(driver, exec, tc, ps.getScenarioId(), false, stepCounter);
                    if (!pass) {
                        scenarioPass = false;
                        if ("STOP".equals(scenario.getFailStrategy())) break;
                    }
                }
            }
            finalizeExecution(exec);
        } catch (Exception e) {
            log.error("UI plan execution failed: {}", e.getMessage(), e);
            exec.setStatus("ERROR");
            exec.setFinishedAt(LocalDateTime.now());
            updateById(exec);
        }
        return CompletableFuture.completedFuture(null);
    }

    private void finalizeExecution(UiExecution exec) {
        List<UiExecutionDetail> allDetails = detailMapper.selectList(
                new LambdaQueryWrapper<UiExecutionDetail>().eq(UiExecutionDetail::getExecutionId, exec.getId()));
        int total = 0, passed = 0, failed = 0, errored = 0;
        long duration = 0;
        for (UiExecutionDetail d : allDetails) {
            total++;
            duration += d.getDurationMs() != null ? d.getDurationMs() : 0;
            switch (d.getStatus()) {
                case "PASS": passed++; break;
                case "FAIL": failed++; break;
                case "ERROR": errored++; break;
            }
        }
        exec.setTotalSteps(total);
        exec.setPassedSteps(passed);
        exec.setFailedSteps(failed);
        exec.setErrorSteps(errored);
        exec.setSkippedSteps(0);
        exec.setDurationMs(duration);
        exec.setStatus(failed == 0 && errored == 0 ? "PASS" : "FAIL");
        exec.setFinishedAt(LocalDateTime.now());
        updateById(exec);
    }

    private int countEnabledSteps(String caseId) {
        Long cnt = stepMapper.selectCount(
                new LambdaQueryWrapper<UiTestStep>()
                        .eq(UiTestStep::getCaseId, caseId)
                        .eq(UiTestStep::getEnabled, 1));
        return cnt != null ? cnt.intValue() : 0;
    }

    @Override
    public Page<UiExecution> listByProject(String projectId, int page, int size) {
        Page<UiExecution> result = page(new Page<>(page, size),
                new LambdaQueryWrapper<UiExecution>()
                        .eq(UiExecution::getProjectId, projectId)
                        .orderByDesc(UiExecution::getStartedAt));
        fillSourceNames(result.getRecords());
        return result;
    }

    private void fillSourceNames(List<UiExecution> list) {
        for (UiExecution exec : list) {
            if (exec.getPlanId() != null && exec.getPlanName() == null) {
                UiTestPlan plan = planMapper.selectById(exec.getPlanId());
                if (plan != null) exec.setPlanName(plan.getName());
            }
            if (exec.getScenarioId() != null && exec.getScenarioName() == null) {
                UiScenario sc = scenarioMapper.selectById(exec.getScenarioId());
                if (sc != null) exec.setScenarioName(sc.getName());
            }
            if (exec.getCaseId() != null && exec.getCaseName() == null) {
                UiTestCase tc = caseMapper.selectById(exec.getCaseId());
                if (tc != null) exec.setCaseName(tc.getName());
            }
        }
    }

    @Override
    public Map<String, Object> getReport(String executionId) {
        UiExecution exec = getById(executionId);
        if (exec == null) return Map.of();
        fillSourceNames(List.of(exec));

        List<UiExecutionDetail> details = detailMapper.selectList(
                new LambdaQueryWrapper<UiExecutionDetail>()
                        .eq(UiExecutionDetail::getExecutionId, executionId)
                        .orderByAsc(UiExecutionDetail::getStepOrder));

        Set<String> scenarioIds = details.stream()
                .map(UiExecutionDetail::getScenarioId)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
        Map<String, String> scenarioNameMap = scenarioIds.isEmpty() ? Map.of() :
                scenarioMapper.selectBatchIds(scenarioIds).stream()
                        .collect(Collectors.toMap(UiScenario::getId, UiScenario::getName));

        Set<String> caseIds = details.stream()
                .map(UiExecutionDetail::getCaseId)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
        Map<String, String> caseNameMap = caseIds.isEmpty() ? Map.of() :
                caseMapper.selectBatchIds(caseIds).stream()
                        .collect(Collectors.toMap(UiTestCase::getId, UiTestCase::getName));

        if ("RUNNING".equals(exec.getStatus())) {
            int passed = 0, failed = 0, errored = 0;
            long duration = 0;
            for (UiExecutionDetail d : details) {
                duration += d.getDurationMs() != null ? d.getDurationMs() : 0;
                switch (d.getStatus()) {
                    case "PASS": passed++; break;
                    case "FAIL": failed++; break;
                    case "ERROR": errored++; break;
                }
            }
            exec.setPassedSteps(passed);
            exec.setFailedSteps(failed);
            exec.setErrorSteps(errored);
            exec.setSkippedSteps(0);
            exec.setDurationMs(duration);
        }

        List<Map<String, Object>> stepList = new ArrayList<>();
        for (UiExecutionDetail d : details) {
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("id", d.getId());
            step.put("scenarioId", d.getScenarioId());
            step.put("scenarioName", d.getScenarioId() == null ? null : scenarioNameMap.get(d.getScenarioId()));
            step.put("caseId", d.getCaseId());
            step.put("caseName", d.getCaseId() == null ? null : caseNameMap.get(d.getCaseId()));
            step.put("stepType", d.getStepType());
            step.put("elementName", d.getElementName());
            step.put("actionDesc", d.getActionDesc());
            step.put("status", d.getStatus());
            step.put("durationMs", d.getDurationMs());
            step.put("screenshotPath", d.getScreenshotPath());
            step.put("errorMessage", d.getErrorMessage());
            step.put("pageUrl", d.getPageUrl());
            stepList.add(step);
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("execution", exec);
        report.put("steps", stepList);
        return report;
    }

    private String saveScreenshot(byte[] data, String executionId, int stepOrder) {
        if (data == null || data.length == 0) return null;
        try {
            Path dir = Paths.get(screenshotDir, executionId);
            Files.createDirectories(dir);
            String filename = stepOrder + ".png";
            Path file = dir.resolve(filename);
            Files.write(file, data);
            return executionId + "/" + filename;
        } catch (IOException e) {
            log.warn("Failed to save screenshot: {}", e.getMessage());
            return null;
        }
    }
}
