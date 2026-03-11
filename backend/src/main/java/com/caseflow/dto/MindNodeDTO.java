package com.caseflow.dto;

import lombok.Data;
import java.util.List;

@Data
public class MindNodeDTO {
    private Long id;
    private Long caseSetId;
    private Long parentId;
    private String text;
    private String nodeType;
    private Integer sortOrder;
    private String priority;
    private String mark;
    private List<String> tags;
    private String automation;
    private String coverage;
    private List<String> platform;
    private List<String> belongsPlatform;
    private List<MindNodeDTO> children;
    private Integer commentCount;
}
