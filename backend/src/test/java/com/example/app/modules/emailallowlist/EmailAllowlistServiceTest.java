// filepath: /backend/src/test/java/com/example/app/modules/emailallowlist/EmailAllowlistServiceTest.java
package com.example.app.modules.emailallowlist;

import com.example.app.modules.automation.AutomationTestSupport.MutableClock;
import com.example.app.modules.emailallowlist.dto.AddAllowedEmailRequestDTO;
import com.example.app.modules.emailallowlist.dto.AllowedEmailRuleDTO;
import com.example.app.modules.emailallowlist.dto.AllowlistResponseDTO;
import com.example.app.modules.emailallowlist.entity.AllowedEmailRule;
import com.example.app.modules.emailallowlist.entity.AllowedEmailRuleType;
import com.example.app.modules.emailallowlist.entity.RegistrationAllowlistSetting;
import com.example.app.modules.emailallowlist.exception.AllowlistRuleNotFoundException;
import com.example.app.modules.emailallowlist.exception.DuplicateAllowlistRuleException;
import com.example.app.modules.emailallowlist.exception.EmailNotAllowedException;
import com.example.app.modules.emailallowlist.exception.InvalidAllowlistRuleException;
import com.example.app.modules.emailallowlist.repository.AllowedEmailRuleRepository;
import com.example.app.modules.emailallowlist.repository.RegistrationAllowlistSettingRepository;
import com.example.app.modules.emailallowlist.service.impl.EmailAllowlistServiceImpl;
import com.example.app.shared.activitylog.ActivityLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailAllowlistServiceTest {

    private final List<AllowedEmailRule> table = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong();
    private RegistrationAllowlistSetting setting;
    private MutableClock clock;
    private ActivityLogService activityLog;
    private EmailAllowlistServiceImpl service;

    @BeforeEach
    void setUp() {
        setting = RegistrationAllowlistSetting.builder().id(1).enabled(false).updatedBy("system")
                .updatedAt(java.time.LocalDateTime.now()).build();
        AllowedEmailRuleRepository rules = mock(AllowedEmailRuleRepository.class);
        when(rules.findAll()).thenAnswer(i -> new ArrayList<>(table));
        when(rules.findAllByOrderByIdAsc()).thenAnswer(i -> new ArrayList<>(table));
        when(rules.save(any(AllowedEmailRule.class))).thenAnswer(i -> {
            AllowedEmailRule r = i.getArgument(0);
            r.setId(ids.incrementAndGet());
            table.add(r);
            return r;
        });
        when(rules.findById(anyLong())).thenAnswer(i -> table.stream().filter(r -> r.getId().equals(i.<Long>getArgument(0))).findFirst());
        when(rules.existsByRuleTypeAndValue(any(), any())).thenAnswer(i -> table.stream().anyMatch(
                r -> r.getRuleType() == i.getArgument(0) && r.getValue().equals(i.<String>getArgument(1))));
        doAnswer(i -> {
            table.remove(i.<AllowedEmailRule>getArgument(0));
            return null;
        }).when(rules).delete(any(AllowedEmailRule.class));

        RegistrationAllowlistSettingRepository settings = mock(RegistrationAllowlistSettingRepository.class);
        when(settings.findById(1)).thenAnswer(i -> Optional.of(setting));
        when(settings.save(any(RegistrationAllowlistSetting.class))).thenAnswer(i -> {
            setting = i.getArgument(0);
            return setting;
        });

        clock = new MutableClock("2026-10-28T12:00:00Z");
        activityLog = mock(ActivityLogService.class);
        service = new EmailAllowlistServiceImpl(rules, settings, activityLog, clock);
    }

    private AllowedEmailRuleDTO add(AllowedEmailRuleType type, String value) {
        AddAllowedEmailRequestDTO r = new AddAllowedEmailRequestDTO();
        r.setRuleType(type);
        r.setValue(value);
        return service.add("admin@x.com", r);
    }

    @Test
    void featureOff_everyoneAllowed_evenWithRules() {
        add(AllowedEmailRuleType.EMAIL, "budi@perusahaan.com");
        assertDoesNotThrow(() -> service.assertAllowed("siapa.saja@gmail.com"));
    }

    @Test
    void featureOn_emptyList_failOpen() {
        service.setEnabled("admin@x.com", true);
        assertDoesNotThrow(() -> service.assertAllowed("siapa.saja@gmail.com"));
    }

    @Test
    void exactEmail_isCaseInsensitiveAndTrimmed() {
        add(AllowedEmailRuleType.EMAIL, "  Budi@Perusahaan.com ");
        service.setEnabled("admin@x.com", true);
        assertDoesNotThrow(() -> service.assertAllowed("BUDI@perusahaan.COM"));
        assertThrows(EmailNotAllowedException.class, () -> service.assertAllowed("budi2@perusahaan.com"));
    }

    @Test
    void domainRule_matchesExactDomainOnly() {
        add(AllowedEmailRuleType.DOMAIN, "@perusahaan.com");
        service.setEnabled("admin@x.com", true);
        assertDoesNotThrow(() -> service.assertAllowed("siapa@perusahaan.com"));
        assertThrows(EmailNotAllowedException.class, () -> service.assertAllowed("x@evilperusahaan.com"));
        assertThrows(EmailNotAllowedException.class, () -> service.assertAllowed("x@mail.perusahaan.com"));
        assertThrows(EmailNotAllowedException.class, () -> service.assertAllowed("x@perusahaan.com.evil.io"));
        assertThrows(EmailNotAllowedException.class, () -> service.assertAllowed("tanpa-at"));
    }

    @Test
    void domainRule_withoutAt_isStoredWithAt() {
        AllowedEmailRuleDTO dto = add(AllowedEmailRuleType.DOMAIN, "Perusahaan.com");
        assertEquals("@perusahaan.com", dto.getValue());
    }

    @Test
    void plusAlias_isNotAutoAllowed() {
        add(AllowedEmailRuleType.EMAIL, "budi@gmail.com");
        service.setEnabled("admin@x.com", true);
        assertThrows(EmailNotAllowedException.class, () -> service.assertAllowed("budi+1@gmail.com"));
    }

    @Test
    void invalidFormats_rejected() {
        assertThrows(InvalidAllowlistRuleException.class, () -> add(AllowedEmailRuleType.EMAIL, "bukan-email"));
        assertThrows(InvalidAllowlistRuleException.class, () -> add(AllowedEmailRuleType.EMAIL, "a@b"));
        assertThrows(InvalidAllowlistRuleException.class, () -> add(AllowedEmailRuleType.DOMAIN, "@"));
        assertThrows(InvalidAllowlistRuleException.class, () -> add(AllowedEmailRuleType.DOMAIN, "@x"));
        assertThrows(InvalidAllowlistRuleException.class, () -> add(AllowedEmailRuleType.DOMAIN, "a@b.com"));
        assertTrue(table.isEmpty());
    }

    @Test
    void duplicate_rejected_caseInsensitive() {
        add(AllowedEmailRuleType.DOMAIN, "@perusahaan.com");
        assertThrows(DuplicateAllowlistRuleException.class, () -> add(AllowedEmailRuleType.DOMAIN, "@PERUSAHAAN.com"));
    }

    @Test
    void remove_unknownId_notFound_andKnownId_removes() {
        assertThrows(AllowlistRuleNotFoundException.class, () -> service.remove("admin@x.com", 99L));
        AllowedEmailRuleDTO dto = add(AllowedEmailRuleType.EMAIL, "budi@perusahaan.com");
        service.remove("admin@x.com", dto.getId());
        assertTrue(table.isEmpty());
        verify(activityLog).log(eq("admin@x.com"), eq("REMOVE_ALLOWED_EMAIL EMAIL budi@perusahaan.com"));
    }

    @Test
    void changes_takeEffectImmediately_notAfterCacheTtl() {
        service.setEnabled("admin@x.com", true);
        assertDoesNotThrow(() -> service.assertAllowed("a@gmail.com")); // daftar kosong, cache terisi
        add(AllowedEmailRuleType.DOMAIN, "@perusahaan.com");
        assertThrows(EmailNotAllowedException.class, () -> service.assertAllowed("a@gmail.com"));
    }

    @Test
    void cache_isRefreshedAfterTtl_forChangesMadeElsewhere() {
        service.setEnabled("admin@x.com", true);
        add(AllowedEmailRuleType.DOMAIN, "@perusahaan.com");
        assertThrows(EmailNotAllowedException.class, () -> service.assertAllowed("a@gmail.com"));
        setting.setEnabled(false); // diubah langsung di DB (instance lain)
        assertThrows(EmailNotAllowedException.class, () -> service.assertAllowed("a@gmail.com")); // masih cache
        clock.advanceHours(1);
        assertDoesNotThrow(() -> service.assertAllowed("a@gmail.com"));
    }

    @Test
    void get_returnsEnabledAndRulesInOrder() {
        add(AllowedEmailRuleType.EMAIL, "b@x.com");
        add(AllowedEmailRuleType.DOMAIN, "@y.com");
        service.setEnabled("admin@x.com", true);
        AllowlistResponseDTO r = service.get();
        assertTrue(r.isEnabled());
        assertEquals(List.of("b@x.com", "@y.com"), r.getRules().stream().map(AllowedEmailRuleDTO::getValue).toList());
    }
}
