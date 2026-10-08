// filepath: /backend/src/test/java/com/example/app/shared/security/JwtServiceTest.java
package com.example.app.shared.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String OLD_HASH = "$2a$10$oldHashAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String NEW_HASH = "$2a$10$newHashBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret",
                "0123456789012345678901234567890123456789012345678901234567890123");
        ReflectionTestUtils.setField(jwtService, "jwtExpirationMinutes", 60L);
    }

    @Test
    void tokenCarriesEmailAndPasswordVersion() {
        String token = jwtService.generateToken("a@b.com", OLD_HASH);

        JwtService.ParsedToken parsed = jwtService.parse(token).orElseThrow();
        assertEquals("a@b.com", parsed.email());
        assertEquals(jwtService.passwordFingerprint(OLD_HASH), parsed.passwordVersion());
    }

    @Test
    void passwordChangeInvalidatesOldTokenVersion() {
        String token = jwtService.generateToken("a@b.com", OLD_HASH);

        JwtService.ParsedToken parsed = jwtService.parse(token).orElseThrow();
        assertNotEquals(jwtService.passwordFingerprint(NEW_HASH), parsed.passwordVersion());
    }

    @Test
    void tamperedOrGarbageTokensAreRejected() {
        String token = jwtService.generateToken("a@b.com", OLD_HASH);

        assertTrue(jwtService.parse(token.substring(0, token.length() - 3) + "abc").isEmpty());
        assertTrue(jwtService.parse("bukan.token.jwt").isEmpty());
    }
}
