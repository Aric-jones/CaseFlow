package com.caseflow.mapper.api;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.caseflow.entity.api.ApiPlanScenario;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface ApiPlanScenarioMapper extends BaseMapper<ApiPlanScenario> {

    @Delete("DELETE FROM api_plan_scenarios WHERE plan_id = #{planId}")
    void physicalDeleteByPlanId(@Param("planId") String planId);
}
