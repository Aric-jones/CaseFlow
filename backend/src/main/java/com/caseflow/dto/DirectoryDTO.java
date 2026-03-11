package com.caseflow.dto;

import lombok.Data;
import java.util.List;

@Data
public class DirectoryDTO {
    private Long id;
    private String name;
    private Long parentId;
    private Long projectId;
    private String dirType;
    private Integer sortOrder;
    private List<DirectoryDTO> children;
}
