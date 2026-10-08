// backend/src/main/java/com/example/app/modules/usertype/seed/UserTypeSeeder.java
package com.example.app.modules.usertype.seed;

import com.example.app.modules.usertype.UserTypeCode;
import com.example.app.modules.usertype.entity.UserType;
import com.example.app.modules.usertype.repository.UserTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Isi data awal tabel user_type saat aplikasi start. Flyway dimatikan dan
 * tidak ada data.sql, jadi seeding dilakukan lewat kode di sini. Dicek
 * satu-satu (bukan cuma cek count==0) supaya idempotent.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserTypeSeeder implements CommandLineRunner {

    private final UserTypeRepository userTypeRepository;

    @Override
    public void run(String... args) {
        seed(UserTypeCode.FREE, "Free", 1);
        seed(UserTypeCode.VIP_MONTHLY, "VIP Monthly", 2);
        seed(UserTypeCode.VIP_YEARLY, "VIP Yearly", 3);
    }

    private void seed(String code, String label, int sortOrder) {
        if (userTypeRepository.existsByCode(code)) {
            return;
        }

        UserType userType = UserType.builder()
                .code(code)
                .label(label)
                .sortOrder(sortOrder)
                .build();

        userTypeRepository.save(userType);
        log.info("Seed user_type: code={}, label={}", code, label);
    }
}
