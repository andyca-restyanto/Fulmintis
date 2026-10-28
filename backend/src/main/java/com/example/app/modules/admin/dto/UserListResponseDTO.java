// filepath: /backend/src/main/java/com/example/app/modules/admin/dto/UserListResponseDTO.java
package com.example.app.modules.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Satu halaman daftar user. {@code page} (mulai 0) dan {@code size} adalah nilai yang benar-benar
 * dipakai setelah dipaksa masuk batas; total mengikuti filter. Match: UserListPage (FE).
 */
@Getter
@Builder
@AllArgsConstructor
public class UserListResponseDTO {
    private List<UserListItemResponseDTO> items;
    private int page;
    private int size;
    private long totalItems;
    private int totalPages;
}
