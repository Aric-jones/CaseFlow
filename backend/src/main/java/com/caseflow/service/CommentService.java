package com.caseflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.caseflow.dto.CommentDTO;
import com.caseflow.entity.Comment;
import java.util.List;

public interface CommentService extends IService<Comment> {
    List<CommentDTO> getNodeComments(String nodeId);
    List<CommentDTO> getAllComments(String caseSetId, int page, int size);
    int countUnresolvedRootComments(String nodeId);
}
