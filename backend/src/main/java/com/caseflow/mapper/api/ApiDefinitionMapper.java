package com.caseflow.mapper.api;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.caseflow.entity.api.ApiDefinition;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface ApiDefinitionMapper extends BaseMapper<ApiDefinition> {

    @Update("UPDATE api_definitions SET deleted = 0, directory_id = #{dirId} WHERE id = #{id}")
    void restore(@Param("id") String id, @Param("dirId") String dirId);

    @Delete("DELETE FROM api_definitions WHERE id = #{id}")
    void physicalDelete(@Param("id") String id);
}
