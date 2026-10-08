// filepath: /backend/src/main/java/com/example/app/shared/security/JwtAuthenticationFilter.java
package com.example.app.shared.security;

import com.example.app.modules.auth.entity.User;
import com.example.app.modules.auth.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Optional;

/**
 * Baca "Authorization: Bearer <jwt>". Token dianggap sah HANYA kalau:
 * <ol>
 *   <li>tanda tangan valid & belum kedaluwarsa,</li>
 *   <li>user pemilik token MASIH ada di DB dan sudah verified, dan</li>
 *   <li>versi password di token ({@code pv}) sama dengan password user
 *       sekarang -- jadi token lama otomatis mati setelah ganti/reset password.</li>
 * </ol>
 * Kalau salah satu gagal, request diteruskan TANPA autentikasi sehingga
 * endpoint terlindungi membalas 401 (JwtAuthenticationEntryPoint). Sebelumnya
 * user yang sudah terhapus lolos filter lalu jadi 500 di service.
 * <p>
 * Biaya: 1 query user per request ber-token (PK/unique index email).
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "Authorization";
    private static final String TOKEN_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String header = request.getHeader(HEADER_NAME);

        if (header != null && header.startsWith(TOKEN_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            String token = header.substring(TOKEN_PREFIX.length());
            Optional<String> authenticatedEmail = resolveAuthenticatedEmail(token);

            if (authenticatedEmail.isPresent()) {
                var authentication = new UsernamePasswordAuthenticationToken(
                        authenticatedEmail.get(), null, Collections.emptyList()
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }

    private Optional<String> resolveAuthenticatedEmail(String token) {
        Optional<JwtService.ParsedToken> parsed = jwtService.parse(token);
        if (parsed.isEmpty() || parsed.get().passwordVersion() == null) {
            // Token tanpa claim "pv" = token lama (sebelum fitur ini) -> login ulang.
            return Optional.empty();
        }

        Optional<User> maybeUser = userRepository.findByEmail(parsed.get().email());
        if (maybeUser.isEmpty() || !maybeUser.get().isVerified()) {
            return Optional.empty();
        }

        User user = maybeUser.get();
        byte[] expected = jwtService.passwordFingerprint(user.getPassword()).getBytes(StandardCharsets.UTF_8);
        byte[] actual = parsed.get().passwordVersion().getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            return Optional.empty();
        }

        return Optional.of(user.getEmail());
    }
}
