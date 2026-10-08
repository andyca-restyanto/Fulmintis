// filepath: /backend/src/main/java/com/example/app/modules/admin/bootstrap/AdminBootstrapWarning.java
package com.example.app.modules.admin.bootstrap;

import com.example.app.modules.auth.repository.UserRepository;
import com.example.app.modules.usertype.UserTypeCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Peringatan saat start: di profil prod, belum ada admin, dan kode bootstrap kosong
 * berarti siapa pun yang membuka /admin/signup lebih dulu menjadi admin pertama.
 * Hanya mencatat log -- tidak pernah menggagalkan startup.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrapWarning implements CommandLineRunner {

    private final UserRepository userRepository;
    private final Environment environment;

    @Value("${app.admin.bootstrap-code:}")
    private String bootstrapCode;

    @Override
    public void run(String... args) {
        try {
            if (!environment.acceptsProfiles(Profiles.of("prod"))) {
                return;
            }
            boolean noAdminYet = userRepository.countByUserType(UserTypeCode.ADMIN) == 0;
            boolean noCode = bootstrapCode == null || bootstrapCode.isBlank();
            if (noAdminYet && noCode) {
                log.warn("[ADMIN-BOOTSTRAP] Belum ada admin dan app.admin.bootstrap-code kosong: "
                        + "siapa pun yang membuka /admin/signup lebih dulu menjadi admin pertama. "
                        + "Isi ADMIN_BOOTSTRAP_CODE sampai admin pertama dibuat.");
            }
        } catch (RuntimeException e) {
            log.debug("Pemeriksaan bootstrap admin dilewati: {}", e.getMessage());
        }
    }
}
