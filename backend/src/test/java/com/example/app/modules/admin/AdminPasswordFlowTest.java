// filepath: /backend/src/test/java/com/example/app/modules/admin/AdminPasswordFlowTest.java
package com.example.app.modules.admin;

import com.example.app.modules.admin.service.impl.AdminAuthServiceImpl;
import com.example.app.modules.auth.dto.ChangePasswordRequestDTO;
import com.example.app.modules.auth.dto.ForgotPasswordRequestDTO;
import com.example.app.modules.auth.dto.LoginRequestDTO;
import com.example.app.modules.auth.dto.LoginResponseDTO;
import com.example.app.modules.auth.dto.ResetPasswordRequestDTO;
import com.example.app.modules.auth.entity.User;
import com.example.app.modules.auth.exception.IncorrectCurrentPasswordException;
import com.example.app.modules.auth.exception.InvalidCredentialsException;
import com.example.app.modules.auth.exception.InvalidOrExpiredResetTokenException;
import com.example.app.modules.auth.repository.UserRepository;
import com.example.app.modules.auth.service.impl.AuthServiceImpl;
import com.example.app.modules.usertype.UserTypeCode;
import com.example.app.shared.activitylog.ActivityLogService;
import com.example.app.shared.email.EmailService;
import com.example.app.shared.security.JwtService;
import com.example.app.shared.security.Roles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ubah password (saat login) dan lupa/reset password untuk akun ADMIN. Endpoint-nya sama dengan
 * user biasa (/api/auth/*); test ini mengunci bahwa perannya tidak hilang dan sesi lama dicabut.
 */
class AdminPasswordFlowTest {

    private static final String OLD_PASSWORD = "Str0ng!Pass";
    private static final String NEW_PASSWORD = "Baru#Pass9";
    private static final String EMAIL = "admin@example.com";

    private UserRepository userRepository;
    private EmailService emailService;
    private JwtService jwtService;
    private AuthServiceImpl authService;
    private AdminAuthServiceImpl adminAuthService;
    private User admin;

    @BeforeEach
    void setUp() {
        userRepository = AdminTestSupport.userRepository();
        emailService = mock(EmailService.class);
        jwtService = AdminTestSupport.jwtService();
        ActivityLogService activityLogService = mock(ActivityLogService.class);

        authService = new AuthServiceImpl(
                userRepository, AdminTestSupport.ENCODER, emailService, jwtService,
                activityLogService, AdminTestSupport.userTypeRepositoryWithAllTypes());
        ReflectionTestUtils.setField(authService, "backendBaseUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(authService, "frontendBaseUrl", "http://localhost:5173");
        ReflectionTestUtils.setField(authService, "verificationTokenExpiryMinutes", 1440L);
        ReflectionTestUtils.setField(authService, "resetPasswordTokenExpiryMinutes", 60L);

        adminAuthService = new AdminAuthServiceImpl(
                userRepository, AdminTestSupport.userTypeRepositoryWithAllTypes(), AdminTestSupport.ENCODER,
                emailService, jwtService, activityLogService);
        ReflectionTestUtils.setField(adminAuthService, "backendBaseUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(adminAuthService, "verificationTokenExpiryMinutes", 1440L);
        ReflectionTestUtils.setField(adminAuthService, "bootstrapCode", "");

        admin = AdminTestSupport.user(EMAIL, UserTypeCode.ADMIN, true, OLD_PASSWORD);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(admin));
    }

    private ChangePasswordRequestDTO change(String current, String next, String confirm) {
        ChangePasswordRequestDTO r = new ChangePasswordRequestDTO();
        r.setCurrentPassword(current);
        r.setNewPassword(next);
        r.setConfirmNewPassword(confirm);
        return r;
    }

    private ForgotPasswordRequestDTO forgot(String email) {
        ForgotPasswordRequestDTO r = new ForgotPasswordRequestDTO();
        r.setEmail(email);
        return r;
    }

    private ResetPasswordRequestDTO reset(String token, String password) {
        ResetPasswordRequestDTO r = new ResetPasswordRequestDTO();
        r.setToken(token);
        r.setNewPassword(password);
        r.setConfirmNewPassword(password);
        return r;
    }

    private LoginRequestDTO login(String password) {
        LoginRequestDTO r = new LoginRequestDTO();
        r.setEmail(EMAIL);
        r.setPassword(password);
        return r;
    }

    private String requestResetToken() {
        authService.forgotPassword(forgot(EMAIL));
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(eq(EMAIL), link.capture());
        String prefix = "http://localhost:5173/admin/reset-password?token=";
        assertTrue(link.getValue().startsWith(prefix), link.getValue());
        String token = link.getValue().substring(prefix.length());
        when(userRepository.findByResetPasswordToken(token)).thenReturn(Optional.of(admin));
        return token;
    }

    // ---------------------------------------------------------------- ubah password

    @Test
    void changePasswordKeepsAdminRoleInNewTokenAndBody() {
        String oldToken = jwtService.generateToken(EMAIL, admin.getPassword(), Roles.ADMIN);

        LoginResponseDTO session = authService.changePassword(EMAIL, change(OLD_PASSWORD, NEW_PASSWORD, NEW_PASSWORD));

        assertEquals("ADMIN", session.getRole());
        JwtService.ParsedToken parsed = jwtService.parse(session.getAccessToken()).orElseThrow();
        assertEquals(EMAIL, parsed.email());
        assertEquals("ADMIN", parsed.role());
        // versi password di token baru = password baru; token lama tidak lagi cocok
        assertEquals(jwtService.passwordFingerprint(admin.getPassword()), parsed.passwordVersion());
        assertNotEquals(
                jwtService.passwordFingerprint(admin.getPassword()),
                jwtService.parse(oldToken).orElseThrow().passwordVersion());
        assertTrue(AdminTestSupport.ENCODER.matches(NEW_PASSWORD, admin.getPassword()));
    }

    @Test
    void changePasswordWithWrongCurrentPasswordChangesNothing() {
        String hashBefore = admin.getPassword();

        assertThrows(IncorrectCurrentPasswordException.class,
                () -> authService.changePassword(EMAIL, change("Salah!Pass1", NEW_PASSWORD, NEW_PASSWORD)));

        assertEquals(hashBefore, admin.getPassword());
    }

    @Test
    void adminCanLoginWithNewPasswordOnlyAfterChange() {
        authService.changePassword(EMAIL, change(OLD_PASSWORD, NEW_PASSWORD, NEW_PASSWORD));

        assertEquals("ADMIN", adminAuthService.login(login(NEW_PASSWORD)).getRole());
        assertThrows(InvalidCredentialsException.class, () -> adminAuthService.login(login(OLD_PASSWORD)));
    }

    // ---------------------------------------------------------------- lupa / reset password

    @Test
    void forgotPasswordForVerifiedAdminSendsAdminResetLink() {
        String token = requestResetToken();

        assertEquals(token, admin.getResetPasswordToken());
        assertTrue(admin.getResetPasswordTokenExpiresAt().isAfter(java.time.LocalDateTime.now()));
    }

    @Test
    void forgotPasswordForUnverifiedAdminIsSilentlyIgnored() {
        User pending = AdminTestSupport.user("baru@example.com", UserTypeCode.ADMIN, false, OLD_PASSWORD);
        pending.setAdminInvitationToken("tok-undangan");
        when(userRepository.findByEmail("baru@example.com")).thenReturn(Optional.of(pending));

        authService.forgotPassword(forgot("baru@example.com")); // tidak melempar apa pun

        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString());
        assertNull(pending.getResetPasswordToken());
        assertNull(pending.getResetPasswordTokenExpiresAt());
        assertEquals("tok-undangan", pending.getAdminInvitationToken()); // undangan tidak tersentuh
    }

    @Test
    void forgotPasswordForDeactivatedAdminIsSilentlyIgnored() {
        admin.setActive(false);

        authService.forgotPassword(forgot(EMAIL)); // tidak melempar apa pun

        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString());
        assertNull(admin.getResetPasswordToken());
    }

    @Test
    void resetPasswordWithAnOutstandingTokenIsRejectedOnceAdminIsDeactivated() {
        String token = requestResetToken();
        admin.setActive(false);
        String hashBefore = admin.getPassword();

        assertThrows(InvalidOrExpiredResetTokenException.class,
                () -> authService.resetPassword(reset(token, NEW_PASSWORD)));
        assertThrows(InvalidOrExpiredResetTokenException.class, () -> authService.validateResetToken(token));

        assertEquals(hashBefore, admin.getPassword());
    }

    @Test
    void forgotPasswordForUnverifiedRegularUserStillSendsLink() {
        // Perilaku user biasa tidak berubah oleh aturan khusus admin.
        User user = AdminTestSupport.user("u@example.com", UserTypeCode.FREE, false, OLD_PASSWORD);
        when(userRepository.findByEmail("u@example.com")).thenReturn(Optional.of(user));

        authService.forgotPassword(forgot("u@example.com"));

        verify(emailService).sendPasswordResetEmail(
                eq("u@example.com"),
                startsWith("http://localhost:5173/auth/reset-password?token="));
    }

    @Test
    void resetPasswordIsSingleUseAndAdminCanThenLoginWithNewPassword() {
        String token = requestResetToken();
        String oldHash = admin.getPassword();
        String oldSessionPv = jwtService.passwordFingerprint(oldHash);

        authService.resetPassword(reset(token, NEW_PASSWORD));

        assertNull(admin.getResetPasswordToken());
        assertNull(admin.getResetPasswordTokenExpiresAt());
        // token sesi lama tidak cocok lagi dengan password baru
        assertNotEquals(oldSessionPv, jwtService.passwordFingerprint(admin.getPassword()));
        assertEquals("ADMIN", adminAuthService.login(login(NEW_PASSWORD)).getRole());
        assertThrows(InvalidCredentialsException.class, () -> adminAuthService.login(login(OLD_PASSWORD)));

        // token yang sama tidak bisa dipakai dua kali
        when(userRepository.findByResetPasswordToken(token)).thenReturn(Optional.empty());
        assertThrows(InvalidOrExpiredResetTokenException.class,
                () -> authService.resetPassword(reset(token, "Lain#Pass7")));
    }

    @Test
    void resetPasswordWithExpiredTokenIsRejected() {
        String token = requestResetToken();
        admin.setResetPasswordTokenExpiresAt(java.time.LocalDateTime.now().minusMinutes(1));
        String hashBefore = admin.getPassword();

        assertThrows(InvalidOrExpiredResetTokenException.class,
                () -> authService.resetPassword(reset(token, NEW_PASSWORD)));

        assertEquals(hashBefore, admin.getPassword());
    }
}
