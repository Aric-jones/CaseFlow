package com.caseflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.caseflow.dto.CommentDTO;
import com.caseflow.entity.Comment;
import java.util.List;

public interface CommentService extends IService<Comment> {
    List<CommentDTO> getNodeComments(Long nodeId);
    List<CommentDTO> getAllComments(Long caseSetId);
    Comment addComment(Long nodeId, Long caseSetId, Long parentId, String content);
    void updateComment(Long id, String content);
    void deleteComment(Long id);
    void resolveComment(Long id);
    int getUnresolvedCount(Long nodeId);
}
