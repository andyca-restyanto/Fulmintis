// backend/src/main/java/com/example/app/modules/auth/service/AuthService.java
package com.example.app.modules.auth.service;

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

public interface AuthService {
    RegisterResponseDTO register(RegisterRequestDTO request);

    /**
     * Validasi token verifikasi dan tandai user sebagai verified.
     * @return URL lengkap tujuan redirect di frontend (sudah termasuk query param status).
     */
    String verifyEmail(String token);

    /**
     * Validasi email+password+status verified, generate JWT kalau valid.
     * Melempar InvalidCredentialsException / AccountNotVerifiedException kalau gagal.
     */
    LoginResponseDTO login(LoginRequestDTO request);

    /**
     * Generate reset token & kirim email kalau email terdaftar. TIDAK
     * melempar exception kalau email tidak ditemukan -- caller (controller)
     * selalu balas pesan generik yang sama supaya tidak bisa dipakai untuk
     * cek email mana yang terdaftar (user enumeration).
     */
    void forgotPassword(ForgotPasswordRequestDTO request);

    /**
     * Cek token reset password masih valid (belum dipakai & belum expired).
     * Dipanggil frontend saat halaman "input password baru" pertama kali
     * dibuka, SEBELUM user isi form -- supaya langsung kasih tau kalau link
     * sudah kedaluwarsa, bukan nunggu submit dulu.
     */
    void validateResetToken(String token);

    /** Set password baru kalau token valid (flow LUPA password / belum login). */
    void resetPassword(ResetPasswordRequestDTO request);

    /**
     * Ganti password user yang SEDANG LOGIN -- beda dengan resetPassword(),
     * di sini user WAJIB tau password lama-nya (bukan lewat email token).
     * @throws com.example.app.modules.auth.exception.IncorrectCurrentPasswordException
     *         kalau currentPassword yang dikirim salah.
     */
    /**
     * Ganti password user yang sedang login. Semua token LAMA otomatis tidak
     * berlaku lagi (versi password di JWT berubah), jadi method ini membalas
     * token BARU supaya sesi user yang mengganti password tidak ikut terputus.
     */
    LoginResponseDTO changePassword(String userEmail, ChangePasswordRequestDTO request);

    /**
     * Kirim ulang link verifikasi. Diam-diam tidak melakukan apa pun kalau
     * email tidak terdaftar / sudah verified / baru saja dikirim (cooldown) --
     * controller SELALU membalas pesan generik yang sama.
     */
    void resendVerification(ResendVerificationRequestDTO request);

    /** Ambil profile (id, email, name) user yang sedang login. */
    ProfileResponseDTO getProfile(String userEmail);

    /** Update nama tampilan (requirement #4) user yang sedang login. */
    ProfileResponseDTO updateProfile(String userEmail, UpdateProfileRequestDTO request);
}
