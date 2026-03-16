package com.caseflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.caseflow.entity.Comment;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

public interface CommentMapper extends BaseMapper<Comment> {
    @Select("SELECT COUNT(*) FROM comments WHERE node_id = #{nodeId} AND resolved = 0 AND parent_id IS NULL")
    int countUnresolvedByNode(String nodeId);

    @Select("SELECT node_id, COUNT(*) AS cnt FROM comments " +
            "WHERE case_set_id = #{caseSetId} AND resolved = 0 AND parent_id IS NULL " +
            "GROUP BY node_id")
    @MapKey("node_id")
    Map<String, Map<String, Object>> countUnresolvedByCaseSet(String caseSetId);
}
