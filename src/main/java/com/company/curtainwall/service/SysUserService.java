package com.company.curtainwall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.company.curtainwall.dto.auth.AuthResponse;
import com.company.curtainwall.entity.SysUser;

public interface SysUserService extends IService<SysUser> {
    AuthResponse login(String username, String password);

    AuthResponse register(String username, String password);

    SysUser getByUsername(String username);
}
