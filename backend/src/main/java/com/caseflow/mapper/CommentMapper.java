package com.caseflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.caseflow.entity.Comment;
import org.apache.ibatis.annotations.Select;

public interface CommentMapper extends BaseMapper<Comment> {
    @Select("SELECT COUNT(*) FROM comments WHERE node_id = #{nodeId} AND resolved = 0 AND parent_id IS NULL")
    int countUnresolvedByNode(String nodeId);
}
