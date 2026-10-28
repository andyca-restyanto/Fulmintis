// filepath: /backend/src/test/java/com/example/app/shared/security/RateLimitFilterAdminTest.java
package com.example.app.shared.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class RateLimitFilterAdminTest {

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter();
        ReflectionTestUtils.setField(filter, "enabled", true);
        ReflectionTestUtils.setField(filter, "loginPerMinute", 20);
        ReflectionTestUtils.setField(filter, "registerPerMinute", 10);
        ReflectionTestUtils.setField(filter, "emailPerMinute", 5);
        ReflectionTestUtils.setField(filter, "searchPerMinute", 30);
        ReflectionTestUtils.setField(filter, "adminRegisterPerMinute", 5);
    }

    private int hit(String method, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr("10.0.0.7");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, mock(FilterChain.class));
        return response.getStatus();
    }

    @Test
    void adminRegisterIsStricterThanUserRegister() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertEquals(200, hit("POST", "/api/admin/auth/register"), "percobaan ke-" + (i + 1));
        }
        assertEquals(429, hit("POST", "/api/admin/auth/register"));
    }

    @Test
    void adminLoginAndInviteAreLimited() throws Exception {
        for (int i = 0; i < 20; i++) {
            hit("POST", "/api/admin/auth/login");
        }
        assertEquals(429, hit("POST", "/api/admin/auth/login"));

        for (int i = 0; i < 5; i++) {
            hit("POST", "/api/admin/admins");
        }
        assertEquals(429, hit("POST", "/api/admin/admins"));
    }

    @Test
    void changePasswordIsLimitedPerIpAndIndependentFromOtherRules() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertEquals(200, hit("POST", "/api/auth/change-password"), "percobaan ke-" + (i + 1));
        }
        assertEquals(429, hit("POST", "/api/auth/change-password"));

        // aturan lain (jendela terpisah) tidak ikut terkunci
        assertEquals(200, hit("POST", "/api/auth/login"));
        assertEquals(200, hit("POST", "/api/auth/forgot-password"));
    }

    @Test
    void changePasswordLimitAppliesOnlyToPost() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertEquals(200, hit("GET", "/api/auth/change-password"));
        }
    }

    @Test
    void publicAdminLookupsAreLimited() throws Exception {
        for (int i = 0; i < 30; i++) {
            hit("GET", "/api/admin/auth/registration-status");
        }
        assertEquals(429, hit("GET", "/api/admin/auth/registration-status"));
    }
}
