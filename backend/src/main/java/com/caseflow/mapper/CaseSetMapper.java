package com.caseflow.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.caseflow.entity.CaseSet;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;

public interface CaseSetMapper extends BaseMapper<CaseSet> {
    @Update("UPDATE case_sets SET deleted = 0, directory_id = #{dirId} WHERE id = #{id}")
    void restoreCaseSet(@Param("id") String id, @Param("dirId") String dirId);

    @Select("SELECT id FROM case_sets WHERE project_id = #{projectId} AND deleted = 1")
    List<String> selectDeletedIdsByProject(@Param("projectId") String projectId);
}
