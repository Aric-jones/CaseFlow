package com.caseflow.mapper.ui;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.caseflow.entity.ui.UiExecution;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface UiExecutionMapper extends BaseMapper<UiExecution> {

    @Update("UPDATE ui_executions SET deleted = 0 WHERE id = #{id}")
    void restore(@Param("id") String id);

    @Delete("DELETE FROM ui_executions WHERE id = #{id}")
    void physicalDelete(@Param("id") String id);
}
