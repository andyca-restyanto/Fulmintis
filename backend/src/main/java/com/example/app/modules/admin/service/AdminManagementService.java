// filepath: /backend/src/main/java/com/example/app/modules/admin/service/AdminManagementService.java
package com.example.app.modules.admin.service;

import com.example.app.modules.admin.dto.AdminInviteRequestDTO;
import com.example.app.modules.admin.dto.AdminInviteResponseDTO;

public interface AdminManagementService {

    /**
     * Admin yang sedang login mengundang admin baru lewat email. Email yang sudah
     * terdaftar (user biasa atau admin aktif) -> 409. Undangan yang belum diterima
     * dikirim ulang dengan token baru (token lama otomatis tidak berlaku).
     */
    AdminInviteResponseDTO invite(String inviterEmail, AdminInviteRequestDTO request);
}
