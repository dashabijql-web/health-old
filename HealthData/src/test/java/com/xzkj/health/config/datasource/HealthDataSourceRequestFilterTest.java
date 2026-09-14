package com.xzkj.health.config.datasource;

import com.xzkj.health.observability.HealthMetricsService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HealthDataSourceRequestFilterTest {

    @Test
    void ignoresExternalOverrideWhenRequesterIsNotAuthorized() throws Exception {
        HealthDataSourceProperties properties = new HealthDataSourceProperties();
        properties.setRequestDefaultSource(HealthDataSourceKey.OLD);
        properties.setRequestHeader("X-Health-Data-Source");

        WatchDataSourceResolver resolver = mock(WatchDataSourceResolver.class);
        when(resolver.resolveRequestSource("new")).thenReturn(HealthDataSourceKey.NEW);

        HealthDataSourceOverrideAuthorizer authorizer = mock(HealthDataSourceOverrideAuthorizer.class);
        when(authorizer.canOverride(any(), any())).thenReturn(false);
        HealthMetricsService healthMetricsService = mock(HealthMetricsService.class);

        HealthDataSourceRequestFilter filter =
                new HealthDataSourceRequestFilter(properties, resolver, authorizer, healthMetricsService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health/dashboard/overview");
        request.setContextPath("/health");
        request.addHeader("X-Health-Data-Source", "new");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<HealthDataSourceKey> seenSource = new AtomicReference<>();
        FilterChain chain = (req, res) -> seenSource.set(HealthDataSourceContext.get());

        filter.doFilter(request, response, chain);

        verify(authorizer).canOverride(eq(request), any());
        verify(healthMetricsService).recordRequestSourceHit("old", "override-denied");
        assertEquals(HealthDataSourceKey.OLD, seenSource.get());
        assertEquals("old", response.getHeader("X-Health-Data-Source"));
        assertNull(HealthDataSourceContext.get());
    }

    @Test
    void appliesExternalOverrideWhenRequesterIsAuthorized() throws Exception {
        HealthDataSourceProperties properties = new HealthDataSourceProperties();
        properties.setRequestDefaultSource(HealthDataSourceKey.OLD);
        properties.setRequestHeader("X-Health-Data-Source");

        WatchDataSourceResolver resolver = mock(WatchDataSourceResolver.class);
        when(resolver.resolveRequestSource("new")).thenReturn(HealthDataSourceKey.NEW);

        HealthDataSourceOverrideAuthorizer authorizer = mock(HealthDataSourceOverrideAuthorizer.class);
        when(authorizer.canOverride(any(), any())).thenReturn(true);
        HealthMetricsService healthMetricsService = mock(HealthMetricsService.class);

        HealthDataSourceRequestFilter filter =
                new HealthDataSourceRequestFilter(properties, resolver, authorizer, healthMetricsService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health/dashboard/overview");
        request.setContextPath("/health");
        request.addHeader("X-Health-Data-Source", "new");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<HealthDataSourceKey> seenSource = new AtomicReference<>();
        FilterChain chain = (req, res) -> seenSource.set(HealthDataSourceContext.get());

        filter.doFilter(request, response, chain);

        verify(authorizer).canOverride(eq(request), any());
        verify(healthMetricsService).recordRequestSourceHit("new", "override-applied");
        assertEquals(HealthDataSourceKey.NEW, seenSource.get());
        assertEquals("new", response.getHeader("X-Health-Data-Source"));
        assertNull(HealthDataSourceContext.get());
    }

    @Test
    void readsCookieOverrideWhenHeaderMissing() throws Exception {
        HealthDataSourceProperties properties = new HealthDataSourceProperties();
        properties.setRequestDefaultSource(HealthDataSourceKey.OLD);
        properties.setRequestHeader("X-Health-Data-Source");
        properties.setRequestCookie("Health-Data-Source");

        WatchDataSourceResolver resolver = mock(WatchDataSourceResolver.class);
        when(resolver.resolveRequestSource("new")).thenReturn(HealthDataSourceKey.NEW);

        HealthDataSourceOverrideAuthorizer authorizer = mock(HealthDataSourceOverrideAuthorizer.class);
        when(authorizer.canOverride(any(), any())).thenReturn(true);
        HealthMetricsService healthMetricsService = mock(HealthMetricsService.class);

        HealthDataSourceRequestFilter filter =
                new HealthDataSourceRequestFilter(properties, resolver, authorizer, healthMetricsService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health/dashboard/overview");
        request.setContextPath("/health");
        request.setCookies(new Cookie("Health-Data-Source", "new"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<HealthDataSourceKey> seenSource = new AtomicReference<>();
        FilterChain chain = (req, res) -> seenSource.set(HealthDataSourceContext.get());

        filter.doFilter(request, response, chain);

        verify(authorizer).canOverride(eq(request), any());
        verify(healthMetricsService).recordRequestSourceHit("new", "override-applied");
        assertEquals(HealthDataSourceKey.NEW, seenSource.get());
        assertEquals("new", response.getHeader("X-Health-Data-Source"));
        assertNull(HealthDataSourceContext.get());
    }

    @Test
    void swallowsClientAbortWithoutLeakingDataSourceContext() throws Exception {
        HealthDataSourceProperties properties = new HealthDataSourceProperties();
        properties.setRequestDefaultSource(HealthDataSourceKey.OLD);
        properties.setRequestHeader("X-Health-Data-Source");

        HealthDataSourceRequestFilter filter = new HealthDataSourceRequestFilter(
                properties,
                mock(WatchDataSourceResolver.class),
                mock(HealthDataSourceOverrideAuthorizer.class),
                mock(HealthMetricsService.class)
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health/dashboard/overview");
        request.setContextPath("/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            throw new java.io.IOException("Broken pipe");
        };

        filter.doFilter(request, response, chain);
        assertNull(HealthDataSourceContext.get());
    }
}
