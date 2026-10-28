// filepath: /backend/src/main/java/com/example/app/modules/admin/service/AiLimitService.java
package com.example.app.modules.admin.service;

import com.example.app.modules.admin.dto.AiLimitsDTO;
import com.example.app.modules.admin.dto.AiLimitsUpdateRequestDTO;

import java.util.List;

/** Menu admin "Batas AI": lihat, ubah, dan reset batas generate test case / automation tanpa deploy. */
public interface AiLimitService {

    AiLimitsDTO get();

    /** Menyimpan nilai yang BERBEDA dari nilai efektif sekarang; seluruh permintaan ditolak bila ada satu nilai tidak valid. */
    AiLimitsDTO update(String actorEmail, AiLimitsUpdateRequestDTO request);

    /** Hapus override (kembali ke bawaan). keys null/kosong = semua; kunci tak dikenal -> InvalidAiLimitException. */
    AiLimitsDTO reset(String actorEmail, List<String> keys);
}
