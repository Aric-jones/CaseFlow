package com.caseflow.service.api.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.caseflow.common.BusinessException;
import com.caseflow.engine.*;
import com.caseflow.entity.api.*;
import com.caseflow.mapper.api.*;
import com.caseflow.service.api.ApiExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiExecutionServiceImpl extends ServiceImpl<ApiExecutionMapper, ApiExecution>
        implements ApiExecutionService {

    private final HttpExecutor httpExecutor;
    private final VariableResolver variableResolver;
    private final AuthInjector authInjector;
    private final AssertionEngine assertionEngine;
    private final ScriptEngine scriptEngine;

    private final ApiEnvironmentMapper envMapper;
    private final ApiDefinitionMapper defMapper;
    private final ApiCaseMapper caseMapper;
    private final ApiAssertionMapper assertionMapper;
    private final ApiScenarioMapper scenarioMapper;
    private final ApiScenarioStepMapper stepMapper;
    private final ApiExecutionDetailMapper detailMapper;
    private final ApiTestPlanMapper planMapper;
    private final ApiPlanScenarioMapper planScenarioMapper;

    // ═══════════════════════════════════════════════════════
    //  单用例调试（同步）
    // ═══════════════════════════════════════════════════════

    @Override
    public Map<String, Object> debugCase(String caseId, String environmentId) {
        ApiCase ac = caseMapper.selectById(caseId);
        if (ac == null) throw new BusinessException("用例不存在");
        ApiDefinition def = defMapper.selectById(ac.getApiId());
        if (def == null) throw new BusinessException("接口定义不存在");
        ApiEnvironment env = envMapper.selectById(environmentId);
        if (env == null) throw new BusinessException("环境不存在");

        List<ApiAssertion> assertions = assertionMapper.selectList(
                new LambdaQueryWrapper<ApiAssertion>().eq(ApiAssertion::getCaseId, caseId)
                        .orderByAsc(ApiAssertion::getSortOrder));

        Map<String, String> vars = new LinkedHashMap<>();
        if (env.getVariables() != null) vars.putAll(env.getVariables());

        return executeSingleCase(def, ac, assertions, env, vars);
    }

    /**
     * 执行单个用例，返回完整的请求/响应/断言结果
     */
    private Map<String, Object> executeSingleCase(ApiDefinition def, ApiCase ac, List<ApiAssertion> assertions,
                                                    ApiEnvironment env, Map<String, String> vars) {
        // 1. 构建请求 URL
        String path = variableResolver.resolve(def.getPath(), vars);
        String url = env.getBaseUrl().replaceAll("/+$", "") + (path.startsWith("/") ? path : "/" + path);

        // 2. 合并 Query 参数
        StringBuilder queryStr = new StringBuilder();
        appendParams(queryStr, def.getDefaultParams(), vars);
        appendParams(queryStr, ac.getQueryParams(), vars);
        if (!queryStr.isEmpty()) url += (url.contains("?") ? "&" : "?") + queryStr;

        // 3. 合并 Headers：环境全局 → 接口默认 → 用例覆盖
        Map<String, String> headers = new LinkedHashMap<>();
        if (env.getHeaders() != null) headers.putAll(env.getHeaders());
        mergeKvList(headers, def.getDefaultHeaders(), vars);
        mergeKvList(headers, ac.getHeaders(), vars);

        // 4. 注入鉴权
        authInjector.inject(headers, def.getAuthType(), def.getAuthConfig(), variableResolver, vars);

        // 5. 执行前置脚本
        scriptEngine.executePreScript(ac.getPreScript(), headers, vars, variableResolver);

        // 6. 构建 Body
        String bodyType = ac.getBodyType() != null ? ac.getBodyType() : def.getDefaultBodyType();
        String bodyContent = ac.getBodyContent() != null ? ac.getBodyContent() : def.getDefaultBody();
        bodyContent = variableResolver.resolve(bodyContent, vars);

        // 7. 发送请求
        HttpExecutor.HttpResult result = httpExecutor.execute(
                new HttpExecutor.ExecuteRequest(def.getMethod(), url, headers, bodyType, bodyContent));

        // 8. 执行后置脚本
        scriptEngine.executePostScript(ac.getPostScript(), result, vars);

        // 9. 执行断言
        List<Map<String, Object>> assertionResults = new ArrayList<>();
        boolean allPass = result.isSuccess();
        for (ApiAssertion a : assertions) {
            AssertionEngine.AssertionResult ar = assertionEngine.evaluate(
                    a.getType(), a.getExpression(), a.getOperator(), a.getExpectedValue(), result);
            Map<String, Object> arMap = new LinkedHashMap<>();
            arMap.put("type", ar.type()); arMap.put("expression", ar.expression());
            arMap.put("operator", ar.operator()); arMap.put("expected", ar.expectedValue());
            arMap.put("actual", ar.actualValue()); arMap.put("pass", ar.pass());
            arMap.put("error", ar.error());
            assertionResults.add(arMap);
            if (!ar.pass()) allPass = false;
        }

        // 10. 组装结果
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("requestUrl", url);
        res.put("requestMethod", def.getMethod());
        res.put("requestHeaders", headers);
        res.put("requestBody", bodyContent);
        res.put("responseStatus", result.statusCode());
        res.put("responseHeaders", result.headers());
        res.put("responseBody", result.body());
        res.put("durationMs", result.durationMs());
        res.put("error", result.error());
        res.put("assertions", assertionResults);
        res.put("allPass", allPass);
        res.put("extractedVars", vars);
        res.put("status", !result.isSuccess() ? "ERROR" : (allPass ? "PASS" : "FAIL"));
        return res;
    }

    // ═══════════════════════════════════════════════════════
    //  场景异步执行
    // ═══════════════════════════════════════════════════════

    @Async("apiExecutorPool")
    @Override
    public CompletableFuture<Void> executeScenarioAsync(String executionId, String scenarioId,
                                                         String environmentId, Map<String, String> vars) {
        doExecuteScenario(executionId, scenarioId, environmentId, vars);
        return CompletableFuture.completedFuture(null);
    }

    /** 场景执行核心逻辑（可同步调用） */
    private void doExecuteScenario(String executionId, String scenarioId, String environmentId, Map<String, String> vars) {
        ApiExecution exec = getById(executionId);
        ApiEnvironment env = envMapper.selectById(environmentId);
        if (exec == null || env == null) return;

        ApiScenario scenario = scenarioMapper.selectById(scenarioId);
        List<ApiScenarioStep> steps = stepMapper.selectList(
                new LambdaQueryWrapper<ApiScenarioStep>().eq(ApiScenarioStep::getScenarioId, scenarioId)
                        .eq(ApiScenarioStep::getEnabled, 1).orderByAsc(ApiScenarioStep::getSortOrder));

        if (vars == null) vars = new LinkedHashMap<>();
        if (env.getVariables() != null) vars.putAll(env.getVariables());

        int total = steps.size(), passed = 0, failed = 0, errored = 0;
        long totalDuration = 0;
        boolean shouldStop = false;

        for (int i = 0; i < steps.size(); i++) {
            if (shouldStop) { break; }
            ApiScenarioStep step = steps.get(i);

            if (step.getDelayMs() != null && step.getDelayMs() > 0) {
                try { Thread.sleep(step.getDelayMs()); } catch (InterruptedException ignored) {}
            }

            ApiCase ac = caseMapper.selectById(step.getCaseId());
            if (ac == null) { errored++; continue; }
            ApiDefinition def = defMapper.selectById(ac.getApiId());
            if (def == null) { errored++; continue; }

            List<ApiAssertion> assertions = assertionMapper.selectList(
                    new LambdaQueryWrapper<ApiAssertion>().eq(ApiAssertion::getCaseId, ac.getId())
                            .orderByAsc(ApiAssertion::getSortOrder));

            // 步骤级脚本追加
            scriptEngine.executePreScript(step.getPreScript(), new LinkedHashMap<>(), vars, variableResolver);

            Map<String, Object> result = executeSingleCase(def, ac, assertions, env, vars);

            // 步骤级后置脚本
            scriptEngine.executePostScript(step.getPostScript(),
                    new HttpExecutor.HttpResult((int) result.get("responseStatus"),
                            Map.of(), (String) result.get("responseBody"),
                            (long) result.get("durationMs"), (String) result.get("error")), vars);

            String status = (String) result.get("status");
            totalDuration += (long) result.get("durationMs");

            // 保存执行详情
            ApiExecutionDetail detail = new ApiExecutionDetail();
            detail.setExecutionId(executionId);
            detail.setScenarioId(scenarioId);
            detail.setCaseId(ac.getId());
            detail.setApiId(def.getId());
            detail.setStepOrder(i);
            detail.setRequestUrl((String) result.get("requestUrl"));
            detail.setRequestMethod(def.getMethod());
            detail.setRequestBody((String) result.get("requestBody"));
            detail.setResponseStatus((Integer) result.get("responseStatus"));
            detail.setResponseBody(truncate((String) result.get("responseBody"), 500000));
            detail.setDurationMs((Long) result.get("durationMs"));
            detail.setStatus(status);
            detail.setErrorMessage((String) result.get("error"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> ar = (List<Map<String, Object>>) result.get("assertions");
            detail.setAssertionResults(ar);
            detailMapper.insert(detail);

            switch (status) {
                case "PASS" -> passed++;
                case "FAIL" -> { failed++; if ("STOP".equals(scenario != null ? scenario.getFailStrategy() : "STOP")) shouldStop = true; }
                default -> { errored++; if ("STOP".equals(scenario != null ? scenario.getFailStrategy() : "STOP")) shouldStop = true; }
            }
        }

        exec.setTotalCases(total);
        exec.setPassedCases(passed);
        exec.setFailedCases(failed);
        exec.setErrorCases(errored);
        exec.setSkippedCases(shouldStop ? total - passed - failed - errored : 0);
        exec.setDurationMs(totalDuration);
        exec.setStatus(failed == 0 && errored == 0 ? "PASS" : "FAIL");
        exec.setFinishedAt(LocalDateTime.now());
        updateById(exec);
    }

    // ═══════════════════════════════════════════════════════
    //  计划异步执行
    // ═══════════════════════════════════════════════════════

    @Async("apiExecutorPool")
    @Override
    public CompletableFuture<Void> executePlanAsync(String executionId) {
        ApiExecution exec = getById(executionId);
        if (exec == null) return CompletableFuture.completedFuture(null);

        ApiTestPlan plan = planMapper.selectById(exec.getPlanId());
        if (plan == null) return CompletableFuture.completedFuture(null);

        List<ApiPlanScenario> planScenarios = planScenarioMapper.selectList(
                new LambdaQueryWrapper<ApiPlanScenario>().eq(ApiPlanScenario::getPlanId, plan.getId())
                        .eq(ApiPlanScenario::getEnabled, 1).orderByAsc(ApiPlanScenario::getSortOrder));

        Map<String, String> vars = new LinkedHashMap<>();
        int totalCases = 0, passed = 0, failed = 0, errored = 0, skipped = 0;
        long totalDuration = 0;

        for (ApiPlanScenario ps : planScenarios) {
            doExecuteScenario(executionId, ps.getScenarioId(), plan.getEnvironmentId(), vars);
        }

        // 汇总统计
        List<ApiExecutionDetail> allDetails = detailMapper.selectList(
                new LambdaQueryWrapper<ApiExecutionDetail>().eq(ApiExecutionDetail::getExecutionId, executionId));
        for (ApiExecutionDetail d : allDetails) {
            totalCases++;
            totalDuration += d.getDurationMs() != null ? d.getDurationMs() : 0;
            switch (d.getStatus()) {
                case "PASS" -> passed++;
                case "FAIL" -> failed++;
                case "ERROR" -> errored++;
                default -> skipped++;
            }
        }

        exec.setTotalCases(totalCases);
        exec.setPassedCases(passed);
        exec.setFailedCases(failed);
        exec.setErrorCases(errored);
        exec.setSkippedCases(skipped);
        exec.setDurationMs(totalDuration);
        exec.setStatus(failed == 0 && errored == 0 ? "PASS" : "FAIL");
        exec.setFinishedAt(LocalDateTime.now());
        updateById(exec);

        return CompletableFuture.completedFuture(null);
    }

    // ═══════════════════════════════════════════════════════
    //  查询
    // ═══════════════════════════════════════════════════════

    @Override
    public Page<ApiExecution> listByProject(String projectId, int page, int size) {
        return page(new Page<>(page, size),
                new LambdaQueryWrapper<ApiExecution>().eq(ApiExecution::getProjectId, projectId)
                        .orderByDesc(ApiExecution::getStartedAt));
    }

    @Override
    public Map<String, Object> getReport(String executionId) {
        ApiExecution exec = getById(executionId);
        if (exec == null) return Map.of();
        List<ApiExecutionDetail> details = detailMapper.selectList(
                new LambdaQueryWrapper<ApiExecutionDetail>().eq(ApiExecutionDetail::getExecutionId, executionId)
                        .orderByAsc(ApiExecutionDetail::getStepOrder));

        Map<String, String> apiNames = new HashMap<>();
        Map<String, String> caseNames = new HashMap<>();
        Set<String> apiIds = details.stream().map(ApiExecutionDetail::getApiId).collect(Collectors.toSet());
        Set<String> caseIds = details.stream().map(ApiExecutionDetail::getCaseId).collect(Collectors.toSet());
        if (!apiIds.isEmpty()) defMapper.selectBatchIds(apiIds).forEach(d -> apiNames.put(d.getId(), d.getName()));
        if (!caseIds.isEmpty()) caseMapper.selectBatchIds(caseIds).forEach(c -> caseNames.put(c.getId(), c.getName()));

        List<Map<String, Object>> stepList = new ArrayList<>();
        for (ApiExecutionDetail d : details) {
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("id", d.getId());
            step.put("apiName", apiNames.getOrDefault(d.getApiId(), ""));
            step.put("caseName", caseNames.getOrDefault(d.getCaseId(), ""));
            step.put("method", d.getRequestMethod());
            step.put("url", d.getRequestUrl());
            step.put("status", d.getStatus());
            step.put("durationMs", d.getDurationMs());
            step.put("responseStatus", d.getResponseStatus());
            step.put("responseBody", d.getResponseBody());
            step.put("assertions", d.getAssertionResults());
            step.put("error", d.getErrorMessage());
            stepList.add(step);
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("execution", exec);
        report.put("steps", stepList);
        return report;
    }

    // ═══════════════════════════════════════════════════════
    //  工具方法
    // ═══════════════════════════════════════════════════════

    private void appendParams(StringBuilder sb, List<Map<String, String>> params, Map<String, String> vars) {
        if (params == null) return;
        for (Map<String, String> p : params) {
            String k = p.get("key"); String v = p.get("value");
            if (k == null || k.isBlank()) continue;
            if (!sb.isEmpty()) sb.append("&");
            sb.append(variableResolver.resolve(k, vars)).append("=").append(variableResolver.resolve(v != null ? v : "", vars));
        }
    }

    private void mergeKvList(Map<String, String> target, List<Map<String, String>> kvList, Map<String, String> vars) {
        if (kvList == null) return;
        for (Map<String, String> kv : kvList) {
            String k = kv.get("key"); String v = kv.get("value");
            if (k != null && !k.isBlank()) {
                target.put(variableResolver.resolve(k, vars), variableResolver.resolve(v != null ? v : "", vars));
            }
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() > maxLen ? s.substring(0, maxLen) + "...[truncated]" : s;
    }
}
