package com.caseflow.service.api;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.caseflow.entity.api.ApiExecution;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface ApiExecutionService extends IService<ApiExecution> {
    /** 单用例调试（同步），返回执行详情 */
    Map<String, Object> debugCase(String caseId, String environmentId);

    /** 场景执行（异步） */
    CompletableFuture<Void> executeScenarioAsync(String executionId, String scenarioId, String environmentId, Map<String, String> vars);

    /** 计划执行（异步） */
    CompletableFuture<Void> executePlanAsync(String executionId);

    /** 执行记录分页 */
    Page<ApiExecution> listByProject(String projectId, int page, int size);

    /** 执行详情（含所有步骤） */
    Map<String, Object> getReport(String executionId);
}
