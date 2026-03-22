package com.caseflow.mapper.api;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.caseflow.entity.api.ApiExecution;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface ApiExecutionMapper extends BaseMapper<ApiExecution> {

    @Update("UPDATE api_executions SET deleted = 0 WHERE id = #{id}")
    void restore(@Param("id") String id);

    @Delete("DELETE FROM api_executions WHERE id = #{id}")
    void physicalDelete(@Param("id") String id);
}
