package com.caseflow.dto;

import lombok.Data;
import java.util.List;

@Data
public class UserDTO {
    private String username;
    private String displayName;
    private String password;
    private String role;
    private String identity;
    private List<Long> projectIds;
}
