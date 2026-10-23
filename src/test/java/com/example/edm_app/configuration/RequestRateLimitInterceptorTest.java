package com.example.edm_app.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestRateLimitInterceptorTest {

    @Test
    void loginRequestsAreLimitedPerClientIp() throws Exception {
        RequestRateLimitInterceptor interceptor = new RequestRateLimitInterceptor();
        MockHttpServletRequest request = request("/auth/login", "POST", "203.0.113.10");

        for (int i = 0; i < 10; i++) {
            assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), this));
        }

        MockHttpServletResponse limited = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, limited, this));
        assertEquals(429, limited.getStatus());
        assertNotNull(limited.getHeader("Retry-After"));
    }

    @Test
    void registrationLimitIsIndependentPerClientIp() throws Exception {
        RequestRateLimitInterceptor interceptor = new RequestRateLimitInterceptor();
        MockHttpServletRequest firstClient = request("/auth/register", "POST", "203.0.113.11");
        MockHttpServletRequest secondClient = request("/auth/register", "POST", "203.0.113.12");

        for (int i = 0; i < 5; i++) {
            assertTrue(interceptor.preHandle(firstClient, new MockHttpServletResponse(), this));
        }
        assertFalse(interceptor.preHandle(firstClient, new MockHttpServletResponse(), this));
        assertTrue(interceptor.preHandle(secondClient, new MockHttpServletResponse(), this));
    }

    private MockHttpServletRequest request(String path, String method, String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(path);
        request.setMethod(method);
        request.setRemoteAddr(ip);
        return request;
    }
}
