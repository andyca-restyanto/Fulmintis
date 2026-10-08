// backend/src/main/java/com/example/app/modules/dashboard/service/impl/DashboardServiceImpl.java
package com.example.app.modules.dashboard.service.impl;

import com.example.app.modules.auth.entity.User;
import com.example.app.modules.auth.repository.UserRepository;
import com.example.app.modules.dashboard.dto.DashboardSummaryDTO;
import com.example.app.modules.dashboard.service.DashboardService;
import com.example.app.modules.usertype.service.UserTypeLookupService;
import com.example.app.shared.activitylog.ActivityLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Contoh konkret pola "2 datasource terpisah -> fetch masing-masing ->
 * in-memory join di kode Java", karena PostgreSQL tidak bisa JOIN lintas
 * database (database "frontline" vs "master_data").
 */
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final UserRepository userRepository;             // -> datasource "frontline"
    private final UserTypeLookupService userTypeLookupService; // -> datasource "master_data" (tabel user_type)
    private final ActivityLogService activityLogService;

    @Override
    public DashboardSummaryDTO getSummary(String email) {
        // ---- STEP 1: Fetch Data A dari Database 1 (frontline) ----
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException(
                        "User dengan email " + email + " tidak ditemukan (token valid tapi user sudah terhapus?)"
                ));

        // ---- STEP 2: Fetch Data B dari Database 2 (master_data, tabel user_type) ----
        // Di sini cuma 1 code (user ini sendiri), tapi method-nya bulk-ready
        // (bisa terima banyak code sekaligus) -- kalau nanti dipakai untuk
        // list banyak user (misal endpoint admin), tetap cuma 1 query ke
        // user_type untuk SEMUA baris, bukan query berulang per baris (N+1).
        Map<String, String> userTypeLabels = userTypeLookupService.getLabelsByCodes(
                List.of(user.getUserType())
        );

        // ---- STEP 3: In-memory join (HashMap lookup) ----
        // Fallback ke code mentah kalau label tidak ketemu -- ini penting
        // justru KARENA tidak ada FK constraint lintas database yang
        // menjamin data konsisten; kalau baris di user_type kehapus tapi
        // user masih nyimpen code lama, aplikasi tidak boleh crash, cukup
        // tampilkan code-nya apa adanya.
        String userTypeLabel = userTypeLabels.getOrDefault(user.getUserType(), user.getUserType());

        activityLogService.log(user.getEmail(), "VIEW_DASHBOARD");

        return DashboardSummaryDTO.builder()
                .email(user.getEmail())
                .name(user.getName())
                .message("Selamat datang di dashboard!")
                .userType(user.getUserType())
                .userTypeLabel(userTypeLabel)
                .build();
    }
}
