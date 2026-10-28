// filepath: /backend/src/test/java/com/example/app/modules/admin/AdminListAndDeactivateTest.java
package com.example.app.modules.admin;

import com.example.app.modules.admin.dto.AdminInviteRequestDTO;
import com.example.app.modules.admin.dto.AdminListItemResponseDTO;
import com.example.app.modules.admin.dto.AdminListResponseDTO;
import com.example.app.modules.admin.dto.AdminStatus;
import com.example.app.modules.admin.exception.AdminNotFoundException;
import com.example.app.modules.admin.exception.CannotDeactivateSelfException;
import com.example.app.modules.admin.exception.LastActiveAdminException;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Menu Admin: daftar berpaginasi, nonaktifkan, aktifkan kembali (AdminManagementServiceImpl). */
class AdminListAndDeactivateTest {

    private static final String ACTOR = "budi@example.com";
    private static final String PASSWORD = "Str0ng!Pass";

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

    // ---------------------------------------------------------------- pembantu

    private User admin(String email, boolean verified, boolean active) {
        User u = AdminTestSupport.user(email, UserTypeCode.ADMIN, verified, PASSWORD);
        u.setActive(active);
        u.setCreatedAt(LocalDateTime.of(2026, 10, 8, 9, 0));
        return u;
    }

    private User pendingAdmin(String email, LocalDateTime invitationExpiresAt) {
        User u = admin(email, false, true);
        u.setAdminInvitationToken("tok-" + email);
        u.setAdminInvitationExpiresAt(invitationExpiresAt);
        return u;
    }

    private void stubFind(User... users) {
        for (User u : users) {
            when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));
        }
    }

    private void stubActiveAdminCount(long count) {
        when(userRepository.countByUserTypeAndActiveTrueAndVerifiedTrue(UserTypeCode.ADMIN)).thenReturn(count);
    }

    private Pageable listAndCapturePageable(int page, int size) {
        when(userRepository.findAllByUserType(eq(UserTypeCode.ADMIN), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<User>(List.of(), inv.getArgument(1), 0));
        service.listAdmins(ACTOR, page, size);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository, org.mockito.Mockito.atLeastOnce())
                .findAllByUserType(eq(UserTypeCode.ADMIN), captor.capture());
        return captor.getValue();
    }

    // ---------------------------------------------------------------- daftar

    @Test
    void listIsNewestFirstWithStableTieBreaker() {
        Pageable pageable = listAndCapturePageable(0, 10);

        Sort.Order created = pageable.getSort().getOrderFor("createdAt");
        Sort.Order id = pageable.getSort().getOrderFor("id");
        assertEquals(Sort.Direction.DESC, created.getDirection());
        assertEquals(Sort.Direction.DESC, id.getDirection());
        assertEquals(2, pageable.getSort().stream().count());
    }

    @Test
    void pageAndSizeAreClampedNotRejected() {
        int[][] cases = {
                // masukan page,size -> dipakai page,size
                {-5, 0, 0, 10},
                {0, -1, 0, 10},
                {2, 500, 2, 50},
                {1, 25, 1, 25},
                {0, 50, 0, 50},
                {0, 51, 0, 50},
                {3, 1, 3, 1},
        };
        for (int[] c : cases) {
            userRepository = AdminTestSupport.userRepository();
            service = new AdminManagementServiceImpl(
                    userRepository, AdminTestSupport.userTypeRepositoryWithAllTypes(), AdminTestSupport.ENCODER,
                    emailService, activityLogService);
            Pageable pageable = listAndCapturePageable(c[0], c[1]);
            assertEquals(c[2], pageable.getPageNumber(), "page untuk masukan " + c[0] + "," + c[1]);
            assertEquals(c[3], pageable.getPageSize(), "size untuk masukan " + c[0] + "," + c[1]);
        }
    }

    @Test
    void responseEchoesTheEffectivePageAndSize() {
        when(userRepository.findAllByUserType(eq(UserTypeCode.ADMIN), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<User>(List.of(), inv.getArgument(1), 0));

        AdminListResponseDTO response = service.listAdmins(ACTOR, -3, 999);

        assertEquals(0, response.getPage());
        assertEquals(50, response.getSize());
    }

    @Test
    void statusesSelfFlagAndInvitationExpiryAreDerivedPerRow() {
        LocalDateTime expires = LocalDateTime.of(2026, 10, 9, 11, 0);
        User me = admin("Budi@Example.com", true, true);            // self: huruf besar/kecil diabaikan
        User invited = pendingAdmin("sari@example.com", expires);   // menunggu undangan
        User unverifiedFirst = admin("pertama@example.com", false, true); // admin pertama belum klik verifikasi
        User deactivated = admin("dewi@example.com", true, false);
        User deactivatedPending = pendingAdmin("rina@example.com", expires);
        deactivatedPending.setActive(false);
        List<User> rows = List.of(me, invited, unverifiedFirst, deactivated, deactivatedPending);
        when(userRepository.findAllByUserType(eq(UserTypeCode.ADMIN), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(rows, inv.getArgument(1), rows.size()));

        List<AdminListItemResponseDTO> items = service.listAdmins(ACTOR, 0, 10).getItems();

        assertEquals(AdminStatus.ACTIVE, items.get(0).getStatus());
        assertTrue(items.get(0).isSelf());
        assertNull(items.get(0).getInvitationExpiresAt());

        assertEquals(AdminStatus.PENDING, items.get(1).getStatus());
        assertFalse(items.get(1).isSelf());
        assertEquals(expires, items.get(1).getInvitationExpiresAt());

        assertEquals(AdminStatus.PENDING, items.get(2).getStatus());
        assertNull(items.get(2).getInvitationExpiresAt()); // bukan undangan -> tanpa masa berlaku

        assertEquals(AdminStatus.INACTIVE, items.get(3).getStatus());
        assertEquals(AdminStatus.INACTIVE, items.get(4).getStatus());
        assertNull(items.get(4).getInvitationExpiresAt());  // nonaktif mengalahkan pending
        for (AdminListItemResponseDTO item : items.subList(1, 5)) {
            assertFalse(item.isSelf());
        }
    }

    @Test
    void totalsPassThroughForEmptyFullAndOutOfRangePages() {
        long[][] totals = {{0, 0}, {1, 1}, {10, 1}, {11, 2}, {23, 3}};
        for (long[] t : totals) {
            when(userRepository.findAllByUserType(eq(UserTypeCode.ADMIN), any(Pageable.class)))
                    .thenAnswer(inv -> new PageImpl<User>(List.of(), inv.getArgument(1), t[0]));
            AdminListResponseDTO r = service.listAdmins(ACTOR, 0, 10);
            assertEquals(t[0], r.getTotalItems(), "totalItems untuk " + t[0]);
            assertEquals((int) t[1], r.getTotalPages(), "totalPages untuk " + t[0]);
        }

        // Halaman jauh di luar jangkauan: items kosong, total tetap benar, tanpa error.
        when(userRepository.findAllByUserType(eq(UserTypeCode.ADMIN), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<User>(List.of(), inv.getArgument(1), 23));
        AdminListResponseDTO far = service.listAdmins(ACTOR, 9, 10);
        assertTrue(far.getItems().isEmpty());
        assertEquals(23, far.getTotalItems());
        assertEquals(3, far.getTotalPages());
        assertEquals(9, far.getPage());
    }

    @Test
    void selfFlagIsComputedOnEveryPage() {
        User me = admin(ACTOR, true, true);
        Page<User> secondPage = new PageImpl<>(List.of(me), PageRequest.of(1, 10), 11);
        when(userRepository.findAllByUserType(eq(UserTypeCode.ADMIN), any(Pageable.class))).thenReturn(secondPage);

        assertTrue(service.listAdmins(ACTOR, 1, 10).getItems().get(0).isSelf());
        assertFalse(service.listAdmins("lain@example.com", 1, 10).getItems().get(0).isSelf());
    }

    // ---------------------------------------------------------------- nonaktifkan

    @Test
    void deactivateTakesTheLockBlocksAccessClearsResetTokenAndLogsActorAndTarget() {
        User target = admin("sari@example.com", true, true);
        target.setResetPasswordToken("reset-tok");
        target.setResetPasswordTokenExpiresAt(LocalDateTime.now().plusHours(1));
        stubFind(target);
        stubActiveAdminCount(2);

        AdminListItemResponseDTO result = service.deactivate(ACTOR, target.getId());

        verify(userRepository).lockAdvisory(anyLong());
        assertFalse(target.isActive());
        assertNull(target.getResetPasswordToken());
        assertNull(target.getResetPasswordTokenExpiresAt());
        verify(userRepository).save(target);
        verify(activityLogService).log(ACTOR, "ADMIN_DEACTIVATED:sari@example.com");
        assertEquals(AdminStatus.INACTIVE, result.getStatus());
        assertFalse(result.isSelf());
    }

    @Test
    void adminCannotDeactivateSelfEvenIfAnotherAdminExists() {
        User me = admin("BUDI@example.com", true, true);
        stubFind(me);
        stubActiveAdminCount(5);

        assertThrows(CannotDeactivateSelfException.class, () -> service.deactivate(ACTOR, me.getId()));

        assertTrue(me.isActive());
        verify(userRepository, never()).save(any(User.class));
        verify(activityLogService, never()).log(anyString(), anyString());
    }

    @Test
    void lastActiveAdminIsProtected() {
        User onlyOther = admin("sari@example.com", true, true);
        stubFind(onlyOther);
        stubActiveAdminCount(1); // mis. pemanggil baru saja dinonaktifkan request lain

        assertThrows(LastActiveAdminException.class, () -> service.deactivate(ACTOR, onlyOther.getId()));

        assertTrue(onlyOther.isActive());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void deactivatingAPendingAdminIsAllowedEvenWhenOnlyOneActiveAdminRemains() {
        // Admin yang belum terverifikasi tidak dihitung sebagai "admin aktif", jadi membatalkan
        // undangannya tidak boleh terhalang aturan admin aktif terakhir.
        User pending = pendingAdmin("sari@example.com", LocalDateTime.now().plusHours(5));
        stubFind(pending);
        stubActiveAdminCount(1);

        AdminListItemResponseDTO result = service.deactivate(ACTOR, pending.getId());

        assertFalse(pending.isActive());
        assertEquals(AdminStatus.INACTIVE, result.getStatus());
        assertEquals("tok-sari@example.com", pending.getAdminInvitationToken()); // diblokir, bukan dihapus
    }

    @Test
    void unknownIdAndNonAdminAccountsAreBothNotFound() {
        UUID unknown = UUID.randomUUID();
        when(userRepository.findById(unknown)).thenReturn(Optional.empty());
        User regular = AdminTestSupport.user("u@example.com", UserTypeCode.FREE, true, PASSWORD);
        stubFind(regular);

        assertThrows(AdminNotFoundException.class, () -> service.deactivate(ACTOR, unknown));
        assertThrows(AdminNotFoundException.class, () -> service.deactivate(ACTOR, regular.getId()));
        assertThrows(AdminNotFoundException.class, () -> service.activate(ACTOR, regular.getId()));

        assertTrue(regular.isActive());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void deactivatingAnAlreadyInactiveAdminIsIdempotent() {
        User target = admin("sari@example.com", true, false);
        stubFind(target);
        stubActiveAdminCount(1);

        AdminListItemResponseDTO result = service.deactivate(ACTOR, target.getId());

        assertEquals(AdminStatus.INACTIVE, result.getStatus());
        verify(userRepository, never()).save(any(User.class));
        verify(activityLogService, never()).log(anyString(), anyString());
    }

    @Test
    void activityTextIsTruncatedToTheColumnLength() {
        String longEmail = "a".repeat(300) + "@example.com";
        User target = admin(longEmail, true, true);
        stubFind(target);
        stubActiveAdminCount(2);

        service.deactivate(ACTOR, target.getId());

        ArgumentCaptor<String> activity = ArgumentCaptor.forClass(String.class);
        verify(activityLogService).log(eq(ACTOR), activity.capture());
        assertEquals(255, activity.getValue().length());
        assertTrue(activity.getValue().startsWith("ADMIN_DEACTIVATED:"));
    }

    // ---------------------------------------------------------------- aktifkan kembali

    @Test
    void activateRestoresAccessAndLogsIt() {
        User target = admin("sari@example.com", true, false);
        stubFind(target);

        AdminListItemResponseDTO result = service.activate(ACTOR, target.getId());

        assertTrue(target.isActive());
        verify(userRepository).save(target);
        verify(activityLogService).log(ACTOR, "ADMIN_REACTIVATED:sari@example.com");
        assertEquals(AdminStatus.ACTIVE, result.getStatus());
    }

    @Test
    void activatingAnAlreadyActiveAdminIsIdempotent() {
        User target = admin("sari@example.com", true, true);
        stubFind(target);

        assertEquals(AdminStatus.ACTIVE, service.activate(ACTOR, target.getId()).getStatus());

        verify(userRepository, never()).save(any(User.class));
        verify(activityLogService, never()).log(anyString(), anyString());
    }

    @Test
    void reactivatedPendingAdminKeepsItsInvitationAndCanBeReinvited() {
        LocalDateTime expires = LocalDateTime.now().minusHours(1); // sudah kedaluwarsa
        User pending = pendingAdmin("sari@example.com", expires);
        stubFind(pending);
        stubActiveAdminCount(3);
        when(userRepository.findByEmail("sari@example.com")).thenReturn(Optional.of(pending));

        service.deactivate(ACTOR, pending.getId());
        AdminListItemResponseDTO afterActivate = service.activate(ACTOR, pending.getId());

        assertEquals(AdminStatus.PENDING, afterActivate.getStatus());
        assertEquals(expires, afterActivate.getInvitationExpiresAt()); // FE menampilkan "kedaluwarsa"

        String oldToken = pending.getAdminInvitationToken();
        AdminInviteRequestDTO resend = new AdminInviteRequestDTO();
        resend.setName("Sari Admin");
        resend.setEmail("sari@example.com");
        service.invite(ACTOR, resend); // kirim ulang undangan memakai endpoint yang sama

        assertNotEquals(oldToken, pending.getAdminInvitationToken());
        assertTrue(pending.getAdminInvitationExpiresAt().isAfter(LocalDateTime.now()));
        verify(emailService).sendAdminInvitationEmail(eq("sari@example.com"), eq("Sari Admin"), anyString(), eq(ACTOR));
    }

    @Test
    void inviteIsRejectedForADeactivatedAdminEvenIfItHasAnInvitationToken() {
        User pending = pendingAdmin("sari@example.com", LocalDateTime.now().plusHours(5));
        pending.setActive(false);
        when(userRepository.findByEmail("sari@example.com")).thenReturn(Optional.of(pending));

        AdminInviteRequestDTO request = new AdminInviteRequestDTO();
        request.setName("Sari");
        request.setEmail("sari@example.com");

        assertThrows(EmailAlreadyExistsException.class, () -> service.invite(ACTOR, request));
        verify(emailService, never()).sendAdminInvitationEmail(anyString(), anyString(), anyString(), anyString());
    }
}
