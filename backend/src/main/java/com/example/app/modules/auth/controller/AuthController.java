// backend/src/main/java/com/example/app/modules/auth/controller/AuthController.java
package com.example.app.modules.auth.controller;

import com.example.app.modules.auth.dto.ChangePasswordRequestDTO;
import com.example.app.modules.auth.dto.ForgotPasswordRequestDTO;
import com.example.app.modules.auth.dto.LoginRequestDTO;
import com.example.app.modules.auth.dto.LoginResponseDTO;
import com.example.app.modules.auth.dto.ProfileResponseDTO;
import com.example.app.modules.auth.dto.RegisterRequestDTO;
import com.example.app.modules.auth.dto.RegisterResponseDTO;
import com.example.app.modules.auth.dto.ResendVerificationRequestDTO;
import com.example.app.modules.auth.dto.ResetPasswordRequestDTO;
import com.example.app.modules.auth.dto.UpdateProfileRequestDTO;
import com.example.app.modules.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<RegisterResponseDTO> register(@Valid @RequestBody RegisterRequestDTO request) {
        RegisterResponseDTO response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * User klik link di email -> browser navigasi langsung ke sini (bukan AJAX,
     * jadi tidak kena isu CORS) -> backend validasi token -> redirect (302) ke
     * halaman login frontend dengan query param status verifikasi.
     */
    @GetMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@RequestParam("token") String token) {
        String redirectUrl = authService.verifyEmail(token);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(redirectUrl))
                .build();
    }

    /**
     * Kirim ulang email verifikasi. Respons SELALU sama persis (generik) --
     * tidak membocorkan apakah email terdaftar / sudah verified. Ada cooldown
     * 60 detik per email dan rate limit per IP (RateLimitFilter).
     */
    @PostMapping("/resend-verification")
    public ResponseEntity<Map<String, String>> resendVerification(
            @Valid @RequestBody ResendVerificationRequestDTO request
    ) {
        authService.resendVerification(request);
        return ResponseEntity.ok(Map.of(
                "message",
                "Kalau email tersebut terdaftar dan belum terverifikasi, kami sudah mengirim ulang link verifikasi."
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        LoginResponseDTO response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Step 2-3 flow forgot password: user input email -> kirim email reset
     * link (kalau email terdaftar). Response SELALU sama persis (generik)
     * baik email ketemu maupun tidak -- cegah user enumeration.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequestDTO request
    ) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(Map.of(
                "message",
                "Kalau email tersebut terdaftar, kami sudah mengirim link reset password ke email itu."
        ));
    }

    /**
     * Dipanggil frontend saat halaman "input password baru" (step 4) pertama
     * kali dibuka -- cek token masih valid SEBELUM user isi form, supaya
     * error "link kedaluwarsa" langsung kelihatan tanpa nunggu submit.
     */
    @GetMapping("/reset-password/validate")
    public ResponseEntity<Map<String, Boolean>> validateResetToken(@RequestParam("token") String token) {
        authService.validateResetToken(token); // throw InvalidOrExpiredResetTokenException kalau invalid
        return ResponseEntity.ok(Map.of("valid", true));
    }

    /** Step 4 flow forgot password: submit password baru + confirm. */
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequestDTO request
    ) {
        authService.resetPassword(request);
        return ResponseEntity.ok(Map.of(
                "message", "Password berhasil diubah. Silakan login dengan password baru kamu."
        ));
    }

    /**
     * PROTECTED (wajib JWT) -- beda dengan /reset-password (flow lupa
     * password lewat email), ini utk user yang SEDANG LOGIN dan tau
     * password lama-nya.
     */
    @PostMapping("/change-password")
    public ResponseEntity<Map<String, Object>> changePassword(
            @Valid @RequestBody ChangePasswordRequestDTO request,
            Authentication authentication
    ) {
        LoginResponseDTO session = authService.changePassword(authentication.getName(), request);
        // Token LAMA sudah tidak berlaku -- FE wajib mengganti token tersimpan
        // dengan accessToken di bawah (field tambahan; FE yang mengabaikannya
        // akan kena 401 di request berikutnya lalu diarahkan login ulang).
        return ResponseEntity.ok(Map.of(
                "message", "Password berhasil diubah.",
                "accessToken", session.getAccessToken(),
                "tokenType", session.getTokenType(),
                "expiresIn", session.getExpiresIn()
        ));
    }

    /** PROTECTED (wajib JWT) -- profile user yang sedang login. */
    @GetMapping("/profile")
    public ProfileResponseDTO getProfile(Authentication authentication) {
        return authService.getProfile(authentication.getName());
    }

    /**
     * PROTECTED (wajib JWT) -- requirement #4: user set nama tampilan
     * custom, supaya bisa dipakai (selain email) saat orang lain nyari dia
     * untuk ditambahkan jadi team member project (lihat
     * ProjectCollaboratorController.searchUsersToAdd).
     */
    @PatchMapping("/profile")
    public ProfileResponseDTO updateProfile(
            @Valid @RequestBody UpdateProfileRequestDTO request,
            Authentication authentication
    ) {
        return authService.updateProfile(authentication.getName(), request);
    }
}
