package com.company.curtainwall.controller;

import com.company.curtainwall.common.ApiResponse;
import com.company.curtainwall.dto.auth.AuthResponse;
import com.company.curtainwall.dto.auth.LoginRequest;
import com.company.curtainwall.dto.auth.RegisterRequest;
import com.company.curtainwall.dto.auth.UserDto;
import com.company.curtainwall.entity.SysUser;
import com.company.curtainwall.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping({"/api/auth", "/auth"})
@RequiredArgsConstructor
public class AuthController {

    private final SysUserService sysUserService;

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@RequestBody LoginRequest request) {
        AuthResponse response = sysUserService.login(request.getUsername(), request.getPassword());
        return ApiResponse.success("Login successful", response);
    }

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@RequestBody RegisterRequest request) {
        // Frontend should check confirm password, but we can double check here if
        // needed
        if (request.getConfirm() != null && !request.getPassword().equals(request.getConfirm())) {
            return ApiResponse.error("两次密码输入不一致");
        }

        AuthResponse response = sysUserService.register(request.getUsername(), request.getPassword());
        return ApiResponse.success("Registration successful", response);
    }

    @GetMapping("/profile")
    public Map<String, Object> profile() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        String username = (String) authentication.getPrincipal();
        SysUser user = sysUserService.getByUsername(username);

        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found");
        }

        UserDto userDto = new UserDto();
        userDto.setId(String.valueOf(user.getId()));
        userDto.setUsername(user.getUsername());
        userDto.setRole(user.getRole());
        userDto.setLastLoginAt(user.getLastLoginAt());

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", userDto);
        response.put("user", userDto);
        return response;
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        // Stateless JWT, client just drops token.
        return ApiResponse.successMessage("已退出登录");
    }
}
