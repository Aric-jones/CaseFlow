package com.caseflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.caseflow.entity.TestPlan;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface TestPlanMapper extends BaseMapper<TestPlan> {
    /** 物理删除（跳过全局逻辑删除拦截） */
    @Delete("DELETE FROM test_plans WHERE id = #{id}")
    void physicalDeleteById(@Param("id") String id);

    /** 恢复：将 deleted 重置为 0（与 CaseSetMapper.restoreCaseSet 对齐） */
    @Update("UPDATE test_plans SET deleted = 0 WHERE id = #{id}")
    void restoreById(@Param("id") String id);
}
