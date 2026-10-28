// filepath: /backend/src/main/java/com/example/app/shared/security/RateLimitFilter.java
package com.example.app.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Pembatas laju (rate limit) sederhana, in-memory, per IP, jendela tetap 60
 * detik -- tanpa dependency baru. Melindungi endpoint yang rawan disalahgunakan:
 * brute force login, email-bombing (register/forgot/resend), dan probing email
 * lewat search collaborator.
 * <p>
 * BATASAN yang perlu diketahui:
 * <ul>
 *   <li>State ada di memori 1 instance. Kalau backend jalan di beberapa
 *       instance, batasnya berlaku PER instance (untuk itu pindah ke
 *       Redis/gateway).</li>
 *   <li>Di belakang reverse proxy, isi {@code app.rate-limit.trust-forward-headers=true}
 *       agar IP klien diambil dari X-Forwarded-For -- HANYA kalau proxy Anda
 *       menimpa header itu (kalau tidak, klien bisa memalsukannya).</li>
 * </ul>
 * Dipasang DI DALAM security filter chain (lihat SecurityConfig) supaya balasan
 * 429 tetap membawa header CORS dan terbaca oleh FE.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MILLIS = 60_000L;
    private static final int CLEANUP_THRESHOLD = 10_000;
    private static final Pattern COLLABORATOR_SEARCH = Pattern.compile("^/api/projects/[^/]+/collaborators/search$");
    private static final Pattern TEST_CASE_IMPORT = Pattern.compile("^/api/projects/[^/]+/test-cases/import$");
    private static final Pattern AUTOMATION_GENERATE = Pattern.compile("^/api/projects/[^/]+/automation/generations$");
    private static final Pattern TEST_CASE_AI_GENERATE = Pattern.compile("^/api/projects/[^/]+/test-cases/ai-generation$");

    @Value("${app.rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${app.rate-limit.login-per-minute:20}")
    private int loginPerMinute;

    @Value("${app.rate-limit.register-per-minute:10}")
    private int registerPerMinute;

    // forgot-password, resend-verification, reset-password, change-password (memicu email / pakai token / cek password)
    @Value("${app.rate-limit.email-per-minute:5}")
    private int emailPerMinute;

    @Value("${app.rate-limit.search-per-minute:30}")
    private int searchPerMinute;

    // Import test case dari Excel (parsing file berat; pratinjau + simpan = 2 panggilan per import)
    @Value("${app.rate-limit.import-per-minute:10}")
    private int importPerMinute;

    // Generate automation dgn AI (setiap permintaan = biaya ke penyedia AI)
    @Value("${app.rate-limit.automation-per-minute:6}")
    private int automationPerMinute;

    // Generate test case dgn AI (setiap permintaan = biaya ke penyedia AI)
    @Value("${app.rate-limit.testcase-ai-per-minute:6}")
    private int testCaseAiPerMinute;

    // Pendaftaran admin pertama: sengaja lebih ketat dari register user
    @Value("${app.rate-limit.admin-register-per-minute:5}")
    private int adminRegisterPerMinute;

    @Value("${app.rate-limit.trust-forward-headers:false}")
    private boolean trustForwardHeaders;

    private static final class Window {
        final long startedAt;
        int count;

        Window(long startedAt) {
            this.startedAt = startedAt;
            this.count = 1;
        }
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String rule = null;
        int limit = 0;
        String path = request.getRequestURI();
        String method = request.getMethod();

        if ("POST".equals(method)) {
            switch (path) {
                case "/api/auth/login" -> { rule = "login"; limit = loginPerMinute; }
                case "/api/auth/register" -> { rule = "register"; limit = registerPerMinute; }
                case "/api/auth/forgot-password" -> { rule = "forgot"; limit = emailPerMinute; }
                case "/api/auth/resend-verification" -> { rule = "resend"; limit = emailPerMinute; }
                case "/api/auth/reset-password" -> { rule = "reset"; limit = emailPerMinute * 2; }
                // Ganti password saat login (user + admin): menebak "password saat ini" dengan token curian
                case "/api/auth/change-password" -> { rule = "change-password"; limit = emailPerMinute; }
                case "/api/admin/auth/login" -> { rule = "admin-login"; limit = loginPerMinute; }
                case "/api/admin/auth/register" -> { rule = "admin-register"; limit = adminRegisterPerMinute; }
                case "/api/admin/auth/accept-invitation" -> { rule = "admin-accept"; limit = emailPerMinute * 2; }
                case "/api/admin/admins" -> { rule = "admin-invite"; limit = emailPerMinute; } // memicu email
                default -> {
                    if (TEST_CASE_IMPORT.matcher(path).matches()) {
                        rule = "import";
                        limit = importPerMinute;
                    } else if (AUTOMATION_GENERATE.matcher(path).matches()) {
                        rule = "automation";
                        limit = automationPerMinute;
                    } else if (TEST_CASE_AI_GENERATE.matcher(path).matches()) {
                        rule = "testcase-ai";
                        limit = testCaseAiPerMinute;
                    }
                }
            }
        } else if ("GET".equals(method) && COLLABORATOR_SEARCH.matcher(path).matches()) {
            rule = "search";
            limit = searchPerMinute;
        } else if ("GET".equals(method)
                && ("/api/admin/auth/registration-status".equals(path)
                    || "/api/admin/auth/invitation/validate".equals(path))) {
            rule = "admin-public";
            limit = searchPerMinute;
        }

        if (!enabled || rule == null) {
            filterChain.doFilter(request, response);
            return;
        }

        long now = System.currentTimeMillis();
        Window window = windows.compute(rule + ":" + clientIp(request), (key, current) -> {
            if (current == null || now - current.startedAt >= WINDOW_MILLIS) {
                return new Window(now);
            }
            current.count++;
            return current;
        });

        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt >= WINDOW_MILLIS);
        }

        if (window.count > limit) {
            long retryAfterSeconds = Math.max(1, (window.startedAt + WINDOW_MILLIS - now + 999) / 1000);
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"timestamp\":\"" + LocalDateTime.now() + "\",\"status\":429,"
                            + "\"message\":\"Terlalu banyak percobaan. Coba lagi dalam " + retryAfterSeconds + " detik.\"}"
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String clientIp(HttpServletRequest request) {
        if (trustForwardHeaders) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
