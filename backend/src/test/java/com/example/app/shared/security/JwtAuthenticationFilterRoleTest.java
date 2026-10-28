// filepath: /backend/src/test/java/com/example/app/shared/security/JwtAuthenticationFilterRoleTest.java
package com.example.app.shared.security;

import com.example.app.modules.admin.AdminTestSupport;
import com.example.app.modules.auth.entity.User;
import com.example.app.modules.auth.repository.UserRepository;
import com.example.app.modules.usertype.UserTypeCode;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Peran diambil dari user_type di DB, bukan dari claim token. */
class JwtAuthenticationFilterRoleTest {

    private UserRepository userRepository;
    private JwtService jwtService;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        userRepository = mock(UserRepository.class);
        jwtService = AdminTestSupport.jwtService();
        filter = new JwtAuthenticationFilter(jwtService, userRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Authentication run(String bearerToken) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + bearerToken);
        filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private String authority(Authentication auth) {
        return auth.getAuthorities().iterator().next().getAuthority();
    }

    @Test
    void adminUserGetsRoleAdmin() throws Exception {
        User admin = AdminTestSupport.user("admin@example.com", UserTypeCode.ADMIN, true, "Str0ng!Pass");
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));

        Authentication auth = run(jwtService.generateToken("admin@example.com", admin.getPassword(), Roles.ADMIN));

        assertEquals("admin@example.com", auth.getName());
        assertEquals("ROLE_ADMIN", authority(auth));
    }

    @Test
    void regularAndVipUsersGetRoleUser() throws Exception {
        for (String type : new String[]{UserTypeCode.FREE, UserTypeCode.VIP_MONTHLY, UserTypeCode.VIP_YEARLY}) {
            SecurityContextHolder.clearContext();
            User user = AdminTestSupport.user("u@example.com", type, true, "Str0ng!Pass");
            when(userRepository.findByEmail("u@example.com")).thenReturn(Optional.of(user));

            Authentication auth = run(jwtService.generateToken("u@example.com", user.getPassword(), Roles.USER));

            assertEquals("ROLE_USER", authority(auth), "type=" + type);
        }
    }

    @Test
    void roleClaimInTokenIsNotTrustedOverDatabase() throws Exception {
        // Token mengaku ADMIN, tetapi di DB user biasa -> tetap ROLE_USER.
        User user = AdminTestSupport.user("u@example.com", UserTypeCode.FREE, true, "Str0ng!Pass");
        when(userRepository.findByEmail("u@example.com")).thenReturn(Optional.of(user));

        Authentication auth = run(jwtService.generateToken("u@example.com", user.getPassword(), Roles.ADMIN));

        assertEquals("ROLE_USER", authority(auth));
    }

    @Test
    void demotedOrChangedTypeTakesEffectImmediatelyWithoutNewToken() throws Exception {
        User admin = AdminTestSupport.user("a@example.com", UserTypeCode.ADMIN, true, "Str0ng!Pass");
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(admin));
        String token = jwtService.generateToken("a@example.com", admin.getPassword(), Roles.ADMIN);
        assertEquals("ROLE_ADMIN", authority(run(token)));

        SecurityContextHolder.clearContext();
        admin.setUserType(UserTypeCode.FREE);
        assertEquals("ROLE_USER", authority(run(token)));
    }

    @Test
    void adminTokenIsRejectedAfterPasswordChangeButNewTokenWorks() throws Exception {
        User admin = AdminTestSupport.user("a@example.com", UserTypeCode.ADMIN, true, "Str0ng!Pass");
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(admin));
        String oldToken = jwtService.generateToken("a@example.com", admin.getPassword(), Roles.ADMIN);
        assertEquals("ROLE_ADMIN", authority(run(oldToken)));

        // password berubah (ubah/reset password) -> token lama dicabut, token baru berlaku
        SecurityContextHolder.clearContext();
        admin.setPassword(AdminTestSupport.ENCODER.encode("Baru#Pass9"));
        assertNull(run(oldToken));

        SecurityContextHolder.clearContext();
        String newToken = jwtService.generateToken("a@example.com", admin.getPassword(), Roles.ADMIN);
        assertEquals("ROLE_ADMIN", authority(run(newToken)));
    }

    @Test
    void deactivatedAdminLosesAccessImmediatelyAndRegainsItWhenReactivated() throws Exception {
        User admin = AdminTestSupport.user("a@example.com", UserTypeCode.ADMIN, true, "Str0ng!Pass");
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(admin));
        String token = jwtService.generateToken("a@example.com", admin.getPassword(), Roles.ADMIN);
        assertEquals("ROLE_ADMIN", authority(run(token)));

        // Dinonaktifkan: token yang SAMA (masih berlaku & tanda tangan benar) langsung ditolak.
        SecurityContextHolder.clearContext();
        admin.setActive(false);
        assertNull(run(token));

        SecurityContextHolder.clearContext();
        admin.setActive(true);
        assertEquals("ROLE_ADMIN", authority(run(token)));
    }

    @Test
    void unverifiedAdminIsNotAuthenticated() throws Exception {
        User admin = AdminTestSupport.user("a@example.com", UserTypeCode.ADMIN, false, "Str0ng!Pass");
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(admin));

        assertNull(run(jwtService.generateToken("a@example.com", admin.getPassword(), Roles.ADMIN)));
    }
}
