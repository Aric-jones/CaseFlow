package com.caseflow.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MindNodeDTO {
    private String id;
    private String text;
    private String nodeType;
    private Integer sortOrder;
    private Integer isRoot;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, Object> properties;
    private List<MindNodeDTO> children;
    private Integer commentCount;
}
