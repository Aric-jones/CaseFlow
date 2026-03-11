package com.caseflow.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CommentDTO {
    private Long id;
    private Long nodeId;
    private Long caseSetId;
    private Long parentId;
    private Long userId;
    private String username;
    private String displayName;
    private String content;
    private Integer resolved;
    private LocalDateTime createdAt;
    private List<CommentDTO> replies;
}
