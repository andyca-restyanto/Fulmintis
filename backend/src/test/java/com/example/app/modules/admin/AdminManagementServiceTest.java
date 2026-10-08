// filepath: /backend/src/test/java/com/example/app/modules/admin/AdminManagementServiceTest.java
package com.example.app.modules.admin;

import com.example.app.modules.admin.dto.AdminInviteRequestDTO;
import com.example.app.modules.admin.dto.AdminInviteResponseDTO;
import com.example.app.modules.admin.service.impl.AdminManagementServiceImpl;
import com.example.app.modules.auth.entity.User;
import com.example.app.modules.auth.exception.EmailAlreadyExistsException;
import com.example.app.modules.auth.repository.UserRepository;
import com.example.app.modules.usertype.UserTypeCode;
import com.example.app.shared.activitylog.ActivityLogService;
import com.example.app.shared.email.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminManagementServiceTest {

    private UserRepository userRepository;
    private EmailService emailService;
    private ActivityLogService activityLogService;
    private AdminManagementServiceImpl service;

    @BeforeEach
    void setUp() {
        userRepository = AdminTestSupport.userRepository();
        emailService = mock(EmailService.class);
        activityLogService = mock(ActivityLogService.class);
        service = new AdminManagementServiceImpl(
                userRepository, AdminTestSupport.userTypeRepositoryWithAllTypes(), AdminTestSupport.ENCODER,
                emailService, activityLogService);
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "http://localhost:5173");
        ReflectionTestUtils.setField(service, "invitationExpiryMinutes", 1440L);
    }

    private AdminInviteRequestDTO request(String name, String email) {
        AdminInviteRequestDTO r = new AdminInviteRequestDTO();
        r.setName(name);
        r.setEmail(email);
        return r;
    }

    @Test
    void newEmailGetsAnUnusableAdminAccountAndAnInvitationEmail() {
        when(userRepository.findByEmail("sari@example.com")).thenReturn(Optional.empty());

        LocalDateTime before = LocalDateTime.now();
        AdminInviteResponseDTO response = service.invite("budi@example.com", request(" Sari Admin ", " Sari@Example.com "));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        User invitee = saved.getValue();
        assertEquals(UserTypeCode.ADMIN, invitee.getUserType());
        assertEquals("sari@example.com", invitee.getEmail());
        assertEquals("Sari Admin", invitee.getName());
        assertFalse(invitee.isVerified());
        assertNotNull(invitee.getAdminInvitationToken());
        assertTrue(invitee.getAdminInvitationExpiresAt().isAfter(before.plusHours(23)));
        // password acak: tidak bisa ditebak dari nama/email
        assertFalse(AdminTestSupport.ENCODER.matches("sari@example.com", invitee.getPassword()));

        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendAdminInvitationEmail(
                eq("sari@example.com"), eq("Sari Admin"), link.capture(), eq("budi@example.com"));
        assertEquals("http://localhost:5173/admin/accept-invitation?token=" + invitee.getAdminInvitationToken(), link.getValue());
        verify(activityLogService).log("budi@example.com", "ADMIN_INVITE_CREATED");
        assertEquals("sari@example.com", response.getEmail());
        assertEquals(invitee.getAdminInvitationExpiresAt(), response.getExpiresAt());
    }

    @Test
    void regularUserEmailIsRejectedAndNotPromoted() {
        User regular = AdminTestSupport.user("user@example.com", UserTypeCode.FREE, true, "Str0ng!Pass");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(regular));

        assertThrows(EmailAlreadyExistsException.class, () -> service.invite("budi@example.com", request("X", "user@example.com")));

        assertEquals(UserTypeCode.FREE, regular.getUserType());
        verify(userRepository, never()).save(any(User.class));
        verify(emailService, never()).sendAdminInvitationEmail(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void activeAdminEmailIsRejected() {
        User active = AdminTestSupport.user("admin2@example.com", UserTypeCode.ADMIN, true, "Str0ng!Pass");
        when(userRepository.findByEmail("admin2@example.com")).thenReturn(Optional.of(active));

        assertThrows(EmailAlreadyExistsException.class, () -> service.invite("budi@example.com", request("X", "admin2@example.com")));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void unverifiedBootstrapAdminWithoutInvitationIsNotOverwritten() {
        User bootstrap = AdminTestSupport.user("pertama@example.com", UserTypeCode.ADMIN, false, "Str0ng!Pass");
        when(userRepository.findByEmail("pertama@example.com")).thenReturn(Optional.of(bootstrap));

        assertThrows(EmailAlreadyExistsException.class, () -> service.invite("budi@example.com", request("X", "pertama@example.com")));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void pendingInvitationIsResentWithAFreshToken() {
        User pending = AdminTestSupport.user("sari@example.com", UserTypeCode.ADMIN, false, "acak");
        pending.setAdminInvitationToken("token-lama");
        pending.setAdminInvitationExpiresAt(LocalDateTime.now().minusHours(2)); // sudah kedaluwarsa
        when(userRepository.findByEmail("sari@example.com")).thenReturn(Optional.of(pending));

        service.invite("budi@example.com", request("Sari Baru", "sari@example.com"));

        assertNotEquals("token-lama", pending.getAdminInvitationToken());
        assertTrue(pending.getAdminInvitationExpiresAt().isAfter(LocalDateTime.now()));
        assertEquals("Sari Baru", pending.getName());
        verify(emailService).sendAdminInvitationEmail(eq("sari@example.com"), eq("Sari Baru"), anyString(), eq("budi@example.com"));
    }
}
