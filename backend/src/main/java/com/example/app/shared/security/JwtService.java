// backend/src/main/java/com/example/app/shared/security/JwtService.java
package com.example.app.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.Optional;

@Component
public class JwtService {

    private static final String PASSWORD_VERSION_CLAIM = "pv";
    private static final String ROLE_CLAIM = "role";

    @Value("${app.security.jwt-secret}")
    private String jwtSecret;

    @Value("${app.security.jwt-expiration-minutes}")
    private long jwtExpirationMinutes;

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate JWT: subject = email, ditambah claim {@code pv} = sidik jari
     * password hash user saat token dibuat. Begitu password berubah (ganti /
     * reset password), {@code pv} token lama tidak cocok lagi dengan DB dan
     * token itu ditolak JwtAuthenticationFilter -- tanpa perlu tabel
     * blacklist/kolom baru.
     */
    public String generateToken(String email, String passwordHash) {
        return generateToken(email, passwordHash, Roles.USER);
    }

    /**
     * Sama seperti di atas, ditambah claim {@code role} ("USER" | "ADMIN"). Claim ini
     * HANYA informasi untuk frontend; otorisasi di backend memakai user_type dari DB
     * (lihat JwtAuthenticationFilter), jadi memalsukan/menunggu claim ini tidak memberi akses.
     */
    public String generateToken(String email, String passwordHash, String role) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(jwtExpirationMinutes * 60);

        return Jwts.builder()
                .subject(email)
                .claim(PASSWORD_VERSION_CLAIM, passwordFingerprint(passwordHash))
                .claim(ROLE_CLAIM, role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey())
                .compact();
    }

    public long getExpirationSeconds() {
        return jwtExpirationMinutes * 60;
    }

    /** Isi token yang sudah lolos verifikasi tanda tangan & kedaluwarsa. */
    public record ParsedToken(String email, String passwordVersion, String role) {
    }

    /** @return isi token kalau tanda tangan valid & belum expired, kosong kalau tidak. */
    public Optional<ParsedToken> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String email = claims.getSubject();
            if (email == null || email.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new ParsedToken(
                    email,
                    claims.get(PASSWORD_VERSION_CLAIM, String.class),
                    // token lama (sebelum fitur admin) tidak punya claim ini -> USER
                    Optional.ofNullable(claims.get(ROLE_CLAIM, String.class)).orElse(Roles.USER)
            ));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * 16 hex pertama SHA-256 dari password hash (BCrypt). Bukan rahasia yang
     * dilindungi -- hanya penanda "versi password"; hash aslinya tidak bisa
     * dipulihkan darinya.
     */
    public String passwordFingerprint(String passwordHash) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(passwordHash.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 tidak tersedia di JVM ini.", e);
        }
    }

    /** @return email (subject) kalau token valid, null kalau invalid/expired. */
    public String extractEmail(String token) {
        return parse(token).map(ParsedToken::email).orElse(null);
    }

    public boolean isValid(String token) {
        return parse(token).isPresent();
    }
}
