package com.caseflow.dto;

import lombok.Data;
import java.util.List;

@Data
public class ReviewRequest {
    private Long caseSetId;
    private List<Long> reviewerIds;
}
