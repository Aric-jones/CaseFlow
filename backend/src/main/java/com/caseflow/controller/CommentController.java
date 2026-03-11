package com.caseflow.controller;

import com.caseflow.common.Result;
import com.caseflow.dto.CommentDTO;
import com.caseflow.entity.Comment;
import com.caseflow.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @GetMapping("/node")
    public Result<List<CommentDTO>> nodeComments(@RequestParam Long nodeId) {
        return Result.ok(commentService.getNodeComments(nodeId));
    }

    @GetMapping("/all")
    public Result<List<CommentDTO>> allComments(@RequestParam Long caseSetId) {
        return Result.ok(commentService.getAllComments(caseSetId));
    }

    @PostMapping
    public Result<Comment> add(@RequestBody Map<String, Object> body) {
        Long nodeId = Long.valueOf(body.get("nodeId").toString());
        Long caseSetId = Long.valueOf(body.get("caseSetId").toString());
        Long parentId = body.get("parentId") != null ? Long.valueOf(body.get("parentId").toString()) : null;
        String content = body.get("content").toString();
        return Result.ok(commentService.addComment(nodeId, caseSetId, parentId, content));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        commentService.updateComment(id, body.get("content"));
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        commentService.deleteComment(id);
        return Result.ok();
    }

    @PutMapping("/{id}/resolve")
    public Result<Void> resolve(@PathVariable Long id) {
        commentService.resolveComment(id);
        return Result.ok();
    }
}
