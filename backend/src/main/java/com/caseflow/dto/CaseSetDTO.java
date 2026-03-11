package com.caseflow.dto;

import lombok.Data;

@Data
public class CaseSetDTO {
    private String name;
    private Long directoryId;
    private Long projectId;
    private String requirementLink;
}
