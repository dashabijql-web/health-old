package com.xzkj.health.config.datasource;

import cn.dev33.satoken.stp.StpUtil;
import com.xzkj.health.model.entity.SysUser;
import com.xzkj.health.service.SysUserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

@Component
@RequiredArgsConstructor
public class HealthDataSourceOverrideAuthorizer {

    private final HealthDataSourceProperties properties;
    private final SysUserService sysUserService;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public boolean canOverride(HttpServletRequest request) {
        return canOverride(request, getCurrentUsername(request));
    }

    public boolean canOverride(HttpServletRequest request, String username) {
        if (!properties.isAllowRequestOverride()) {
            return false;
        }
        if (!matchesPath(normalizePath(request))) {
            return false;
        }
        return canSwitchDataSource(username);
    }

    public boolean canSwitchDataSource(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        return properties.getRequestOverrideUsernames().stream()
                .filter(item -> item != null && !item.isBlank())
                .anyMatch(item -> item.equalsIgnoreCase(username.trim()));
    }

    public String getCurrentUsername() {
        return getCurrentUsername(null);
    }

    public String getCurrentUsername(HttpServletRequest request) {
        try {
            Long userId = resolveCurrentUserId(request);
            if (userId == null) {
                return null;
            }
            SysUser user = sysUserService.getById(userId);
            return user != null ? user.getUsername() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean matchesPath(String path) {
        return properties.getRequestOverridePaths().stream()
                .filter(pattern -> pattern != null && !pattern.isBlank())
                .anyMatch(pattern -> pathMatcher.match(pattern.trim(), path));
    }

    private Long resolveCurrentUserId(HttpServletRequest request) {
        String token = resolveTokenValue(request);
        if (token != null) {
            try {
                Object loginId = StpUtil.getLoginIdByToken(token);
                if (loginId instanceof Number number) {
                    return number.longValue();
                }
                if (loginId != null) {
                    return Long.parseLong(String.valueOf(loginId));
                }
            } catch (Exception ignored) {
                // fall through to the current thread-local login state
            }
        }

        try {
            if (StpUtil.isLogin()) {
                return StpUtil.getLoginIdAsLong();
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private String resolveTokenValue(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        String tokenName = StpUtil.getTokenName();
        String token = readHeaderOrParameter(request, tokenName);
        if (token == null || token.isBlank()) {
            token = readCookie(request, tokenName);
        }
        if (token == null) {
            return null;
        }

        String normalized = token.trim();
        if (normalized.regionMatches(true, 0, "Bearer ", 0, 7)) {
            normalized = normalized.substring(7).trim();
        }
        return normalized.isBlank() ? null : normalized;
    }

    private String readHeaderOrParameter(HttpServletRequest request, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) {
            value = request.getParameter(name);
        }
        return value;
    }

    private String readCookie(HttpServletRequest request, String cookieName) {
        if (cookieName == null || cookieName.isBlank()) {
            return null;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private String normalizePath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (requestUri == null || requestUri.isBlank()) {
            return "/";
        }
        if (contextPath != null && !contextPath.isBlank() && requestUri.startsWith(contextPath)) {
            String normalized = requestUri.substring(contextPath.length());
            return normalized.isBlank() ? "/" : normalized;
        }
        return requestUri;
    }
}
