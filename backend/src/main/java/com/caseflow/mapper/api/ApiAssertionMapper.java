package com.caseflow.mapper.api;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.caseflow.entity.api.ApiAssertion;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface ApiAssertionMapper extends BaseMapper<ApiAssertion> {

    @Delete("DELETE FROM api_assertions WHERE case_id IN (SELECT id FROM api_cases WHERE api_id = #{apiId})")
    void physicalDeleteByApiId(@Param("apiId") String apiId);
}
