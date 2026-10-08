// filepath: /backend/src/main/java/com/example/app/modules/admin/service/UserManagementService.java
package com.example.app.modules.admin.service;

import com.example.app.modules.admin.dto.UserListItemResponseDTO;
import com.example.app.modules.admin.dto.UserListResponseDTO;

import java.util.UUID;

/** Menu User di dashboard admin: daftar (cari + filter tier, berpaginasi) dan ubah tier. */
public interface UserManagementService {

    /**
     * Satu halaman daftar user NON-admin, terbaru dulu. {@code page} (mulai 0) dan {@code size} di luar batas
     * dipaksa masuk batas (page &lt; 0 -&gt; 0; size &lt; 1 -&gt; 10; size &gt; 50 -&gt; 50), bukan error.
     *
     * @param query kata kunci email/nama (trim; kosong = tanpa pencarian; lebih dari 100 karakter -&gt; 400)
     * @param tier  FREE | VIP_MONTHLY | VIP_YEARLY (kosong = semua tier; nilai lain termasuk ADMIN -&gt; 400)
     */
    UserListResponseDTO listUsers(int page, int size, String query, String tier);

    /**
     * Mengubah tier user. Tier harus FREE / VIP_MONTHLY / VIP_YEARLY (400). Id yang tidak ada atau akun ADMIN
     * -&gt; 404. Idempoten: tier yang sama dikembalikan apa adanya tanpa menyimpan/mencatat log.
     */
    UserListItemResponseDTO updateTier(String actorEmail, UUID userId, String newTier);
}
