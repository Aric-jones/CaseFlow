package com.caseflow.controller;

import com.caseflow.common.CurrentUserUtil;
import com.caseflow.common.Result;
import com.caseflow.entity.Comment;
import com.caseflow.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequestMapping("/api/comments") @RequiredArgsConstructor
public class CommentController {
    private final CommentService commentService;

    @GetMapping("/node") public Result<?> nodeComments(@RequestParam String nodeId) { return Result.ok(commentService.getNodeComments(nodeId)); }
    @GetMapping("/all") public Result<?> allComments(@RequestParam String caseSetId) { return Result.ok(commentService.getAllComments(caseSetId)); }
    @PostMapping public Result<?> add(@RequestBody Map<String, String> body) {
        Comment c = new Comment(); c.setNodeId(body.get("nodeId")); c.setCaseSetId(body.get("caseSetId"));
        c.setContent(body.get("content")); c.setParentId(body.get("parentId"));
        c.setUserId(CurrentUserUtil.getCurrentUserId()); c.setResolved(0);
        commentService.save(c); return Result.ok(c);
    }
    @PutMapping("/{id}") public Result<?> update(@PathVariable String id, @RequestBody Map<String, String> body) {
        Comment c = commentService.getById(id); if (c != null) { c.setContent(body.get("content")); commentService.updateById(c); } return Result.ok();
    }
    @DeleteMapping("/{id}") public Result<?> delete(@PathVariable String id) { commentService.removeById(id); return Result.ok(); }
    @PutMapping("/{id}/resolve") public Result<?> resolve(@PathVariable String id) {
        Comment c = commentService.getById(id); if (c != null) { c.setResolved(1); commentService.updateById(c); } return Result.ok();
    }
}
