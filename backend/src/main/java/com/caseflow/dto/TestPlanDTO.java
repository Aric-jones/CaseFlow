package com.caseflow.dto;

import lombok.Data;
import java.util.List;

@Data
public class TestPlanDTO {
    private String name;
    private Long directoryId;
    private Long projectId;
    private List<Long> executorIds;
    private List<TestPlanCaseDTO> cases;
}
