package com.xzkj.health.config.datasource;

import cn.dev33.satoken.stp.StpUtil;
import com.xzkj.health.model.entity.SysUser;
import com.xzkj.health.service.SysUserService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class HealthDataSourceOverrideAuthorizerTest {

    @Test
    void resolvesUsernameFromRequestTokenAndAllowsAdminOverride() {
        HealthDataSourceProperties properties = new HealthDataSourceProperties();
        properties.setAllowRequestOverride(true);
        properties.setRequestOverridePaths(List.of("/**"));
        properties.setRequestOverrideUsernames(List.of("admin"));

        SysUserService sysUserService = mock(SysUserService.class);
        HealthDataSourceOverrideAuthorizer authorizer = new HealthDataSourceOverrideAuthorizer(properties, sysUserService);

        SysUser user = mock(SysUser.class);
        when(user.getUsername()).thenReturn("admin");
        when(sysUserService.getById(1L)).thenReturn(user);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health/dashboard/overview");
        request.setContextPath("/health");
        request.addHeader("satoken", "token-123");
        request.setCookies(new Cookie("satoken", "token-123"));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getTokenName).thenReturn("satoken");
            stp.when(() -> StpUtil.getLoginIdByToken("token-123")).thenReturn(1L);

            assertEquals("admin", authorizer.getCurrentUsername(request));
            assertTrue(authorizer.canOverride(request));
        }
    }
}
