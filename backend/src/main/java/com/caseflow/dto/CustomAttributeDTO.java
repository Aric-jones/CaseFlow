package com.caseflow.dto;

import lombok.Data;
import java.util.List;

@Data
public class CustomAttributeDTO {
    private String projectId;
    private String name;
    private List<String> options;
    private Integer multiSelect;
    private String nodeTypeLimit;
    private String displayType;
}
