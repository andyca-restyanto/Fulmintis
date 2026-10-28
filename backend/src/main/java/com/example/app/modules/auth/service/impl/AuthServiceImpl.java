// backend/src/main/java/com/example/app/modules/auth/service/impl/AuthServiceImpl.java
package com.example.app.modules.auth.service.impl;

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
import com.example.app.modules.auth.entity.User;
import com.example.app.modules.auth.exception.AccountNotVerifiedException;
import com.example.app.modules.auth.exception.EmailAlreadyExistsException;
import com.example.app.modules.auth.exception.IncorrectCurrentPasswordException;
import com.example.app.modules.auth.exception.InvalidCredentialsException;
import com.example.app.modules.auth.exception.InvalidOrExpiredResetTokenException;
import com.example.app.modules.auth.repository.UserRepository;
import com.example.app.modules.auth.service.AuthService;
import com.example.app.modules.auth.service.EmailVerificationResult;
import com.example.app.modules.usertype.UserTypeCode;
import com.example.app.modules.usertype.entity.UserType;
import com.example.app.modules.usertype.repository.UserTypeRepository;
import com.example.app.shared.activitylog.ActivityLogService;
import com.example.app.shared.email.EmailService;
import com.example.app.shared.security.JwtService;
import com.example.app.shared.security.Roles;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final JwtService jwtService;
    private final ActivityLogService activityLogService;
    private final UserTypeRepository userTypeRepository;

    @Value("${app.backend.base-url}")
    private String backendBaseUrl;

    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

    @Value("${app.security.verification-token-expiry-minutes}")
    private long verificationTokenExpiryMinutes;

    @Value("${app.security.reset-password-token-expiry-minutes}")
    private long resetPasswordTokenExpiryMinutes;

    @Override
    @Transactional
    public RegisterResponseDTO register(RegisterRequestDTO request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyExistsException(normalizedEmail);
        }

        String verificationToken = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(verificationTokenExpiryMinutes);

        // Default user type saat register baru = "Free" (dari tabel user_type
        // di database TERPISAH "master_data", di-seed otomatis oleh
        // UserTypeSeeder). Query ini lewat koneksi/datasource BEDA dari
        // insert User di bawah (lihat MasterDataSourceConfig) -- tapi karena
        // cuma READ, tidak perlu distributed transaction/XA. Yang disimpan
        // ke User cuma STRING code-nya, bukan objek relasi (tidak ada FK
        // lintas database).
        UserType freeUserType = userTypeRepository
                .findByCode(UserTypeCode.FREE)
                .orElseThrow(() -> new IllegalStateException(
                        "user_type dengan code FREE tidak ditemukan di database master_data. "
                                + "Pastikan database 'master_data' sudah dibuat dan UserTypeSeeder sudah jalan."
                ));

        User user = User.builder()
                .email(normalizedEmail)
                .password(passwordEncoder.encode(request.getPassword()))
                .verified(false)
                .verificationToken(verificationToken)
                .verificationTokenExpiresAt(expiresAt)
                .userType(freeUserType.getCode())
                .build();

        User saved = userRepository.save(user);

        String verificationLink = backendBaseUrl + "/api/auth/verify-email?token=" + verificationToken;
        emailService.sendVerificationEmail(saved.getEmail(), verificationLink);

        activityLogService.log(saved.getEmail(), "REGISTER");

        return RegisterResponseDTO.builder()
                .id(saved.getId())
                .email(saved.getEmail())
                .createdAt(saved.getCreatedAt())
                .message("Registrasi berhasil! Silakan cek email kamu untuk verifikasi akun.")
                .build();
    }

    @Override
    @Transactional
    public String verifyEmail(String token) {
        Optional<User> maybeUser = userRepository.findByVerificationToken(token);

        if (maybeUser.isEmpty()) {
            return buildRedirectUrl(EmailVerificationResult.INVALID, USER_SIGNIN_PATH);
        }

        User user = maybeUser.get();

        String signinPath = signinPathFor(user);

        if (user.isVerified()) {
            return buildRedirectUrl(EmailVerificationResult.ALREADY_VERIFIED, signinPath);
        }

        if (user.getVerificationTokenExpiresAt() == null
                || user.getVerificationTokenExpiresAt().isBefore(LocalDateTime.now())) {
            return buildRedirectUrl(EmailVerificationResult.EXPIRED, signinPath);
        }

        user.setVerified(true);
        user.setVerificationToken(null);
        user.setVerificationTokenExpiresAt(null);
        userRepository.save(user);

        activityLogService.log(user.getEmail(), "VERIFY_EMAIL");

        return buildRedirectUrl(EmailVerificationResult.SUCCESS, signinPath);
    }

    @Override
    @Transactional
    public LoginResponseDTO login(LoginRequestDTO request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        // Akun ADMIN tidak boleh masuk lewat login user. Balasannya SAMA dengan
        // password salah (401 generik) supaya endpoint ini tidak membocorkan
        // email mana yang admin. Admin login lewat /api/admin/auth/login.
        if (UserTypeCode.ADMIN.equals(user.getUserType())) {
            throw new InvalidCredentialsException();
        }

        // Cek verified SETELAH password valid -> supaya orang yang asal
        // nebak-nebak email tidak bisa membedakan "email tidak ada" vs
        // "email ada tapi belum verified" dari pesan errornya.
        if (!user.isVerified()) {
            throw new AccountNotVerifiedException();
        }

        String role = Roles.fromUserType(user.getUserType());
        String token = jwtService.generateToken(user.getEmail(), user.getPassword(), role);

        activityLogService.log(user.getEmail(), "LOGIN");

        return LoginResponseDTO.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationSeconds())
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .role(role)
                .build();
    }

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequestDTO request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        Optional<User> maybeUser = userRepository.findByEmail(normalizedEmail);

        // SENGAJA tidak throw exception kalau email tidak ketemu -- caller
        // (controller) selalu balas pesan generik yang sama persis baik
        // email ketemu maupun tidak, supaya endpoint ini tidak bisa dipakai
        // untuk mengecek "email mana saja yang terdaftar" (user enumeration
        // lewat forgot password adalah celah keamanan yang umum).
        if (maybeUser.isEmpty()) {
            return;
        }

        User user = maybeUser.get();

        // Admin yang masih berstatus UNDANGAN (belum menerima undangan => belum terverifikasi)
        // tidak boleh lewat jalur reset: password-nya dibuat lewat accept-invitation, dan reset
        // di sini akan meninggalkan akun tanpa verifikasi dengan token undangan yang masih hidup.
        // Diam-diam (respons ke klien tetap generik) agar tidak membuka enumerasi email.
        if (UserTypeCode.ADMIN.equals(user.getUserType()) && !user.isVerified()) {
            return;
        }

        // Akun nonaktif tidak dikirimi link reset (tidak ada jalan masuk lewat email). Diam-diam
        // juga: respons ke klien tetap generik.
        if (!user.isActive()) {
            return;
        }

        // Cooldown per email: link yang baru dikirim < 60 detik lalu -> abaikan
        // diam-diam (respons ke klien tetap generik). Mencegah endpoint ini
        // dipakai membanjiri inbox satu korban; waktu kirim dihitung dari
        // expiresAt - masa berlaku, tanpa kolom baru.
        if (wasIssuedRecently(user.getResetPasswordTokenExpiresAt(), resetPasswordTokenExpiryMinutes)) {
            return;
        }

        String resetToken = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(resetPasswordTokenExpiryMinutes);

        user.setResetPasswordToken(resetToken);
        user.setResetPasswordTokenExpiresAt(expiresAt);
        userRepository.save(user);

        // BEDA dengan link verifikasi email: link ini mengarah LANGSUNG ke
        // FRONTEND (bukan backend) -- karena user perlu isi FORM password
        // baru, bukan sekadar klik konfirmasi. Validasi token yang
        // sebenarnya terjadi saat user SUBMIT form (POST /reset-password).
        // Akun admin memakai halaman reset di area admin (sukses -> /admin/signin).
        String resetPath = UserTypeCode.ADMIN.equals(user.getUserType())
                ? "/admin/reset-password" : "/auth/reset-password";
        String resetLink = frontendBaseUrl + resetPath + "?token=" + resetToken;
        emailService.sendPasswordResetEmail(user.getEmail(), resetLink);

        activityLogService.log(user.getEmail(), "FORGOT_PASSWORD_REQUEST");
    }

    @Override
    public void validateResetToken(String token) {
        User user = userRepository.findByResetPasswordToken(token)
                .orElseThrow(InvalidOrExpiredResetTokenException::new);

        if (!user.isActive()
                || user.getResetPasswordTokenExpiresAt() == null
                || user.getResetPasswordTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidOrExpiredResetTokenException();
        }
        // valid -> tidak perlu return apa-apa, controller balas 200 OK kalau tidak ada exception
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequestDTO request) {
        User user = userRepository.findByResetPasswordToken(request.getToken())
                .orElseThrow(InvalidOrExpiredResetTokenException::new);

        if (!user.isActive()
                || user.getResetPasswordTokenExpiresAt() == null
                || user.getResetPasswordTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidOrExpiredResetTokenException();
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        // Token cuma bisa dipakai SEKALI -- langsung di-clear setelah dipakai,
        // supaya link yang sama tidak bisa dipakai reset password berkali-kali.
        user.setResetPasswordToken(null);
        user.setResetPasswordTokenExpiresAt(null);
        userRepository.save(user);

        activityLogService.log(user.getEmail(), "RESET_PASSWORD");
    }

    @Override
    @Transactional
    public LoginResponseDTO changePassword(String userEmail, ChangePasswordRequestDTO request) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException(
                        "User dengan email " + userEmail + " tidak ditemukan (token valid tapi user sudah terhapus?)"
                ));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new IncorrectCurrentPasswordException();
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        User saved = userRepository.save(user);

        activityLogService.log(user.getEmail(), "CHANGE_PASSWORD");

        // Token lama sudah tidak berlaku (versi password berubah) -> beri
        // token baru untuk sesi ini.
        return LoginResponseDTO.builder()
                .accessToken(jwtService.generateToken(
                        saved.getEmail(), saved.getPassword(), Roles.fromUserType(saved.getUserType())))
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationSeconds())
                .id(saved.getId())
                .email(saved.getEmail())
                .name(saved.getName())
                .role(Roles.fromUserType(saved.getUserType()))
                .build();
    }

    @Override
    @Transactional
    public void resendVerification(ResendVerificationRequestDTO request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        Optional<User> maybeUser = userRepository.findByEmail(normalizedEmail);
        if (maybeUser.isEmpty() || maybeUser.get().isVerified()) {
            return; // sengaja diam -- anti user enumeration, lihat javadoc AuthService
        }

        User user = maybeUser.get();

        if (wasIssuedRecently(user.getVerificationTokenExpiresAt(), verificationTokenExpiryMinutes)) {
            return; // cooldown 60 detik per email
        }

        String verificationToken = UUID.randomUUID().toString();
        user.setVerificationToken(verificationToken);
        user.setVerificationTokenExpiresAt(LocalDateTime.now().plusMinutes(verificationTokenExpiryMinutes));
        userRepository.save(user);

        String verificationLink = backendBaseUrl + "/api/auth/verify-email?token=" + verificationToken;
        emailService.sendVerificationEmail(user.getEmail(), verificationLink);

        activityLogService.log(user.getEmail(), "RESEND_VERIFICATION");
    }

    /**
     * true kalau token diterbitkan < 60 detik lalu. Waktu terbit = expiresAt
     * dikurangi masa berlaku token (tidak ada kolom "issued at").
     */
    private boolean wasIssuedRecently(LocalDateTime expiresAt, long expiryMinutes) {
        if (expiresAt == null) {
            return false;
        }
        LocalDateTime issuedAt = expiresAt.minusMinutes(expiryMinutes);
        return issuedAt.plusSeconds(60).isAfter(LocalDateTime.now());
    }

    @Override
    public ProfileResponseDTO getProfile(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException(
                        "User dengan email " + userEmail + " tidak ditemukan (token valid tapi user sudah terhapus?)"
                ));
        return toProfileDTO(user);
    }

    @Override
    @Transactional
    public ProfileResponseDTO updateProfile(String userEmail, UpdateProfileRequestDTO request) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException(
                        "User dengan email " + userEmail + " tidak ditemukan (token valid tapi user sudah terhapus?)"
                ));

        user.setName(request.getName().trim());
        User saved = userRepository.save(user);

        activityLogService.log(user.getEmail(), "UPDATE_PROFILE");

        return toProfileDTO(saved);
    }

    private ProfileResponseDTO toProfileDTO(User user) {
        return ProfileResponseDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .build();
    }

    private static final String USER_SIGNIN_PATH = "/auth/signin";
    private static final String ADMIN_SIGNIN_PATH = "/admin/signin";

    private String signinPathFor(User user) {
        return UserTypeCode.ADMIN.equals(user.getUserType()) ? ADMIN_SIGNIN_PATH : USER_SIGNIN_PATH;
    }

    private String buildRedirectUrl(EmailVerificationResult result, String signinPath) {
        String loginUrl = frontendBaseUrl + signinPath;

        return switch (result) {
            case SUCCESS, ALREADY_VERIFIED -> loginUrl + "?verified=true";
            case EXPIRED -> loginUrl + "?verified=false&reason=expired";
            case INVALID -> loginUrl + "?verified=false&reason=invalid";
        };
    }
}
