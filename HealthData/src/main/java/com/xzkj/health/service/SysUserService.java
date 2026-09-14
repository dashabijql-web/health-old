package com.xzkj.health.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xzkj.health.model.entity.SysUser;

import java.util.List;

public interface SysUserService extends IService<SysUser> {

    SysUser getByUsername(String username);

    boolean existsByUsername(String username);

    // 新加：登录校验（内部用）
    SysUser login(String username, String password);

    // 权限相关（模拟或实际实现）
    List<String> getUserRoles(Long userId);

    List<String> getUserButtons(Long userId);

    List<String> getUserRoutes(Long userId);

}
