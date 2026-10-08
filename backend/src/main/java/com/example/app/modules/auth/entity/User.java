// backend/src/main/java/com/example/app/modules/auth/entity/User.java
package com.example.app.modules.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    // Nama tampilan custom (requirement #4) -- OPSIONAL/nullable, karena
    // user yang sudah terdaftar sebelum fitur ini ada belum tentu sudah
    // set nama. Dipakai antara lain saat search "tambah team member by
    // name/email" (lihat ProjectCollaboratorServiceImpl).
    @Column
    private String name;

    @Column(nullable = false)
    private String password;

    // ---- Email verification flag ----
    @Column(name = "is_verified", nullable = false)
    @Builder.Default
    private boolean verified = false;

    @Column(name = "verification_token")
    private String verificationToken;

    @Column(name = "verification_token_expires_at")
    private LocalDateTime verificationTokenExpiresAt;

    // ---- Forgot/reset password ----
    @Column(name = "reset_password_token")
    private String resetPasswordToken;

    @Column(name = "reset_password_token_expires_at")
    private LocalDateTime resetPasswordTokenExpiresAt;

    // ---- User type ----
    // Menyimpan KODE dari master_data (contoh: "FREE", "VIP_MONTHLY").
    // BUKAN relasi JPA (@ManyToOne) karena master_data ada di DATABASE
    // TERPISAH ("master_data" db, beda dari "frontline" db tempat tabel ini
    // berada) -- PostgreSQL tidak mendukung foreign key lintas database.
    // Validasi kode ini valid dilakukan di application layer
    // (AuthServiceImpl), bukan di level database.
    @Column(name = "user_type", nullable = false)
    private String userType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
