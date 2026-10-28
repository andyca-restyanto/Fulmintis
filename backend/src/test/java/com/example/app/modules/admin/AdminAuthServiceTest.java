// filepath: /backend/src/test/java/com/example/app/modules/admin/AdminAuthServiceTest.java
package com.example.app.modules.admin;

import com.example.app.modules.admin.dto.AdminAcceptInvitationRequestDTO;
import com.example.app.modules.admin.dto.AdminRegisterRequestDTO;
import com.example.app.modules.admin.dto.AdminRegisterResponseDTO;
import com.example.app.modules.admin.dto.AdminRegistrationStatusResponseDTO;
import com.example.app.modules.admin.exception.AccountDeactivatedException;
import com.example.app.modules.admin.exception.AdminRegistrationClosedException;
import com.example.app.modules.admin.exception.InvalidAdminBootstrapCodeException;
import com.example.app.modules.admin.exception.InvalidAdminInvitationException;
import com.example.app.modules.admin.service.impl.AdminAuthServiceImpl;
import com.example.app.modules.auth.dto.LoginRequestDTO;
import com.example.app.modules.auth.dto.LoginResponseDTO;
import com.example.app.modules.auth.entity.User;
import com.example.app.modules.auth.exception.AccountNotVerifiedException;
import com.example.app.modules.auth.exception.EmailAlreadyExistsException;
import com.example.app.modules.auth.exception.InvalidCredentialsException;
import com.example.app.modules.auth.repository.UserRepository;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminAuthServiceTest {

    private static final String PASSWORD = "Str0ng!Pass";

    private UserRepository userRepository;
    private EmailService emailService;
    private ActivityLogService activityLogService;
    private JwtService jwtService;
    private AdminAuthServiceImpl service;

    @BeforeEach
    void setUp() {
        userRepository = AdminTestSupport.userRepository();
        emailService = mock(EmailService.class);
        activityLogService = mock(ActivityLogService.class);
        jwtService = AdminTestSupport.jwtService();
        service = new AdminAuthServiceImpl(
                userRepository, AdminTestSupport.userTypeRepositoryWithAllTypes(), AdminTestSupport.ENCODER,
                emailService, jwtService, activityLogService);
        ReflectionTestUtils.setField(service, "backendBaseUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(service, "verificationTokenExpiryMinutes", 1440L);
        ReflectionTestUtils.setField(service, "bootstrapCode", "");
    }

    private AdminRegisterRequestDTO registerRequest(String email, String bootstrapCode) {
        AdminRegisterRequestDTO r = new AdminRegisterRequestDTO();
        r.setName("  Budi Admin ");
        r.setEmail(email);
        r.setPassword(PASSWORD);
        r.setConfirmPassword(PASSWORD);
        r.setBootstrapCode(bootstrapCode);
        return r;
    }

    private LoginRequestDTO loginRequest(String email, String password) {
        LoginRequestDTO r = new LoginRequestDTO();
        r.setEmail(email);
        r.setPassword(password);
        return r;
    }

    // ---------------- status ----------------

    @Test
    void statusIsOpenWhenNoAdminAndReportsBootstrapCodeRequirement() {
        when(userRepository.countByUserType(UserTypeCode.ADMIN)).thenReturn(0L);

        AdminRegistrationStatusResponseDTO free = service.getRegistrationStatus();
        assertTrue(free.isOpen());
        assertFalse(free.isBootstrapCodeRequired());

        ReflectionTestUtils.setField(service, "bootstrapCode", "rahasia");
        assertTrue(service.getRegistrationStatus().isBootstrapCodeRequired());
    }

    @Test
    void statusIsClosedOnceAnAdminExists() {
        when(userRepository.countByUserType(UserTypeCode.ADMIN)).thenReturn(1L);
        ReflectionTestUtils.setField(service, "bootstrapCode", "rahasia");

        AdminRegistrationStatusResponseDTO status = service.getRegistrationStatus();
        assertFalse(status.isOpen());
        assertFalse(status.isBootstrapCodeRequired()); // tidak relevan lagi saat ditutup
    }

    // ---------------- register (admin pertama) ----------------

    @Test
    void firstAdminCanRegisterAndIsStoredAsUnverifiedAdmin() {
        when(userRepository.countByUserType(UserTypeCode.ADMIN)).thenReturn(0L);
        when(userRepository.existsByEmail("budi@example.com")).thenReturn(false);

        AdminRegisterResponseDTO response = service.register(registerRequest("  Budi@Example.com ", null));

        // kunci advisory diambil SEBELUM cek-lalu-simpan
        verify(userRepository).lockAdvisory(anyLong());
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        User admin = saved.getValue();
        assertEquals(UserTypeCode.ADMIN, admin.getUserType());
        assertEquals("budi@example.com", admin.getEmail());
        assertEquals("Budi Admin", admin.getName());
        assertFalse(admin.isVerified());
        assertNotEquals(PASSWORD, admin.getPassword()); // tersimpan sebagai hash
        assertTrue(AdminTestSupport.ENCODER.matches(PASSWORD, admin.getPassword()));
        assertEquals("budi@example.com", response.getEmail());
        assertEquals("Budi Admin", response.getName());
        verify(emailService).sendVerificationEmail(eq("budi@example.com"), anyString());
        verify(activityLogService).log("budi@example.com", "ADMIN_REGISTER_BOOTSTRAP");
    }

    @Test
    void registrationIsRejectedOnceAnyAdminExists() {
        when(userRepository.countByUserType(UserTypeCode.ADMIN)).thenReturn(1L);

        assertThrows(AdminRegistrationClosedException.class,
                () -> service.register(registerRequest("lain@example.com", null)));

        verify(userRepository, never()).save(any(User.class));
        verify(emailService, never()).sendVerificationEmail(anyString(), anyString());
    }

    @Test
    void bootstrapCodeIsEnforcedWhenConfigured() {
        ReflectionTestUtils.setField(service, "bootstrapCode", "rahasia-123");
        when(userRepository.countByUserType(UserTypeCode.ADMIN)).thenReturn(0L);

        assertThrows(InvalidAdminBootstrapCodeException.class,
                () -> service.register(registerRequest("a@example.com", null)));
        assertThrows(InvalidAdminBootstrapCodeException.class,
                () -> service.register(registerRequest("a@example.com", "")));
        assertThrows(InvalidAdminBootstrapCodeException.class,
                () -> service.register(registerRequest("a@example.com", "rahasia-124")));
        verify(userRepository, never()).save(any(User.class));

        // kode benar -> lolos
        service.register(registerRequest("a@example.com", "rahasia-123"));
        verify(userRepository).save(any(User.class));
    }

    @Test
    void bootstrapCodeIsIgnoredWhenNotConfigured() {
        when(userRepository.countByUserType(UserTypeCode.ADMIN)).thenReturn(0L);

        service.register(registerRequest("a@example.com", "apa-saja"));

        verify(userRepository).save(any(User.class));
    }

    @Test
    void existingEmailIsRejectedAndNotPromotedToAdmin() {
        when(userRepository.countByUserType(UserTypeCode.ADMIN)).thenReturn(0L);
        when(userRepository.existsByEmail("user@example.com")).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class,
                () -> service.register(registerRequest("user@example.com", null)));

        verify(userRepository, never()).save(any(User.class));
    }

    // ---------------- login ----------------

    @Test
    void adminCanLoginAndTokenCarriesAdminRole() {
        User admin = AdminTestSupport.user("admin@example.com", UserTypeCode.ADMIN, true, PASSWORD);
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));

        LoginResponseDTO response = service.login(loginRequest("Admin@Example.com", PASSWORD));

        assertEquals("ADMIN", response.getRole());
        assertEquals("Bearer", response.getTokenType());
        JwtService.ParsedToken parsed = jwtService.parse(response.getAccessToken()).orElseThrow();
        assertEquals("ADMIN", parsed.role());
        assertEquals("admin@example.com", parsed.email());
        verify(activityLogService).log("admin@example.com", "ADMIN_LOGIN");
    }

    @Test
    void regularUserCannotLoginThroughAdminEndpointAndGetsTheSameErrorAsWrongPassword() {
        User regular = AdminTestSupport.user("user@example.com", UserTypeCode.FREE, true, PASSWORD);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(regular));
        when(userRepository.findByEmail("tidak-ada@example.com")).thenReturn(Optional.empty());

        InvalidCredentialsException notAdmin = assertThrows(InvalidCredentialsException.class,
                () -> service.login(loginRequest("user@example.com", PASSWORD)));
        InvalidCredentialsException wrongPassword = assertThrows(InvalidCredentialsException.class,
                () -> service.login(loginRequest("user@example.com", "Salah!Pass1")));
        InvalidCredentialsException unknown = assertThrows(InvalidCredentialsException.class,
                () -> service.login(loginRequest("tidak-ada@example.com", PASSWORD)));

        assertEquals(wrongPassword.getMessage(), notAdmin.getMessage());
        assertEquals(wrongPassword.getMessage(), unknown.getMessage());
    }

    @Test
    void unverifiedAdminCannotLogin() {
        User admin = AdminTestSupport.user("admin@example.com", UserTypeCode.ADMIN, false, PASSWORD);
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));

        assertThrows(AccountNotVerifiedException.class,
                () -> service.login(loginRequest("admin@example.com", PASSWORD)));
    }

    @Test
    void deactivatedAdminWithCorrectPasswordGetsTheDedicatedErrorNotTheVerificationOne() {
        User admin = AdminTestSupport.user("admin@example.com", UserTypeCode.ADMIN, true, PASSWORD);
        admin.setActive(false);
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));

        assertThrows(AccountDeactivatedException.class,
                () -> service.login(loginRequest("admin@example.com", PASSWORD)));

        // Admin nonaktif yang juga belum terverifikasi: tetap "nonaktif" (FE tidak boleh menawarkan verifikasi).
        admin.setVerified(false);
        assertThrows(AccountDeactivatedException.class,
                () -> service.login(loginRequest("admin@example.com", PASSWORD)));
        verify(activityLogService, never()).log(anyString(), eq("ADMIN_LOGIN"));
    }

    @Test
    void deactivatedAdminWithWrongPasswordLearnsNothing() {
        User admin = AdminTestSupport.user("admin@example.com", UserTypeCode.ADMIN, true, PASSWORD);
        admin.setActive(false);
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));

        assertThrows(InvalidCredentialsException.class,
                () -> service.login(loginRequest("admin@example.com", "Salah!Pass1")));
    }

    @Test
    void reactivatedAdminCanLoginAgain() {
        User admin = AdminTestSupport.user("admin@example.com", UserTypeCode.ADMIN, true, PASSWORD);
        admin.setActive(false);
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        assertThrows(AccountDeactivatedException.class,
                () -> service.login(loginRequest("admin@example.com", PASSWORD)));

        admin.setActive(true);

        assertEquals("ADMIN", service.login(loginRequest("admin@example.com", PASSWORD)).getRole());
    }

    // ---------------- undangan ----------------

    private User pendingInvitee(String token, LocalDateTime expiresAt) {
        User invitee = AdminTestSupport.user("sari@example.com", UserTypeCode.ADMIN, false, "acak-tak-dikenal");
        invitee.setAdminInvitationToken(token);
        invitee.setAdminInvitationExpiresAt(expiresAt);
        return invitee;
    }

    private AdminAcceptInvitationRequestDTO acceptRequest(String token) {
        AdminAcceptInvitationRequestDTO r = new AdminAcceptInvitationRequestDTO();
        r.setToken(token);
        r.setNewPassword(PASSWORD);
        r.setConfirmPassword(PASSWORD);
        return r;
    }

    @Test
    void validInvitationIsReportedWithEmailAndName() {
        User invitee = pendingInvitee("tok-1", LocalDateTime.now().plusHours(1));
        when(userRepository.findByAdminInvitationToken("tok-1")).thenReturn(Optional.of(invitee));

        var result = service.validateInvitation("tok-1");

        assertTrue(result.isValid());
        assertEquals("sari@example.com", result.getEmail());
    }

    @Test
    void unknownBlankExpiredOrAlreadyUsedInvitationsAreRejected() {
        when(userRepository.findByAdminInvitationToken("tak-ada")).thenReturn(Optional.empty());
        User expired = pendingInvitee("tok-exp", LocalDateTime.now().minusMinutes(1));
        when(userRepository.findByAdminInvitationToken("tok-exp")).thenReturn(Optional.of(expired));
        User active = pendingInvitee("tok-aktif", LocalDateTime.now().plusHours(1));
        active.setVerified(true); // sudah diterima / akun aktif
        when(userRepository.findByAdminInvitationToken("tok-aktif")).thenReturn(Optional.of(active));
        User notAdmin = pendingInvitee("tok-bukan-admin", LocalDateTime.now().plusHours(1));
        notAdmin.setUserType(UserTypeCode.FREE);
        when(userRepository.findByAdminInvitationToken("tok-bukan-admin")).thenReturn(Optional.of(notAdmin));

        for (String token : new String[]{null, "", "  ", "tak-ada", "tok-exp", "tok-aktif", "tok-bukan-admin"}) {
            assertThrows(InvalidAdminInvitationException.class, () -> service.validateInvitation(token), "token=" + token);
        }
    }

    @Test
    void acceptingInvitationSetsPasswordVerifiesAndConsumesToken() {
        User invitee = pendingInvitee("tok-1", LocalDateTime.now().plusHours(1));
        when(userRepository.findByAdminInvitationToken("tok-1")).thenReturn(Optional.of(invitee));

        service.acceptInvitation(acceptRequest("tok-1"));

        assertTrue(invitee.isVerified());
        assertNull(invitee.getAdminInvitationToken());
        assertNull(invitee.getAdminInvitationExpiresAt());
        assertTrue(AdminTestSupport.ENCODER.matches(PASSWORD, invitee.getPassword()));
        verify(activityLogService).log("sari@example.com", "ADMIN_INVITE_ACCEPTED");
    }

    @Test
    void invitationTokenCannotBeUsedTwice() {
        User invitee = pendingInvitee("tok-1", LocalDateTime.now().plusHours(1));
        when(userRepository.findByAdminInvitationToken("tok-1")).thenReturn(Optional.of(invitee));

        service.acceptInvitation(acceptRequest("tok-1"));

        // setelah dipakai token sudah null di DB -> pencarian berikutnya kosong
        when(userRepository.findByAdminInvitationToken("tok-1")).thenReturn(Optional.empty());
        assertThrows(InvalidAdminInvitationException.class, () -> service.acceptInvitation(acceptRequest("tok-1")));
    }

    @Test
    void invitedAdminCanLoginAfterAccepting() {
        User invitee = pendingInvitee("tok-1", LocalDateTime.now().plusHours(1));
        when(userRepository.findByAdminInvitationToken("tok-1")).thenReturn(Optional.of(invitee));
        when(userRepository.findByEmail("sari@example.com")).thenReturn(Optional.of(invitee));

        assertThrows(InvalidCredentialsException.class,
                () -> service.login(loginRequest("sari@example.com", PASSWORD))); // belum punya password

        service.acceptInvitation(acceptRequest("tok-1"));

        assertEquals("ADMIN", service.login(loginRequest("sari@example.com", PASSWORD)).getRole());
    }

    @Test
    void deactivatedInviteeCannotValidateOrAcceptTheInvitationUntilReactivated() {
        User invitee = pendingInvitee("tok-1", LocalDateTime.now().plusHours(1));
        invitee.setActive(false);
        when(userRepository.findByAdminInvitationToken("tok-1")).thenReturn(Optional.of(invitee));

        assertThrows(InvalidAdminInvitationException.class, () -> service.validateInvitation("tok-1"));
        assertThrows(InvalidAdminInvitationException.class, () -> service.acceptInvitation(acceptRequest("tok-1")));
        assertFalse(invitee.isVerified());
        assertEquals("tok-1", invitee.getAdminInvitationToken());

        invitee.setActive(true); // diaktifkan kembali: undangan yang sama berlaku lagi
        assertTrue(service.validateInvitation("tok-1").isValid());
    }

    private static long anyLong() {
        return org.mockito.ArgumentMatchers.anyLong();
    }
}
