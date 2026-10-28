// backend/src/main/java/com/example/app/shared/config/SecurityConfig.java
package com.example.app.shared.config;

import com.example.app.shared.security.JwtAccessDeniedHandler;
import com.example.app.shared.security.JwtAuthenticationEntryPoint;
import com.example.app.shared.security.JwtAuthenticationFilter;
import com.example.app.shared.security.RateLimitFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CorsConfigurationSource corsConfigurationSource;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Pakai CorsConfigurationSource dari CorsConfig.java (berlaku utk semua modul)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                // REST API stateless -> CSRF token tidak relevan.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        // Endpoint publik (tidak butuh token): register, verifikasi email, login.
                        // TAMBAHKAN endpoint publik modul baru di sini kalau perlu.
                        .requestMatchers(
                                "/api/auth/register",
                                "/api/auth/verify-email",
                                "/api/auth/login",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password",
                                "/api/auth/resend-verification",
                                "/api/auth/reset-password/validate",
                                // Autentikasi admin (pendaftaran admin pertama, login, terima undangan).
                                // Pembatasan siapa yang boleh daftar ada di AdminAuthServiceImpl.
                                "/api/admin/auth/registration-status",
                                "/api/admin/auth/register",
                                "/api/admin/auth/login",
                                "/api/admin/auth/invitation/validate",
                                "/api/admin/auth/accept-invitation"
                        ).permitAll()
                        // Area admin: hanya ROLE_ADMIN. User biasa -> 403.
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // Endpoint akun yang dipakai KEDUA peran (profil & ganti password).
                        .requestMatchers("/api/auth/profile", "/api/auth/change-password").authenticated()
                        // SEMUA endpoint lain (termasuk /api/dashboard/**) WAJIB
                        // JWT valid -> ini yang mencegah orang bypass dengan
                        // paste URL dashboard tanpa login: request ke API-nya
                        // tetap ditolak 401 walau URL frontend-nya ke-akses.
                        // Semua API bisnis user: hanya ROLE_USER. Admin yang login mendapat
                        // 403 di sini -- admin tidak boleh memakai dashboard/fitur user.
                        .anyRequest().hasRole("USER")
                )
                // Filter custom kita jalan sebelum filter default Spring Security,
                // supaya SecurityContext sudah terisi (dari token) saat authorizeHttpRequests dievaluasi.
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // Rate limit sebelum JWT filter, tapi tetap di dalam chain
                // (setelah CorsFilter) supaya 429 membawa header CORS.
                .addFilterBefore(rateLimitFilter, JwtAuthenticationFilter.class);

        return http.build();
    }
}
