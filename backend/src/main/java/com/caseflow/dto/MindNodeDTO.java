package com.caseflow.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class MindNodeDTO {
    private String id;
    private String caseSetId;
    private String parentId;
    private String text;
    private String nodeType;
    private Integer sortOrder;
    private Integer isRoot;
    private Map<String, Object> properties;
    private List<MindNodeDTO> children;
    private Integer commentCount;
}
