// filepath: /backend/src/main/java/com/example/app/modules/admin/dto/AdminListItemResponseDTO.java
package com.example.app.modules.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/** Satu baris di daftar admin. Match: AdminListItem (FE). */
@Getter
@Builder
@AllArgsConstructor
public class AdminListItemResponseDTO {
    private UUID id;
    private String name;
    private String email;
    private AdminStatus status;
    /** true bila baris ini adalah admin yang sedang login (FE menyembunyikan tombol nonaktifkan). */
    private boolean self;
    private LocalDateTime createdAt;
    /** Hanya terisi untuk PENDING yang menunggu undangan; null untuk status lain. */
    private LocalDateTime invitationExpiresAt;
}
