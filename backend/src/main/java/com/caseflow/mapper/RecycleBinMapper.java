package com.caseflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.caseflow.entity.RecycleBin;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

public interface RecycleBinMapper extends BaseMapper<RecycleBin> {

    /** 按项目和类型查询回收站记录，按删除时间倒序 */
    @Select("SELECT * FROM recycle_bin WHERE project_id = #{projectId} AND item_type = #{itemType} ORDER BY deleted_at DESC")
    List<RecycleBin> selectByProjectAndType(@Param("projectId") String projectId, @Param("itemType") String itemType);

    /**
     * 旧版用例集回收站查询（兼容 project_id 为 null 的历史数据）：
     * 通过 case_sets 表的 project_id 反查
     */
    @Select("""
        SELECT rb.*, cs.name AS item_name
        FROM recycle_bin rb
        JOIN case_sets cs ON cs.id = rb.item_id
        WHERE rb.item_type = 'CASE_SET'
          AND (rb.project_id = #{projectId} OR cs.project_id = #{projectId})
        ORDER BY rb.deleted_at DESC
        """)
    List<RecycleBin> selectCaseSetsByProject(@Param("projectId") String projectId);
}
