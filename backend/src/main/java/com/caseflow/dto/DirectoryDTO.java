package com.caseflow.dto;

import lombok.Data;
import java.util.List;

@Data
public class DirectoryDTO {
    private String id;
    private String name;
    private String parentId;
    private String projectId;
    private String dirType;
    private Integer sortOrder;
    private List<DirectoryDTO> children;
}
