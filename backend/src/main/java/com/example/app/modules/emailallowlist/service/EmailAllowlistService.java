// filepath: /backend/src/main/java/com/example/app/modules/emailallowlist/service/EmailAllowlistService.java
package com.example.app.modules.emailallowlist.service;

import com.example.app.modules.emailallowlist.dto.AddAllowedEmailRequestDTO;
import com.example.app.modules.emailallowlist.dto.AllowedEmailRuleDTO;
import com.example.app.modules.emailallowlist.dto.AllowlistResponseDTO;

public interface EmailAllowlistService {

    /**
     * Dipanggil saat register. Melempar EmailNotAllowedException bila pembatasan AKTIF, daftar TIDAK kosong,
     * dan email tidak cocok aturan mana pun. Fitur mati / daftar kosong = semua boleh (fail-open).
     */
    void assertAllowed(String email);

    AllowlistResponseDTO get();

    AllowedEmailRuleDTO add(String actorEmail, AddAllowedEmailRequestDTO request);

    void remove(String actorEmail, Long id);

    AllowlistResponseDTO setEnabled(String actorEmail, boolean enabled);
}
