// filepath: /backend/src/test/java/com/example/app/modules/admin/UserManagementServiceTest.java
package com.example.app.modules.admin;

import com.example.app.modules.admin.dto.UserListItemResponseDTO;
import com.example.app.modules.admin.dto.UserListResponseDTO;
import com.example.app.modules.admin.exception.InvalidUserQueryException;
import com.example.app.modules.admin.exception.InvalidUserTierException;
import com.example.app.modules.admin.exception.UserNotFoundException;
import com.example.app.modules.admin.service.impl.UserManagementServiceImpl;
import com.example.app.modules.auth.entity.User;
import com.example.app.modules.auth.repository.UserRepository;
import com.example.app.modules.usertype.UserTypeCode;
import com.example.app.modules.usertype.entity.UserType;
import com.example.app.modules.usertype.repository.UserTypeRepository;
import com.example.app.modules.usertype.service.UserTypeLookupService;
import com.example.app.shared.activitylog.ActivityLogService;
import com.example.app.shared.ai.AiTier;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Menu User: daftar (cari + filter tier, berpaginasi) dan ubah tier (UserManagementServiceImpl). */
class UserManagementServiceTest {

    private static final String ACTOR = "admin@example.com";
    private static final String PASSWORD = "Str0ng!Pass";

    private UserRepository userRepository;
    private UserTypeRepository userTypeRepository;
    private UserTypeLookupService lookupService;
    private ActivityLogService activityLogService;
    private UserManagementServiceImpl service;

    @BeforeEach
    void setUp() {
        userRepository = AdminTestSupport.userRepository();
        userTypeRepository = mock(UserTypeRepository.class);
        for (String[] t : new String[][]{
                {UserTypeCode.FREE, "Free"}, {UserTypeCode.VIP_MONTHLY, "VIP Monthly"},
                {UserTypeCode.VIP_YEARLY, "VIP Yearly"}, {UserTypeCode.ADMIN, "Admin"}}) {
            when(userTypeRepository.findByCode(t[0])).thenReturn(Optional.of(UserType.builder().code(t[0]).label(t[1]).build()));
        }
        lookupService = mock(UserTypeLookupService.class);
        when(lookupService.getLabelsByCodes(anyCollection())).thenReturn(
                Map.of(UserTypeCode.FREE, "Free", UserTypeCode.VIP_MONTHLY, "VIP Monthly", UserTypeCode.VIP_YEARLY, "VIP Yearly"));
        activityLogService = mock(ActivityLogService.class);
        service = new UserManagementServiceImpl(userRepository, userTypeRepository, lookupService, activityLogService);
    }

    // ---------------------------------------------------------------- pembantu

    private User user(String email, String tier) {
        User u = AdminTestSupport.user(email, tier, true, PASSWORD);
        u.setCreatedAt(LocalDateTime.of(2026, 10, 8, 9, 0));
        return u;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubPage(List<User> users, long total) {
        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<User>(users, inv.getArgument(1), total));
    }

    private void stubFind(User... users) {
        for (User u : users) {
            when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));
        }
    }

    private record Captured(Specification<User> spec, Pageable pageable) {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Captured capture() {
        ArgumentCaptor<Specification> specCaptor = ArgumentCaptor.forClass(Specification.class);
        ArgumentCaptor<Pageable> pageCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAll(specCaptor.capture(), pageCaptor.capture());
        return new Captured(specCaptor.getValue(), pageCaptor.getValue());
    }

    /** Menjalankan spesifikasi terhadap CriteriaBuilder tiruan untuk melihat syarat apa saja yang dirangkai. */
    @SuppressWarnings("unchecked")
    private CriteriaBuilder run(Specification<User> spec) {
        CriteriaBuilder cb = Mockito.mock(CriteriaBuilder.class, Mockito.RETURNS_MOCKS);
        Root<User> root = Mockito.mock(Root.class, Mockito.RETURNS_MOCKS);
        CriteriaQuery<?> query = Mockito.mock(CriteriaQuery.class, Mockito.RETURNS_MOCKS);
        spec.toPredicate(root, query, cb);
        return cb;
    }

    private Captured list(int page, int size, String q, String tier) {
        stubPage(List.of(), 0);
        service.listUsers(page, size, q, tier);
        return capture();
    }

    // ---------------------------------------------------------------- daftar: paginasi & urutan

    @Test
    void listIsNewestFirstWithStableTieBreaker() {
        Sort sort = list(0, 10, null, null).pageable().getSort();

        assertEquals(Sort.Direction.DESC, sort.getOrderFor("createdAt").getDirection());
        assertEquals(Sort.Direction.DESC, sort.getOrderFor("id").getDirection());
        assertEquals("createdAt", sort.iterator().next().getProperty());
    }

    @Test
    void pageAndSizeAreForcedIntoBounds() {
        assertEquals(0, list(-3, 10, null, null).pageable().getPageNumber());
    }

    @Test
    void sizeBelowOneFallsBackToTenAndAboveFiftyIsCapped() {
        assertEquals(10, list(0, 0, null, null).pageable().getPageSize());
    }

    @Test
    void oversizedSizeIsCappedAtFifty() {
        assertEquals(50, list(0, 500, null, null).pageable().getPageSize());
    }

    @Test
    void responseEchoesEffectivePageSizeAndTotalsFromTheRepository() {
        stubPage(List.of(user("a@x.com", UserTypeCode.FREE)), 23);

        UserListResponseDTO result = service.listUsers(1, 10, null, null);

        assertEquals(1, result.getPage());
        assertEquals(10, result.getSize());
        assertEquals(23, result.getTotalItems());
        assertEquals(3, result.getTotalPages());
        assertEquals(1, result.getItems().size());
    }

    @Test
    void pageBeyondTheEndGivesEmptyItemsButCorrectTotals() {
        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<User>(List.of(), PageRequest.of(9, 10), 23));

        UserListResponseDTO result = service.listUsers(9, 10, null, null);

        assertTrue(result.getItems().isEmpty());
        assertEquals(23, result.getTotalItems());
        assertEquals(3, result.getTotalPages());
        verify(lookupService, never()).getLabelsByCodes(anyCollection()); // halaman kosong: tanpa query label
    }

    // ---------------------------------------------------------------- daftar: isi baris

    @Test
    void rowCarriesTierLabelStatusAndAllFields() {
        User budi = user("budi@example.com", UserTypeCode.VIP_MONTHLY);
        budi.setActive(false);
        budi.setVerified(false);
        stubPage(List.of(budi), 1);

        UserListItemResponseDTO row = service.listUsers(0, 10, null, null).getItems().get(0);

        assertEquals(budi.getId(), row.getId());
        assertEquals("budi@example.com", row.getEmail());
        assertEquals(budi.getName(), row.getName());
        assertEquals("VIP_MONTHLY", row.getUserType());
        assertEquals("VIP Monthly", row.getUserTypeLabel());
        assertFalse(row.isActive());
        assertFalse(row.isVerified());
        assertEquals(LocalDateTime.of(2026, 10, 8, 9, 0), row.getCreatedAt());
    }

    @Test
    void labelsForTheWholePageAreFetchedInOneQueryWithDistinctCodes() {
        stubPage(List.of(user("a@x.com", UserTypeCode.FREE), user("b@x.com", UserTypeCode.FREE),
                user("c@x.com", UserTypeCode.VIP_YEARLY)), 3);

        service.listUsers(0, 10, null, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<String>> codes = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(lookupService, times(1)).getLabelsByCodes(codes.capture());
        assertEquals(List.of(UserTypeCode.FREE, UserTypeCode.VIP_YEARLY), List.copyOf(codes.getValue()));
    }

    @Test
    void unknownLabelFallsBackToTheCode() {
        when(lookupService.getLabelsByCodes(anyCollection())).thenReturn(Map.of());
        stubPage(List.of(user("a@x.com", UserTypeCode.FREE)), 1);

        assertEquals("FREE", service.listUsers(0, 10, null, null).getItems().get(0).getUserTypeLabel());
    }

    // ---------------------------------------------------------------- daftar: filter

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void withoutFiltersOnlyTheNotAdminConditionIsApplied() {
        CriteriaBuilder cb = run(list(0, 10, null, null).spec());

        verify(cb).notEqual(any(Expression.class), eq("ADMIN"));
        verify(cb, never()).equal(any(Expression.class), anyString());
        verify(cb, never()).like(any(Expression.class), anyString(), Mockito.anyChar());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void blankQueryAndBlankTierMeanNoFilter() {
        CriteriaBuilder cb = run(list(0, 10, "   ", "  ").spec());

        verify(cb, never()).equal(any(Expression.class), anyString());
        verify(cb, never()).like(any(Expression.class), anyString(), Mockito.anyChar());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void tierFilterAddsAnExactTierCondition() {
        CriteriaBuilder cb = run(list(0, 10, null, "FREE").spec());

        verify(cb).notEqual(any(Expression.class), eq("ADMIN"));
        verify(cb).equal(any(Expression.class), eq("FREE"));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void queryIsTrimmedLowercasedAndEscapedBeforeMatching() {
        CriteriaBuilder cb = run(list(0, 10, "  Budi%  ", null).spec());

        verify(cb, times(2)).like(any(Expression.class), eq("%budi\\%%"), eq('\\'));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void queryAndTierAreCombined() {
        CriteriaBuilder cb = run(list(0, 10, "budi", "VIP_YEARLY").spec());

        verify(cb).notEqual(any(Expression.class), eq("ADMIN"));
        verify(cb).equal(any(Expression.class), eq("VIP_YEARLY"));
        verify(cb, times(2)).like(any(Expression.class), eq("%budi%"), eq('\\'));
    }

    @Test
    void tierAdminOrUnknownOrWrongCaseIsRejectedNotIgnored() {
        for (String bad : new String[]{"ADMIN", "ngawur", "free", "VIP"}) {
            assertThrows(InvalidUserTierException.class, () -> service.listUsers(0, 10, null, bad), bad);
        }
        verify(userRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void queryOfExactlyOneHundredCharsIsAcceptedAndOneMoreIsRejected() {
        stubPage(List.of(), 0);
        service.listUsers(0, 10, "a".repeat(100), null);
        assertThrows(InvalidUserQueryException.class, () -> service.listUsers(0, 10, "a".repeat(101), null));
    }

    @Test
    void lengthIsCheckedAfterTrimming() {
        stubPage(List.of(), 0);
        service.listUsers(0, 10, "  " + "a".repeat(100) + "  ", null); // tidak melempar
    }

    // ---------------------------------------------------------------- ubah tier

    @Test
    void changesTierSavesAndLogsWhoChangedWhatFromWhatTo() {
        User budi = user("budi@example.com", UserTypeCode.FREE);
        stubFind(budi);

        UserListItemResponseDTO result = service.updateTier(ACTOR, budi.getId(), UserTypeCode.VIP_YEARLY);

        assertEquals(UserTypeCode.VIP_YEARLY, budi.getUserType());
        assertEquals("VIP_YEARLY", result.getUserType());
        assertEquals("VIP Yearly", result.getUserTypeLabel());
        verify(userRepository).save(budi);
        verify(activityLogService).log(ACTOR, "USER_TIER_CHANGED:budi@example.com:FREE->VIP_YEARLY");
    }

    @Test
    void newTierTakesEffectForAiQuotaImmediately() {
        User budi = user("budi@example.com", UserTypeCode.FREE);
        stubFind(budi);
        assertEquals(AiTier.FREE, AiTier.fromUserType(budi.getUserType()));

        service.updateTier(ACTOR, budi.getId(), UserTypeCode.VIP_MONTHLY);

        assertEquals(AiTier.VIP, AiTier.fromUserType(budi.getUserType()));
    }

    @Test
    void canDowngradeToFree() {
        User budi = user("budi@example.com", UserTypeCode.VIP_YEARLY);
        stubFind(budi);

        service.updateTier(ACTOR, budi.getId(), UserTypeCode.FREE);

        assertEquals(UserTypeCode.FREE, budi.getUserType());
    }

    @Test
    void sameTierIsIdempotentNoSaveNoLog() {
        User budi = user("budi@example.com", UserTypeCode.VIP_MONTHLY);
        stubFind(budi);

        UserListItemResponseDTO result = service.updateTier(ACTOR, budi.getId(), UserTypeCode.VIP_MONTHLY);

        assertEquals("VIP_MONTHLY", result.getUserType());
        assertEquals("VIP Monthly", result.getUserTypeLabel());
        verify(userRepository, never()).save(any(User.class));
        verify(activityLogService, never()).log(anyString(), anyString());
    }

    @Test
    void tierValueIsTrimmed() {
        User budi = user("budi@example.com", UserTypeCode.FREE);
        stubFind(budi);

        service.updateTier(ACTOR, budi.getId(), "  VIP_MONTHLY ");

        assertEquals(UserTypeCode.VIP_MONTHLY, budi.getUserType());
    }

    @Test
    void invalidTiersAreRejectedBeforeTouchingAnything() {
        User budi = user("budi@example.com", UserTypeCode.FREE);
        stubFind(budi);

        for (String bad : new String[]{"ADMIN", "ngawur", "vip_monthly", "", "  ", null}) {
            assertThrows(InvalidUserTierException.class, () -> service.updateTier(ACTOR, budi.getId(), bad), String.valueOf(bad));
        }
        assertEquals(UserTypeCode.FREE, budi.getUserType());
        verify(userRepository, never()).save(any(User.class));
        verify(userRepository, never()).findById(any(UUID.class));
    }

    @Test
    void adminAccountCannotBeChangedAndLooksLikeNotFound() {
        User admin = user("sari@example.com", UserTypeCode.ADMIN);
        stubFind(admin);

        assertThrows(UserNotFoundException.class, () -> service.updateTier(ACTOR, admin.getId(), UserTypeCode.FREE));
        assertEquals(UserTypeCode.ADMIN, admin.getUserType());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void unknownIdIsNotFound() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> service.updateTier(ACTOR, id, UserTypeCode.FREE));
    }

    @Test
    void nonVerifiedAndDeactivatedUsersCanStillHaveTheirTierChanged() {
        User budi = user("budi@example.com", UserTypeCode.FREE);
        budi.setVerified(false);
        budi.setActive(false);
        stubFind(budi);

        UserListItemResponseDTO result = service.updateTier(ACTOR, budi.getId(), UserTypeCode.VIP_MONTHLY);

        assertEquals("VIP_MONTHLY", result.getUserType());
        assertFalse(result.isActive());
        assertFalse(result.isVerified());
    }

    @Test
    void missingTierRowInMasterDataIsAnExplicitConfigurationError() {
        User budi = user("budi@example.com", UserTypeCode.FREE);
        stubFind(budi);
        when(userTypeRepository.findByCode(UserTypeCode.VIP_MONTHLY)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> service.updateTier(ACTOR, budi.getId(), UserTypeCode.VIP_MONTHLY));
        assertEquals(UserTypeCode.FREE, budi.getUserType());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void logTextIsTruncatedToTheColumnLimit() {
        User budi = user("a".repeat(300) + "@x.com", UserTypeCode.FREE);
        stubFind(budi);

        service.updateTier(ACTOR, budi.getId(), UserTypeCode.VIP_MONTHLY);

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(activityLogService).log(eq(ACTOR), text.capture());
        assertEquals(255, text.getValue().length());
    }
}
