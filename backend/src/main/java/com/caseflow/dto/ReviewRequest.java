package com.caseflow.dto;

import lombok.Data;
import java.util.List;

@Data
public class ReviewRequest {
    private String caseSetId;
    private List<String> reviewerIds;
}
