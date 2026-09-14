package com.xzkj.health.controller;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AuthControllerTest {

    @Test
    void logoutUsesExplicitSaTokenHeaderBeforeCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("satoken", "header-token");
        request.setCookies(new Cookie("satoken", "cookie-token"));

        assertEquals("header-token", AuthController.resolveLogoutToken(request));
    }

    @Test
    void logoutFallsBackToSaTokenCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("satoken", "cookie-token"));

        assertEquals("cookie-token", AuthController.resolveLogoutToken(request));
    }

    @Test
    void logoutDoesNotInventAToken() {
        assertNull(AuthController.resolveLogoutToken(new MockHttpServletRequest()));
    }
}
