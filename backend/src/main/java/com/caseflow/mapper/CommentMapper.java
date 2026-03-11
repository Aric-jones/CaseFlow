package com.caseflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.caseflow.entity.Comment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CommentMapper extends BaseMapper<Comment> {
    @Select("SELECT COUNT(*) FROM comments WHERE node_id = #{nodeId} AND parent_id IS NULL AND resolved = 0")
    int countUnresolvedByNode(Long nodeId);
}
