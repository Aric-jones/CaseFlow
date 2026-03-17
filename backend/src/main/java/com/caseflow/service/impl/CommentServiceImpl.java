package com.caseflow.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.caseflow.dto.CommentDTO;
import com.caseflow.entity.Comment;
import com.caseflow.entity.MindNode;
import com.caseflow.entity.User;
import com.caseflow.mapper.CommentMapper;
import com.caseflow.mapper.MindNodeMapper;
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
    private final MindNodeMapper mindNodeMapper;

    @Override
    public List<CommentDTO> getNodeComments(String nodeId) {
        List<Comment> all = lambdaQuery().eq(Comment::getNodeId, nodeId).orderByAsc(Comment::getCreatedAt).list();
        return buildTree(all);
    }

    @Override
    public List<CommentDTO> getAllComments(String caseSetId, int page, int size) {
        List<Comment> all = lambdaQuery().eq(Comment::getCaseSetId, caseSetId).orderByAsc(Comment::getCreatedAt).list();
        List<CommentDTO> tree = buildTree(all);
        int start = (page - 1) * size;
        if (start >= tree.size()) return List.of();
        return tree.subList(start, Math.min(start + size, tree.size()));
    }

    @Override
    public int countUnresolvedRootComments(String nodeId) {
        return Math.toIntExact(lambdaQuery()
                .eq(Comment::getNodeId, nodeId)
                .isNull(Comment::getParentId)
                .eq(Comment::getResolved, 0)
                .count());
    }

    @Override
    public void deleteWithDescendants(String id) {
        // 收集所有要删除的 ID（当前评论 + 所有子孙）
        List<String> toDelete = new ArrayList<>();
        collectDescendants(id, toDelete);
        if (!toDelete.isEmpty()) {
            removeBatchByIds(toDelete);
        }
    }

    private void collectDescendants(String parentId, List<String> ids) {
        ids.add(parentId);
        List<Comment> children = lambdaQuery().eq(Comment::getParentId, parentId).list();
        for (Comment child : children) {
            collectDescendants(child.getId(), ids);
        }
    }

    private List<CommentDTO> buildTree(List<Comment> comments) {
        Map<String, User> userCache = new HashMap<>();
        Map<String, String> nodeTextCache = new HashMap<>();
        Map<String, List<Comment>> childMap = new HashMap<>();
        List<Comment> roots = new ArrayList<>();

        Set<String> nodeIds = comments.stream().map(Comment::getNodeId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (!nodeIds.isEmpty()) {
            List<MindNode> nodes = mindNodeMapper.selectBatchIds(nodeIds);
            for (MindNode n : nodes) nodeTextCache.put(n.getId(), n.getText());
        }

        for (Comment c : comments) {
            if (c.getParentId() == null || c.getParentId().isEmpty()) {
                roots.add(c);
            } else {
                childMap.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c);
            }
        }

        return roots.stream().map(root -> {
            CommentDTO dto = toDTO(root, userCache);
            dto.setNodeText(nodeTextCache.getOrDefault(root.getNodeId(), ""));
            List<Comment> replies = childMap.getOrDefault(root.getId(), List.of());
            dto.setReplies(replies.stream().map(r -> {
                CommentDTO rd = toDTO(r, userCache);
                rd.setNodeText(nodeTextCache.getOrDefault(r.getNodeId(), ""));
                return rd;
            }).collect(Collectors.toList()));
            dto.setReplyCount(replies.size());
            return dto;
        }).collect(Collectors.toList());
    }

    private CommentDTO toDTO(Comment c, Map<String, User> userCache) {
        CommentDTO dto = new CommentDTO();
        dto.setId(c.getId());
        dto.setNodeId(c.getNodeId());
        dto.setCaseSetId(c.getCaseSetId());
        dto.setParentId(c.getParentId());
        dto.setUserId(c.getUserId());
        dto.setContent(c.getContent());
        dto.setResolved(c.getResolved());
        dto.setCreatedAt(c.getCreatedAt());

        if (c.getDisplayName() != null && !c.getDisplayName().isEmpty()) {
            dto.setDisplayName(c.getDisplayName());
        } else {
            User u = userCache.computeIfAbsent(c.getUserId(), id -> userMapper.selectById(id));
            dto.setDisplayName(u != null ? u.getDisplayName() : "未知");
            dto.setUsername(u != null ? u.getUsername() : "");
        }

        User u = userCache.computeIfAbsent(c.getUserId(), id -> userMapper.selectById(id));
        dto.setUsername(u != null ? u.getUsername() : "");
        if (dto.getDisplayName() == null || dto.getDisplayName().isEmpty()) {
            dto.setDisplayName(u != null ? u.getDisplayName() : "未知");
        }

        dto.setReplies(List.of());
        return dto;
    }
}
