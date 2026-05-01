package com.company.curtainwall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.company.curtainwall.dto.auth.AuthResponse;
import com.company.curtainwall.dto.auth.UserDto;
import com.company.curtainwall.entity.SysUser;
import com.company.curtainwall.mapper.SysUserMapper;
import com.company.curtainwall.service.SysUserService;
import com.company.curtainwall.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public AuthResponse login(String username, String password) {
        SysUser user = this.getOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new RuntimeException("用户名或密码错误");
        }

        // Update last login
        user.setLastLoginAt(LocalDateTime.now());
        this.updateById(user);

        return buildAuthResponse(user);
    }

    @Override
    @Transactional
    public AuthResponse register(String username, String password) {
        if (this.count(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username)) > 0) {
            throw new RuntimeException("用户名已存在");
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole("user");
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        this.save(user);

        return buildAuthResponse(user);
    }

    @Override
    public SysUser getByUsername(String username) {
        return this.getOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
    }

    private AuthResponse buildAuthResponse(SysUser user) {
        String token = jwtUtil.generateToken(user.getUsername(), user.getId());

        UserDto userDto = new UserDto();
        userDto.setId(String.valueOf(user.getId()));
        userDto.setUsername(user.getUsername());
        userDto.setRole(user.getRole());
        userDto.setLastLoginAt(user.getLastLoginAt());

        return AuthResponse.builder()
                .token(token)
                .user(userDto)
                .build();
    }
}
