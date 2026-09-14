package com.xzkj.health.config.security;

import com.xzkj.health.common.exception.BusinessException;
import com.xzkj.health.service.SysUserService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeviceDiagnosticsAuthorizerTest {

    @Test
    void allowsUsersWithDevicePermission() {
        SysUserService userService = mock(SysUserService.class);
        when(userService.getUserRoutes(42L)).thenReturn(List.of("device:list"));

        DeviceDiagnosticsAuthorizer authorizer = new DeviceDiagnosticsAuthorizer(userService);

        assertDoesNotThrow(() -> authorizer.checkUser(42L));
    }

    @Test
    void deniesByDefaultWithoutDevicePermission() {
        SysUserService userService = mock(SysUserService.class);
        when(userService.getUserRoutes(42L)).thenReturn(List.of("health:realtime"));

        DeviceDiagnosticsAuthorizer authorizer = new DeviceDiagnosticsAuthorizer(userService);
        BusinessException error = assertThrows(BusinessException.class, () -> authorizer.checkUser(42L));

        assertEquals(403, error.getCode());
    }
}
