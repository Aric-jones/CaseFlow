package com.caseflow.service.ui;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.caseflow.entity.ui.UiExecution;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface UiExecutionService extends IService<UiExecution> {
    CompletableFuture<Void> executeCaseAsync(String executionId, String caseId);
    CompletableFuture<Void> executeScenarioAsync(String executionId, String scenarioId);
    CompletableFuture<Void> executePlanAsync(String executionId);
    Page<UiExecution> listByProject(String projectId, int page, int size);
    Map<String, Object> getReport(String executionId);
}
