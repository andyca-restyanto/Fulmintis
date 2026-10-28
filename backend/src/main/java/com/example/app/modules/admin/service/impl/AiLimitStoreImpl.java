// filepath: /backend/src/main/java/com/example/app/modules/admin/service/impl/AiLimitStoreImpl.java
package com.example.app.modules.admin.service.impl;

import com.example.app.modules.admin.entity.AiLimitSetting;
import com.example.app.modules.admin.repository.AiLimitSettingRepository;
import com.example.app.shared.ai.AiLimitStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/** Membaca override batas AI dari tabel ai_limit_setting untuk AiLimitProvider (shared/ai tidak tahu soal JPA). */
@Component
@RequiredArgsConstructor
public class AiLimitStoreImpl implements AiLimitStore {

    private final AiLimitSettingRepository repository;

    @Override
    @Transactional(readOnly = true)
    public Map<String, Integer> overrides() {
        Map<String, Integer> map = new HashMap<>();
        for (AiLimitSetting row : repository.findAll()) {
            map.put(row.getKey(), row.getValue());
        }
        return map;
    }
}
