package com.xzkj.health.config.security;

import com.xzkj.health.common.exception.BusinessException;
import com.xzkj.health.service.SysUserService;
import org.springframework.stereotype.Component;

@Component
public class DeviceDiagnosticsAuthorizer {

    static final String REQUIRED_PERMISSION = "device:list";

    private final SysUserService userService;

    public DeviceDiagnosticsAuthorizer(SysUserService userService) {
        this.userService = userService;
    }

    public void checkUser(long userId) {
        if (!userService.getUserRoutes(userId).contains(REQUIRED_PERMISSION)) {
            throw new BusinessException(403, "无设备诊断权限");
        }
    }
}
