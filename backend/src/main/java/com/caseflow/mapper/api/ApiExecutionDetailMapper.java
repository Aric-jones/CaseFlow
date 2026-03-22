package com.caseflow.mapper.api;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.caseflow.entity.api.ApiExecutionDetail;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface ApiExecutionDetailMapper extends BaseMapper<ApiExecutionDetail> {

    @Delete("DELETE FROM api_execution_details WHERE execution_id = #{executionId}")
    void physicalDeleteByExecutionId(@Param("executionId") String executionId);
}
