package com.caseflow.mapper.api;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.caseflow.entity.api.ApiCase;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface ApiCaseMapper extends BaseMapper<ApiCase> {

    @Delete("DELETE FROM api_cases WHERE api_id = #{apiId}")
    void physicalDeleteByApiId(@Param("apiId") String apiId);
}
