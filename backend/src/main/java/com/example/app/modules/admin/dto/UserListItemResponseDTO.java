// filepath: /backend/src/main/java/com/example/app/modules/admin/dto/UserListItemResponseDTO.java
package com.example.app.modules.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/** Satu baris di daftar user (menu User admin). Match: UserListItem (FE). */
@Getter
@Builder
@AllArgsConstructor
public class UserListItemResponseDTO {
    private UUID id;
    private String name;
    private String email;
    /** Kode tier: FREE | VIP_MONTHLY | VIP_YEARLY. */
    private String userType;
    /** Label dari master_data (mis. "VIP Monthly"); jatuh ke kode bila label tidak ditemukan. */
    private String userTypeLabel;
    private boolean verified;
    private boolean active;
    private LocalDateTime createdAt;
}
