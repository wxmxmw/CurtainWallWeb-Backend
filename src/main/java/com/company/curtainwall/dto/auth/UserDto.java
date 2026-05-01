package com.company.curtainwall.dto.auth;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserDto {
    private String id;
    private String username;
    private String role;
    private LocalDateTime lastLoginAt;
}
