package com.xzkj.health.config;

import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaTokenConfigTest {

    private final SaTokenConfig config = new SaTokenConfig();

    @Test
    void skipsLoginCheckForAsyncAndErrorDispatches() {
        MockHttpServletRequest asyncRequest = new MockHttpServletRequest("POST", "/health/ai/chat/stream");
        asyncRequest.setContextPath("/health");
        asyncRequest.setDispatcherType(DispatcherType.ASYNC);

        MockHttpServletRequest errorRequest = new MockHttpServletRequest("POST", "/health/ai/chat/stream");
        errorRequest.setContextPath("/health");
        errorRequest.setDispatcherType(DispatcherType.ERROR);

        assertFalse(config.shouldCheckLogin(asyncRequest));
        assertFalse(config.shouldCheckLogin(errorRequest));
    }

    @Test
    void checksLoginForNormalBusinessRequests() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/health/ai/chat/stream");
        request.setContextPath("/health");

        assertTrue(config.shouldCheckLogin(request));
    }

    @Test
    void skipsLoginWhitelistWithContextPath() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/health/auth/login");
        request.setContextPath("/health");

        assertFalse(config.shouldCheckLogin(request));
    }

}
