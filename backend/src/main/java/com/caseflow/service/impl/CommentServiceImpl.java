package com.caseflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.caseflow.common.BusinessException;
import com.caseflow.common.CurrentUserUtil;
import com.caseflow.dto.CommentDTO;
import com.caseflow.entity.Comment;
import com.caseflow.entity.User;
import com.caseflow.mapper.CommentMapper;
import com.caseflow.mapper.UserMapper;
import com.caseflow.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements CommentService {

    private final UserMapper userMapper;

    @Override
    public List<CommentDTO> getNodeComments(Long nodeId) {
        List<Comment> all = this.lambdaQuery()
                .eq(Comment::getNodeId, nodeId)
                .orderByAsc(Comment::getCreatedAt)
                .list();
        return buildCommentTree(all);
    }

    @Override
    public List<CommentDTO> getAllComments(Long caseSetId) {
        List<Comment> all = this.lambdaQuery()
                .eq(Comment::getCaseSetId, caseSetId)
                .orderByAsc(Comment::getCreatedAt)
                .list();
        return buildCommentTree(all);
    }

    private List<CommentDTO> buildCommentTree(List<Comment> all) {
        Map<Long, User> userCache = new HashMap<>();
        List<CommentDTO> dtos = all.stream().map(c -> {
            CommentDTO dto = new CommentDTO();
            dto.setId(c.getId());
            dto.setNodeId(c.getNodeId());
            dto.setCaseSetId(c.getCaseSetId());
            dto.setParentId(c.getParentId());
            dto.setUserId(c.getUserId());
            dto.setContent(c.getContent());
            dto.setResolved(c.getResolved());
            dto.setCreatedAt(c.getCreatedAt());
            User user = userCache.computeIfAbsent(c.getUserId(), userMapper::selectById);
            if (user != null) {
                dto.setUsername(user.getUsername());
                dto.setDisplayName(user.getDisplayName());
            }
            dto.setReplies(new ArrayList<>());
            return dto;
        }).collect(Collectors.toList());

        Map<Long, CommentDTO> map = dtos.stream().collect(Collectors.toMap(CommentDTO::getId, d -> d));
        List<CommentDTO> roots = new ArrayList<>();
        for (CommentDTO dto : dtos) {
            if (dto.getParentId() == null) {
                roots.add(dto);
            } else {
                CommentDTO parent = map.get(dto.getParentId());
                if (parent != null) parent.getReplies().add(dto);
            }
        }
        return roots;
    }

    @Override
    public Comment addComment(Long nodeId, Long caseSetId, Long parentId, String content) {
        Comment comment = new Comment();
        comment.setNodeId(nodeId);
        comment.setCaseSetId(caseSetId);
        comment.setParentId(parentId);
        comment.setUserId(CurrentUserUtil.getCurrentUserId());
        comment.setContent(content);
        comment.setResolved(0);
        this.save(comment);
        return comment;
    }

    @Override
    public void updateComment(Long id, String content) {
        Comment comment = getById(id);
        if (comment == null) throw new BusinessException("评论不存在");
        if (!comment.getUserId().equals(CurrentUserUtil.getCurrentUserId())) {
            throw new BusinessException("只能编辑自己的评论");
        }
        comment.setContent(content);
        this.updateById(comment);
    }

    @Override
    public void deleteComment(Long id) {
        Comment comment = getById(id);
        if (comment == null) throw new BusinessException("评论不存在");
        if (!comment.getUserId().equals(CurrentUserUtil.getCurrentUserId())) {
            throw new BusinessException("只能删除自己的评论");
        }
        this.removeById(id);
        this.remove(new LambdaQueryWrapper<Comment>().eq(Comment::getParentId, id));
    }

    @Override
    public void resolveComment(Long id) {
        Comment comment = getById(id);
        if (comment == null) throw new BusinessException("评论不存在");
        comment.setResolved(1);
        this.updateById(comment);
    }

    @Override
    public int getUnresolvedCount(Long nodeId) {
        return baseMapper.countUnresolvedByNode(nodeId);
    }
}
