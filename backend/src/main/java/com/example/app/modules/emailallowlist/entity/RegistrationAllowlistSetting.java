// filepath: /backend/src/main/java/com/example/app/modules/emailallowlist/entity/RegistrationAllowlistSetting.java
package com.example.app.modules.emailallowlist.entity;

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

/** Satu baris (id = 1): apakah pembatasan email register sedang aktif. */
@Entity
@Table(name = "registration_allowlist_setting")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegistrationAllowlistSetting {

    public static final int SINGLETON_ID = 1;

    @Id
    private Integer id;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
