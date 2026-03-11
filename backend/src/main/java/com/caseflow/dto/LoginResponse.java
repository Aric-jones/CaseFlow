package com.caseflow.dto;

import lombok.Data;

@Data
public class LoginResponse {
    private String token;
    private String userId;
    private String username;
    private String displayName;
    private String role;
}
