// filepath: /backend/src/main/java/com/example/app/modules/admin/entity/AiLimitSetting.java
package com.example.app.modules.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Satu override batas AI. Ada baris = nilai database dipakai; tidak ada baris = nilai bawaan dari konfigurasi (app.ai.*).
 * Kunci: "FREE.AUTOMATION_DAILY_LIMIT" / "GLOBAL.GLOBAL_DAILY_LIMIT" (lihat AiLimitKey).
 */
@Entity
@Table(name = "ai_limit_setting")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiLimitSetting {

    @Id
    @Column(name = "setting_key", length = 80, nullable = false)
    private String key;

    @Column(name = "setting_value", nullable = false)
    private int value;

    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
