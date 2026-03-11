package com.caseflow.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.caseflow.entity.Comment;
import com.caseflow.entity.User;
import com.caseflow.mapper.CommentMapper;
import com.caseflow.mapper.UserMapper;
import com.caseflow.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements CommentService {
    private final UserMapper userMapper;

    @Override
    public List<Map<String, Object>> getNodeComments(String nodeId) {
        List<Comment> comments = lambdaQuery().eq(Comment::getNodeId, nodeId).orderByAsc(Comment::getCreatedAt).list();
        return enrichComments(comments);
    }
    @Override
    public List<Map<String, Object>> getAllComments(String caseSetId) {
        List<Comment> comments = lambdaQuery().eq(Comment::getCaseSetId, caseSetId).orderByDesc(Comment::getCreatedAt).list();
        return enrichComments(comments);
    }
    private List<Map<String, Object>> enrichComments(List<Comment> comments) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Comment c : comments) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getId()); m.put("nodeId", c.getNodeId()); m.put("caseSetId", c.getCaseSetId());
            m.put("parentId", c.getParentId()); m.put("userId", c.getUserId()); m.put("content", c.getContent());
            m.put("resolved", c.getResolved()); m.put("createdAt", c.getCreatedAt());
            User u = userMapper.selectById(c.getUserId());
            m.put("displayName", u != null ? u.getDisplayName() : "未知");
            m.put("username", u != null ? u.getUsername() : "");
            result.add(m);
        }
        return result;
    }
}
