package com.caseflow.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CommentDTO {
    private String id;
    private String nodeId;
    private String caseSetId;
    private String parentId;
    private String userId;
    private String username;
    private String displayName;
    private String content;
    private Integer resolved;
    private LocalDateTime createdAt;
    private List<CommentDTO> replies;
}
