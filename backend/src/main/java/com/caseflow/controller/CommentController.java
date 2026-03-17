package com.caseflow.controller;

import com.caseflow.common.CurrentUserUtil;
import com.caseflow.common.Result;
import com.caseflow.entity.CaseSet;
import com.caseflow.entity.Comment;
import com.caseflow.service.CaseSetService;
import com.caseflow.service.CommentService;
import com.caseflow.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
public class CommentController {
    private final CommentService commentService;
    private final NotificationService notificationService;
    private final CaseSetService caseSetService;

    @GetMapping("/node")
    public Result<?> nodeComments(@RequestParam String nodeId) {
        return Result.ok(commentService.getNodeComments(nodeId));
    }

    @GetMapping("/all")
    public Result<?> allComments(@RequestParam String caseSetId,
                                 @RequestParam(defaultValue = "1") int page,
                                 @RequestParam(defaultValue = "20") int size) {
        return Result.ok(commentService.getAllComments(caseSetId, page, size));
    }

    @GetMapping("/node/count")
    public Result<?> unresolvedCount(@RequestParam String nodeId) {
        return Result.ok(commentService.countUnresolvedRootComments(nodeId));
    }

    @PostMapping
    public Result<?> add(@RequestBody Map<String, String> body) {
        String content = body.get("content");
        String nodeId = body.get("nodeId");
        String caseSetId = body.get("caseSetId");
        if (content == null || content.isBlank()) return Result.error("评论内容不能为空");
        if (nodeId == null || nodeId.isBlank()) return Result.error("节点ID不能为空");
        Comment c = new Comment();
        c.setNodeId(nodeId);
        c.setCaseSetId(caseSetId);
        c.setContent(content.trim());
        c.setParentId(body.get("parentId"));
        c.setUserId(CurrentUserUtil.getCurrentUserId());
        c.setDisplayName(CurrentUserUtil.getCurrentUserDisplayName());
        c.setResolved(0);
        commentService.save(c);

        String currentUserId = CurrentUserUtil.getCurrentUserId();
        String currentUserName = CurrentUserUtil.getCurrentUserDisplayName();
        String link = "/review/" + (caseSetId != null ? caseSetId : "") + "?nodeId=" + nodeId + "&openComment=1";
        CaseSet cs = caseSetId != null ? caseSetService.getById(caseSetId) : null;
        String csCreator = cs != null ? cs.getCreatedBy() : null;
        String csName = cs != null ? cs.getName() : "";

        if (c.getParentId() != null) {
            // 回复：只通知被回复者
            Comment parent = commentService.getById(c.getParentId());
            if (parent != null && !parent.getUserId().equals(currentUserId)) {
                notificationService.send(parent.getUserId(), "COMMENT_REPLY",
                        "收到评论回复", currentUserName + " 回复了您的评论：" + content.substring(0, Math.min(content.length(), 50)), link);
            }
        } else {
            // 根评论：通知用例集创建人
            if (csCreator != null && !csCreator.equals(currentUserId)) {
                notificationService.send(csCreator, "COMMENT_NEW",
                        "新的评论", currentUserName + " 在用例集「" + csName + "」中添加了评论", link);
            }
        }

        return Result.ok(c);
    }

    @PutMapping("/{id}")
    public Result<?> update(@PathVariable String id, @RequestBody Map<String, String> body) {
        Comment c = commentService.getById(id);
        if (c == null) return Result.error("评论不存在");
        String content = body.get("content");
        if (content == null || content.isBlank()) return Result.error("评论内容不能为空");
        c.setContent(content.trim());
        commentService.updateById(c);
        return Result.ok(c);
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable String id) {
        commentService.removeById(id);
        return Result.ok();
    }

    @PutMapping("/{id}/resolve")
    public Result<?> resolve(@PathVariable String id) {
        Comment c = commentService.getById(id);
        if (c == null) return Result.error("评论不存在");
        c.setResolved(c.getResolved() != null && c.getResolved() == 1 ? 0 : 1);
        commentService.updateById(c);
        return Result.ok(c);
    }
}
