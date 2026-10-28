// filepath: /backend/src/main/java/com/example/app/modules/emailallowlist/service/impl/EmailAllowlistServiceImpl.java
package com.example.app.modules.emailallowlist.service.impl;

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
import com.example.app.modules.emailallowlist.service.EmailAllowlistService;
import com.example.app.shared.activitylog.ActivityLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class EmailAllowlistServiceImpl implements EmailAllowlistService {

    static final long CACHE_TTL_MILLIS = 10_000L;

    private static final Pattern EMAIL = Pattern.compile("^[a-z0-9._%+\\-]+@[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$");
    private static final Pattern DOMAIN = Pattern.compile("^@[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$");

    private final AllowedEmailRuleRepository ruleRepository;
    private final RegistrationAllowlistSettingRepository settingRepository;
    private final ActivityLogService activityLogService;
    private final Clock clock;

    private record Snapshot(boolean enabled, Set<String> emails, Set<String> domains, long loadedAtMillis) {
        boolean restricting() {
            return enabled && !(emails.isEmpty() && domains.isEmpty());
        }
    }

    private volatile Snapshot cache;

    // ------------------------------------------------------------------ register

    @Override
    public void assertAllowed(String email) {
        Snapshot s = snapshot();
        if (!s.restricting()) {
            return;
        }
        String normalized = normalize(email);
        int at = normalized.lastIndexOf('@');
        // Cocokkan domain PERSIS setelah '@' terakhir (bukan endsWith) supaya "x@evilperusahaan.com" tidak lolos.
        String domain = at >= 0 ? normalized.substring(at) : "";
        if (s.emails().contains(normalized) || (!domain.isEmpty() && s.domains().contains(domain))) {
            return;
        }
        throw new EmailNotAllowedException();
    }

    // ------------------------------------------------------------------ admin

    @Override
    @Transactional(readOnly = true)
    public AllowlistResponseDTO get() {
        return response();
    }

    @Override
    @Transactional
    public AllowedEmailRuleDTO add(String actorEmail, AddAllowedEmailRequestDTO request) {
        AllowedEmailRuleType type = request.getRuleType();
        String value = normalize(request.getValue());
        if (type == AllowedEmailRuleType.EMAIL) {
            if (!EMAIL.matcher(value).matches()) {
                throw new InvalidAllowlistRuleException("Format email tidak valid (contoh: budi@perusahaan.com)");
            }
        } else {
            if (!value.startsWith("@") && !value.contains("@")) {
                value = "@" + value; // "perusahaan.com" diterima, disimpan sebagai "@perusahaan.com"
            }
            if (!DOMAIN.matcher(value).matches()) {
                throw new InvalidAllowlistRuleException("Format domain tidak valid (contoh: @perusahaan.com)");
            }
        }
        if (ruleRepository.existsByRuleTypeAndValue(type, value)) {
            throw new DuplicateAllowlistRuleException(value);
        }

        AllowedEmailRule saved = ruleRepository.save(AllowedEmailRule.builder()
                .ruleType(type)
                .value(value)
                .createdAt(LocalDateTime.now(clock))
                .createdBy(actorEmail)
                .build());
        activityLogService.log(actorEmail, "ADD_ALLOWED_EMAIL " + type + " " + value);
        invalidate();
        return toDto(saved);
    }

    @Override
    @Transactional
    public void remove(String actorEmail, Long id) {
        AllowedEmailRule rule = ruleRepository.findById(id).orElseThrow(() -> new AllowlistRuleNotFoundException(id));
        ruleRepository.delete(rule);
        activityLogService.log(actorEmail, "REMOVE_ALLOWED_EMAIL " + rule.getRuleType() + " " + rule.getValue());
        invalidate();
    }

    @Override
    @Transactional
    public AllowlistResponseDTO setEnabled(String actorEmail, boolean enabled) {
        RegistrationAllowlistSetting setting = settingRepository.findById(RegistrationAllowlistSetting.SINGLETON_ID)
                .orElseGet(() -> RegistrationAllowlistSetting.builder().id(RegistrationAllowlistSetting.SINGLETON_ID).build());
        setting.setEnabled(enabled);
        setting.setUpdatedBy(actorEmail);
        setting.setUpdatedAt(LocalDateTime.now(clock));
        settingRepository.save(setting);
        activityLogService.log(actorEmail, enabled ? "ENABLE_EMAIL_ALLOWLIST" : "DISABLE_EMAIL_ALLOWLIST");
        invalidate();
        return response();
    }

    // ------------------------------------------------------------------ internal

    private AllowlistResponseDTO response() {
        invalidate(); // halaman admin selalu menampilkan keadaan terbaru
        boolean enabled = settingRepository.findById(RegistrationAllowlistSetting.SINGLETON_ID)
                .map(RegistrationAllowlistSetting::isEnabled).orElse(false);
        List<AllowedEmailRuleDTO> rules = ruleRepository.findAllByOrderByIdAsc().stream().map(this::toDto).toList();
        return AllowlistResponseDTO.builder().enabled(enabled).rules(rules).build();
    }

    private Snapshot snapshot() {
        Snapshot s = cache;
        long now = clock.millis();
        if (s != null && now - s.loadedAtMillis() < CACHE_TTL_MILLIS) {
            return s;
        }
        boolean enabled = settingRepository.findById(RegistrationAllowlistSetting.SINGLETON_ID)
                .map(RegistrationAllowlistSetting::isEnabled).orElse(false);
        Set<String> emails = new HashSet<>();
        Set<String> domains = new HashSet<>();
        if (enabled) {
            for (AllowedEmailRule r : ruleRepository.findAll()) {
                (r.getRuleType() == AllowedEmailRuleType.EMAIL ? emails : domains).add(r.getValue());
            }
        }
        s = new Snapshot(enabled, emails, domains, now);
        cache = s;
        return s;
    }

    private void invalidate() {
        cache = null;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private AllowedEmailRuleDTO toDto(AllowedEmailRule r) {
        return AllowedEmailRuleDTO.builder()
                .id(r.getId()).ruleType(r.getRuleType()).value(r.getValue())
                .createdAt(r.getCreatedAt()).createdBy(r.getCreatedBy())
                .build();
    }
}
