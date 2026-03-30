package com.caseflow.mapper.ui;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.caseflow.entity.ui.UiScenario;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface UiScenarioMapper extends BaseMapper<UiScenario> {

    @Update("UPDATE ui_scenarios SET deleted = 0, directory_id = #{dirId} WHERE id = #{id}")
    void restore(@Param("id") String id, @Param("dirId") String dirId);

    @Delete("DELETE FROM ui_scenarios WHERE id = #{id}")
    void physicalDelete(@Param("id") String id);
}
