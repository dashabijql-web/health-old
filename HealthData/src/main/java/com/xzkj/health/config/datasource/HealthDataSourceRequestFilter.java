package com.xzkj.health.config.datasource;

import com.xzkj.health.common.exception.ClientAbortExceptions;
import com.xzkj.health.observability.HealthMetricsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class HealthDataSourceRequestFilter extends OncePerRequestFilter {

    private final HealthDataSourceProperties properties;
    private final WatchDataSourceResolver resolver;
    private final HealthDataSourceOverrideAuthorizer overrideAuthorizer;
    private final HealthMetricsService healthMetricsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestedSource = request.getHeader(properties.getRequestHeader());
        if (requestedSource == null || requestedSource.isBlank()) {
            requestedSource = readCookie(request, properties.getRequestCookie());
        }

        HealthDataSourceKey defaultSource = properties.getRequestDefaultSource();
        HealthDataSourceKey requested = resolver.resolveRequestSource(requestedSource);
        HealthDataSourceKey effective = defaultSource;
        String metricOutcome = "default";

        if (isOverrideRequest(requestedSource, requested, defaultSource)) {
            String username = overrideAuthorizer.getCurrentUsername(request);
            if (overrideAuthorizer.canOverride(request, username)) {
                effective = requested;
                metricOutcome = "override-applied";
                if (properties.isAuditRequestOverride()) {
                    log.info("数据源切换生效: user={}, path={}, source={}",
                            username == null ? "anonymous" : username,
                            request.getRequestURI(),
                            effective.key());
                }
            } else if (properties.isAuditRequestOverride()) {
                metricOutcome = "override-denied";
                log.warn("数据源切换被拒绝: user={}, path={}, requested={}, fallback={}",
                        username == null ? "anonymous" : username,
                        request.getRequestURI(),
                        requested.key(),
                        defaultSource.key());
            }
        }

        HealthDataSourceContext.set(effective);
        response.setHeader(properties.getRequestHeader(), effective.key());
        healthMetricsService.recordRequestSourceHit(effective.key(), metricOutcome);
        try {
            filterChain.doFilter(request, response);
        } catch (IOException e) {
            if (ClientAbortExceptions.isClientAbort(e)) {
                log.debug("客户端中断请求: {} {}", request.getMethod(), request.getRequestURI());
                return;
            }
            throw e;
        } finally {
            HealthDataSourceContext.clear();
        }
    }

    private String readCookie(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookieName == null || cookieName.isBlank()) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private boolean isOverrideRequest(String raw, HealthDataSourceKey requested, HealthDataSourceKey defaultSource) {
        return raw != null
                && !raw.isBlank()
                && requested != null
                && requested != defaultSource;
    }
}
