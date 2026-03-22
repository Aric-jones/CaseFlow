package com.caseflow.mapper.api;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.caseflow.entity.api.ApiScenarioStep;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface ApiScenarioStepMapper extends BaseMapper<ApiScenarioStep> {

    @Delete("DELETE FROM api_scenario_steps WHERE scenario_id = #{scenarioId}")
    void physicalDeleteByScenarioId(@Param("scenarioId") String scenarioId);
}
