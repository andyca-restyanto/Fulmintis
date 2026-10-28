// backend/src/main/java/com/example/app/modules/usertype/UserTypeCode.java
package com.example.app.modules.usertype;

/** Kode-kode di tabel user_type. */
public final class UserTypeCode {
    private UserTypeCode() {
    }

    public static final String FREE = "FREE";
    public static final String VIP_MONTHLY = "VIP_MONTHLY";
    public static final String VIP_YEARLY = "VIP_YEARLY";

    /**
     * Akun administrator. BUKAN tier berlangganan: tidak boleh muncul di dropdown
     * upgrade akun (lihat UserTypeController) dan tidak boleh dibuat lewat
     * /api/auth/register (selalu FREE).
     */
    public static final String ADMIN = "ADMIN";
}
