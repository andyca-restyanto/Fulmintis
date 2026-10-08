// filepath: /backend/src/test/java/com/example/app/modules/auth/AuthAdminSeparationTest.java
package com.example.app.modules.auth;

import com.example.app.modules.admin.AdminTestSupport;
import com.example.app.modules.auth.dto.ForgotPasswordRequestDTO;
import com.example.app.modules.auth.dto.LoginRequestDTO;
import com.example.app.modules.auth.dto.LoginResponseDTO;
import com.example.app.modules.auth.dto.RegisterRequestDTO;
import com.example.app.modules.auth.entity.User;
import com.example.app.modules.auth.exception.InvalidCredentialsException;
import com.example.app.modules.auth.repository.UserRepository;
import com.example.app.modules.auth.service.impl.AuthServiceImpl;
import com.example.app.modules.usertype.UserTypeCode;
import com.example.app.shared.activitylog.ActivityLogService;
import com.example.app.shared.email.EmailService;
import com.example.app.shared.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pemisahan login/verifikasi/reset antara user biasa dan admin di AuthServiceImpl. */
class AuthAdminSeparationTest {

    private static final String PASSWORD = "Str0ng!Pass";

    private UserRepository userRepository;
    private EmailService emailService;
    private JwtService jwtService;
    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        userRepository = AdminTestSupport.userRepository();
        emailService = mock(EmailService.class);
        jwtService = AdminTestSupport.jwtService();
        service = new AuthServiceImpl(
                userRepository, AdminTestSupport.ENCODER, emailService, jwtService,
                mock(ActivityLogService.class), AdminTestSupport.userTypeRepositoryWithAllTypes());
        ReflectionTestUtils.setField(service, "backendBaseUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "http://localhost:5173");
        ReflectionTestUtils.setField(service, "verificationTokenExpiryMinutes", 1440L);
        ReflectionTestUtils.setField(service, "resetPasswordTokenExpiryMinutes", 60L);
    }

    private LoginRequestDTO login(String email, String password) {
        LoginRequestDTO r = new LoginRequestDTO();
        r.setEmail(email);
        r.setPassword(password);
        return r;
    }

    @Test
    void adminCannotLoginThroughUserEndpointWithCorrectPassword() {
        User admin = AdminTestSupport.user("admin@example.com", UserTypeCode.ADMIN, true, PASSWORD);
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(userRepository.findByEmail("hantu@example.com")).thenReturn(Optional.empty());

        InvalidCredentialsException asAdmin = assertThrows(InvalidCredentialsException.class,
                () -> service.login(login("admin@example.com", PASSWORD)));
        InvalidCredentialsException unknown = assertThrows(InvalidCredentialsException.class,
                () -> service.login(login("hantu@example.com", PASSWORD)));

        assertEquals(unknown.getMessage(), asAdmin.getMessage()); // tidak membocorkan "ini akun admin"
    }

    @Test
    void userLoginResponseCarriesUserRoleInBodyAndToken() {
        User user = AdminTestSupport.user("u@example.com", UserTypeCode.VIP_MONTHLY, true, PASSWORD);
        when(userRepository.findByEmail("u@example.com")).thenReturn(Optional.of(user));

        LoginResponseDTO response = service.login(login("u@example.com", PASSWORD));

        assertEquals("USER", response.getRole());
        assertEquals("USER", jwtService.parse(response.getAccessToken()).orElseThrow().role());
    }

    @Test
    void publicRegisterAlwaysCreatesFreeUser() {
        when(userRepository.existsByEmail("baru@example.com")).thenReturn(false);
        RegisterRequestDTO request = new RegisterRequestDTO();
        request.setEmail("baru@example.com");
        request.setPassword(PASSWORD);
        request.setConfirmPassword(PASSWORD);

        service.register(request);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals(UserTypeCode.FREE, saved.getValue().getUserType());
    }

    @Test
    void verificationRedirectGoesToAdminSigninForAdminAndUserSigninForOthers() {
        User admin = AdminTestSupport.user("admin@example.com", UserTypeCode.ADMIN, false, PASSWORD);
        admin.setVerificationToken("tok-admin");
        admin.setVerificationTokenExpiresAt(LocalDateTime.now().plusHours(1));
        when(userRepository.findByVerificationToken("tok-admin")).thenReturn(Optional.of(admin));

        User user = AdminTestSupport.user("u@example.com", UserTypeCode.FREE, false, PASSWORD);
        user.setVerificationToken("tok-user");
        user.setVerificationTokenExpiresAt(LocalDateTime.now().plusHours(1));
        when(userRepository.findByVerificationToken("tok-user")).thenReturn(Optional.of(user));

        when(userRepository.findByVerificationToken("tok-salah")).thenReturn(Optional.empty());

        assertEquals("http://localhost:5173/admin/signin?verified=true", service.verifyEmail("tok-admin"));
        assertEquals("http://localhost:5173/auth/signin?verified=true", service.verifyEmail("tok-user"));
        assertEquals("http://localhost:5173/auth/signin?verified=false&reason=invalid", service.verifyEmail("tok-salah"));
    }

    @Test
    void resetPasswordLinkPointsToAdminAreaForAdminAccounts() {
        User admin = AdminTestSupport.user("admin@example.com", UserTypeCode.ADMIN, true, PASSWORD);
        User user = AdminTestSupport.user("u@example.com", UserTypeCode.FREE, true, PASSWORD);
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(userRepository.findByEmail("u@example.com")).thenReturn(Optional.of(user));

        ForgotPasswordRequestDTO forAdmin = new ForgotPasswordRequestDTO();
        forAdmin.setEmail("admin@example.com");
        ForgotPasswordRequestDTO forUser = new ForgotPasswordRequestDTO();
        forUser.setEmail("u@example.com");
        service.forgotPassword(forAdmin);
        service.forgotPassword(forUser);

        ArgumentCaptor<String> adminLink = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(eq("admin@example.com"), adminLink.capture());
        assertTrue(adminLink.getValue().startsWith("http://localhost:5173/admin/reset-password?token="));

        ArgumentCaptor<String> userLink = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(eq("u@example.com"), userLink.capture());
        assertTrue(userLink.getValue().startsWith("http://localhost:5173/auth/reset-password?token="));
        verify(emailService, never()).sendPasswordResetEmail(eq("hantu@example.com"), anyString());
    }
}
