// filepath: /backend/src/main/java/com/example/app/modules/admin/service/AdminManagementService.java
package com.example.app.modules.admin.service;

import com.example.app.modules.admin.dto.AdminInviteRequestDTO;
import com.example.app.modules.admin.dto.AdminInviteResponseDTO;
import com.example.app.modules.admin.dto.AdminListItemResponseDTO;
import com.example.app.modules.admin.dto.AdminListResponseDTO;

import java.util.UUID;

public interface AdminManagementService {

    /**
     * Admin yang sedang login mengundang admin baru lewat email. Email yang sudah
     * terdaftar (user biasa atau admin aktif) -> 409. Undangan yang belum diterima
     * dikirim ulang dengan token baru (token lama otomatis tidak berlaku).
     */
    AdminInviteResponseDTO invite(String inviterEmail, AdminInviteRequestDTO request);

    /**
     * Satu halaman daftar admin, terbaru dulu. {@code page} (mulai 0) dan {@code size} yang di luar batas
     * dipaksa masuk batas (page &lt; 0 -&gt; 0; size &lt; 1 -&gt; 10; size &gt; 50 -&gt; 50), bukan error.
     * Halaman di luar jangkauan = {@code items} kosong dengan total yang tetap benar.
     */
    AdminListResponseDTO listAdmins(String currentAdminEmail, int page, int size);

    /**
     * Menonaktifkan admin: tidak bisa login, token yang sudah terbit langsung ditolak, token reset
     * password dihapus. Ditolak untuk diri sendiri (400), admin aktif terakhir (409), dan id yang
     * bukan admin (404). Idempoten: yang sudah nonaktif dikembalikan apa adanya.
     */
    AdminListItemResponseDTO deactivate(String actorEmail, UUID adminId);

    /** Mengaktifkan kembali admin yang dinonaktifkan. Idempoten; id yang bukan admin -> 404. */
    AdminListItemResponseDTO activate(String actorEmail, UUID adminId);
}
