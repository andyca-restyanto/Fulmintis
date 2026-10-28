// backend/src/main/java/com/example/app/modules/usertype/service/UserTypeLookupService.java
package com.example.app.modules.usertype.service;

import java.util.Collection;
import java.util.Map;

/**
 * Utility untuk pola "in-memory join": module lain (misal dashboard) fetch
 * Data A dari database mereka sendiri (users dari database "frontline"),
 * lalu panggil method di sini untuk fetch Data B (dari tabel user_type di
 * database "master_data") dan gabungkan sendiri di kode Java -- karena
 * PostgreSQL tidak bisa JOIN lintas database.
 */
public interface UserTypeLookupService {

    /**
     * Fetch label untuk banyak code sekaligus DALAM SATU QUERY (hindari N+1),
     * dikembalikan sebagai Map<code, label>. Code yang tidak ketemu di
     * user_type TIDAK ADA di map hasil -- caller wajib handle fallback sendiri.
     */
    Map<String, String> getLabelsByCodes(Collection<String> codes);
}
