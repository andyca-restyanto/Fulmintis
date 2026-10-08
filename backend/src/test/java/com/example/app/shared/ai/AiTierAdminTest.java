// filepath: /backend/src/test/java/com/example/app/shared/ai/AiTierAdminTest.java
package com.example.app.shared.ai;

import com.example.app.modules.usertype.UserTypeCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiTierAdminTest {

    @Test
    void adminNeverGetsVipQuota() {
        assertEquals(AiTier.FREE, AiTier.fromUserType(UserTypeCode.ADMIN));
    }

    @Test
    void existingMappingIsUnchanged() {
        assertEquals(AiTier.FREE, AiTier.fromUserType(UserTypeCode.FREE));
        assertEquals(AiTier.VIP, AiTier.fromUserType(UserTypeCode.VIP_MONTHLY));
        assertEquals(AiTier.VIP, AiTier.fromUserType(UserTypeCode.VIP_YEARLY));
        assertEquals(AiTier.FREE, AiTier.fromUserType(null));
    }
}
