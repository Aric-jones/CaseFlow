package com.caseflow.dto;

import lombok.Data;
import java.util.List;

@Data
public class TestPlanDTO {
    private String name;
    private String directoryId;
    private String projectId;
    private List<String> executorIds;
    private List<TestPlanCaseDTO> cases;
}
