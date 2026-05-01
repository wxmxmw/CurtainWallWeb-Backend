package com.company.curtainwall.dto.auth;

import lombok.Data;

@Data
public class RegisterRequest {
    private String username;
    private String password;
    private String confirm;
}
