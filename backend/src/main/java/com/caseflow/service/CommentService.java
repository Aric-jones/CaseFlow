package com.caseflow.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.caseflow.entity.Comment;
import java.util.List;
import java.util.Map;
public interface CommentService extends IService<Comment> {
    List<Map<String, Object>> getNodeComments(String nodeId);
    List<Map<String, Object>> getAllComments(String caseSetId);
}
