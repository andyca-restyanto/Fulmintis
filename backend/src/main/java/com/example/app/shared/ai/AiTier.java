// filepath: /backend/src/main/java/com/example/app/shared/ai/AiTier.java
package com.example.app.shared.ai;

import com.example.app.modules.usertype.UserTypeCode;

/**
 * Tier konfigurasi AI. Mengikuti tipe user (users.user_type): FREE -> FREE;
 * VIP_MONTHLY dan VIP_YEARLY -> VIP (konfigurasi sama). Tipe yang tidak
 * dikenal diperlakukan sebagai FREE (arah paling aman terhadap biaya).
 */
public enum AiTier {
    FREE,
    VIP;

    public static AiTier fromUserType(String userType) {
        if (UserTypeCode.VIP_MONTHLY.equals(userType) || UserTypeCode.VIP_YEARLY.equals(userType)) {
            return VIP;
        }
        return FREE;
    }
}
