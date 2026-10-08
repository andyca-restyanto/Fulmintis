// backend/src/main/java/com/example/app/modules/usertype/entity/UserType.java
package com.example.app.modules.usertype.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Tabel dedicated untuk user type (Free, VIP Monthly, VIP Yearly), ada di
 * database TERPISAH "master_data" (bukan "frontline" tempat tabel users
 * berada) -- lihat UserTypeDataSourceConfig.java.
 *
 * Sengaja dedicated (bukan tabel generic multi-kategori) sesuai kebutuhan:
 * kalau nanti ada master data lain, buat tabel/entity dedicated baru lagi
 * di module masing-masing, tetap di database "master_data" yang sama.
 */
@Entity
@Table(name = "user_type")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String label;

    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;
}
