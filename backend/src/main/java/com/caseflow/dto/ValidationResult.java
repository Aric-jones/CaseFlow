package com.caseflow.dto;

import lombok.Data;
import java.util.List;

@Data
public class ValidationResult {
    private boolean valid;
    private int errorCount;
    private List<ValidationError> errors;

    @Data
    public static class ValidationError {
        private Long nodeId;
        private String nodePath;
        private String message;
    }
}
