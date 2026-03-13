package com.caseflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.caseflow.entity.TestPlan;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface TestPlanMapper extends BaseMapper<TestPlan> {
    /** 物理删除（跳过全局逻辑删除拦截） */
    @Delete("DELETE FROM test_plans WHERE id = #{id}")
    void physicalDeleteById(@Param("id") String id);

    /** 查询已逻辑删除的计划（全局拦截会过滤 deleted=1，需手写 SQL） */
    @Select("SELECT * FROM test_plans WHERE id = #{id} AND deleted = 1")
    TestPlan selectDeletedById(@Param("id") String id);

    /** 恢复：将 deleted 重置为 0 */
    @Update("UPDATE test_plans SET deleted = 0, deleted_at = NULL, deleted_by = NULL, deleted_by_name = NULL WHERE id = #{id}")
    void restoreById(@Param("id") String id);

    /** 分页查询已逻辑删除的计划（绕过全局 deleted=0 拦截） */
    @Select("SELECT * FROM test_plans WHERE project_id = #{projectId} AND deleted = 1 ORDER BY deleted_at DESC")
    Page<TestPlan> selectDeletedPage(Page<TestPlan> page, @Param("projectId") String projectId);
}
