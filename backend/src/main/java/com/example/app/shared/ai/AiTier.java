// filepath: /backend/src/main/java/com/example/app/shared/ai/AiTier.java
package com.example.app.shared.ai;

import com.example.app.modules.usertype.UserTypeCode;

/**
 * Tier konfigurasi AI. Mengikuti tipe user (users.user_type): FREE -> FREE; VIP_MONTHLY -> VIP_MONTHLY;
 * VIP_YEARLY -> VIP_YEARLY (batasnya bisa dibedakan lewat menu admin "Batas AI"). Tipe yang tidak
 * dikenal diperlakukan sebagai FREE (arah paling aman terhadap biaya).
 *
 * ADMIN juga jatuh ke FREE secara EKSPLISIT: admin bukan pengguna fitur AI dan
 * sudah ditolak 403 oleh SecurityConfig (/api/** non-admin hanya ROLE_USER); ini
 * hanya lapis kedua agar admin tidak pernah mendapat kuota VIP.
 */
public enum AiTier {
    FREE,
    VIP_MONTHLY,
    VIP_YEARLY,
    /**
     * LEGACY: nilai "VIP" yang tertulis di baris generate lama (sebelum VIP dipecah). Tidak lagi dihasilkan
     * {@link #fromUserType}; dibaca sebagai VIP_MONTHLY agar pekerjaan lama tetap bisa diproses.
     */
    VIP;

    public static AiTier fromUserType(String userType) {
        if (UserTypeCode.ADMIN.equals(userType)) {
            return FREE;
        }
        if (UserTypeCode.VIP_MONTHLY.equals(userType)) {
            return VIP_MONTHLY;
        }
        if (UserTypeCode.VIP_YEARLY.equals(userType)) {
            return VIP_YEARLY;
        }
        return FREE;
    }

    /** Pengelompokan untuk API ke user ("FREE" / "VIP"): kontrak DTO usage tidak berubah. */
    public String group() {
        return this == FREE ? "FREE" : "VIP";
    }

    /** Tier yang dipakai mencari batas: VIP lama -> VIP_MONTHLY. */
    public AiTier canonical() {
        return this == VIP ? VIP_MONTHLY : this;
    }
}
