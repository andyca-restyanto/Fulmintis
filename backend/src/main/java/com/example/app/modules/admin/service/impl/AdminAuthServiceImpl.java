// filepath: /backend/src/main/java/com/example/app/modules/admin/service/impl/AdminAuthServiceImpl.java
package com.example.app.modules.admin.service.impl;

import com.example.app.modules.admin.dto.AdminAcceptInvitationRequestDTO;
import com.example.app.modules.admin.dto.AdminInvitationValidationResponseDTO;
import com.example.app.modules.admin.dto.AdminProfileResponseDTO;
import com.example.app.modules.admin.dto.AdminRegisterRequestDTO;
import com.example.app.modules.admin.dto.AdminRegisterResponseDTO;
import com.example.app.modules.admin.dto.AdminRegistrationStatusResponseDTO;
import com.example.app.modules.admin.exception.AccountDeactivatedException;
import com.example.app.modules.admin.exception.AdminRegistrationClosedException;
import com.example.app.modules.admin.exception.InvalidAdminBootstrapCodeException;
import com.example.app.modules.admin.exception.InvalidAdminInvitationException;
import com.example.app.modules.admin.service.AdminAuthService;
import com.example.app.modules.auth.dto.LoginRequestDTO;
import com.example.app.modules.auth.dto.LoginResponseDTO;
import com.example.app.modules.auth.entity.User;
import com.example.app.modules.auth.exception.AccountNotVerifiedException;
import com.example.app.modules.auth.exception.EmailAlreadyExistsException;
import com.example.app.modules.auth.exception.InvalidCredentialsException;
import com.example.app.modules.auth.repository.UserRepository;
import com.example.app.modules.usertype.UserTypeCode;
import com.example.app.modules.usertype.entity.UserType;
import com.example.app.modules.usertype.repository.UserTypeRepository;
import com.example.app.shared.activitylog.ActivityLogService;
import com.example.app.shared.email.EmailService;
import com.example.app.shared.security.JwtService;
import com.example.app.shared.security.Roles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthServiceImpl implements AdminAuthService {

    /** Kunci advisory lock untuk menyerialkan pendaftaran admin pertama (nilai bebas, harus tetap). */
    static final long ADMIN_BOOTSTRAP_LOCK_KEY = 7_201_810_001L;

    private final UserRepository userRepository;
    private final UserTypeRepository userTypeRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final JwtService jwtService;
    private final ActivityLogService activityLogService;

    @Value("${app.backend.base-url}")
    private String backendBaseUrl;

    @Value("${app.security.verification-token-expiry-minutes}")
    private long verificationTokenExpiryMinutes;

    // Kosong = pendaftaran admin pertama bebas (sesuai keputusan produk). Disarankan
    // diisi di production (env ADMIN_BOOTSTRAP_CODE) lalu dikosongkan/dihapus setelah admin pertama ada.
    @Value("${app.admin.bootstrap-code:}")
    private String bootstrapCode;

    @Override
    @Transactional(readOnly = true)
    public AdminRegistrationStatusResponseDTO getRegistrationStatus() {
        boolean open = userRepository.countByUserType(UserTypeCode.ADMIN) == 0;
        return AdminRegistrationStatusResponseDTO.builder()
                .open(open)
                .bootstrapCodeRequired(open && isBootstrapCodeConfigured())
                .build();
    }

    @Override
    @Transactional
    public AdminRegisterResponseDTO register(AdminRegisterRequestDTO request) {
        // Serialkan cek-lalu-simpan: dua request bersamaan (atau dua instance backend)
        // tidak boleh sama-sama melihat "belum ada admin". Kunci dilepas saat transaksi selesai.
        userRepository.lockAdvisory(ADMIN_BOOTSTRAP_LOCK_KEY);

        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (userRepository.countByUserType(UserTypeCode.ADMIN) > 0) {
            // Pakai log aplikasi, bukan activity_log: transaksi ini di-rollback karena exception.
            log.warn("Pendaftaran admin ditolak (sudah ada admin): email={}", normalizedEmail);
            throw new AdminRegistrationClosedException();
        }

        if (isBootstrapCodeConfigured() && !bootstrapCodeMatches(request.getBootstrapCode())) {
            log.warn("Pendaftaran admin ditolak (kode bootstrap salah): email={}", normalizedEmail);
            throw new InvalidAdminBootstrapCodeException();
        }

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyExistsException(normalizedEmail);
        }

        UserType adminType = userTypeRepository.findByCode(UserTypeCode.ADMIN)
                .orElseThrow(() -> new IllegalStateException(
                        "user_type dengan code ADMIN tidak ditemukan di database master_data. "
                                + "Pastikan UserTypeSeeder sudah jalan (restart backend)."
                ));

        String verificationToken = UUID.randomUUID().toString();

        User admin = User.builder()
                .email(normalizedEmail)
                .name(request.getName().trim())
                .password(passwordEncoder.encode(request.getPassword()))
                .verified(false)
                .verificationToken(verificationToken)
                .verificationTokenExpiresAt(LocalDateTime.now().plusMinutes(verificationTokenExpiryMinutes))
                .userType(adminType.getCode())
                .build();

        User saved = userRepository.save(admin);

        // Link yang sama dengan user biasa; AuthServiceImpl.verifyEmail mengalihkan
        // akun ADMIN ke /admin/signin.
        emailService.sendVerificationEmail(
                saved.getEmail(), backendBaseUrl + "/api/auth/verify-email?token=" + verificationToken);

        activityLogService.log(saved.getEmail(), "ADMIN_REGISTER_BOOTSTRAP");

        return AdminRegisterResponseDTO.builder()
                .id(saved.getId())
                .email(saved.getEmail())
                .name(saved.getName())
                .createdAt(saved.getCreatedAt())
                .message("Registrasi admin berhasil! Silakan cek email kamu untuk verifikasi akun.")
                .build();
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

        // Akun non-admin (user biasa) ditolak dengan balasan yang SAMA dengan password salah.
        if (!UserTypeCode.ADMIN.equals(user.getUserType())) {
            throw new InvalidCredentialsException();
        }

        // Dicek SEBELUM verifikasi: admin nonaktif mendapat 403 ber-code sendiri (FE tidak boleh
        // menawarkan "kirim ulang verifikasi"). Baru sampai sini setelah password terbukti benar.
        if (!user.isActive()) {
            throw new AccountDeactivatedException();
        }
        if (!user.isVerified()) {
            throw new AccountNotVerifiedException();
        }

        String token = jwtService.generateToken(user.getEmail(), user.getPassword(), Roles.ADMIN);

        activityLogService.log(user.getEmail(), "ADMIN_LOGIN");

        return LoginResponseDTO.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationSeconds())
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .role(Roles.ADMIN)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AdminInvitationValidationResponseDTO validateInvitation(String token) {
        User invitee = requirePendingInvitation(token);
        return AdminInvitationValidationResponseDTO.builder()
                .valid(true)
                .email(invitee.getEmail())
                .name(invitee.getName())
                .build();
    }

    @Override
    @Transactional
    public void acceptInvitation(AdminAcceptInvitationRequestDTO request) {
        User invitee = requirePendingInvitation(request.getToken());

        invitee.setPassword(passwordEncoder.encode(request.getNewPassword()));
        // Memegang link di emailnya = bukti kepemilikan email, jadi sekaligus terverifikasi.
        invitee.setVerified(true);
        invitee.setVerificationToken(null);
        invitee.setVerificationTokenExpiresAt(null);
        // Token undangan hanya bisa dipakai SEKALI.
        invitee.setAdminInvitationToken(null);
        invitee.setAdminInvitationExpiresAt(null);
        userRepository.save(invitee);

        activityLogService.log(invitee.getEmail(), "ADMIN_INVITE_ACCEPTED");
    }

    @Override
    @Transactional(readOnly = true)
    public AdminProfileResponseDTO getProfile(String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new IllegalStateException(
                        "Admin dengan email " + adminEmail + " tidak ditemukan (token valid tapi user sudah terhapus?)"));
        return AdminProfileResponseDTO.builder()
                .id(admin.getId())
                .email(admin.getEmail())
                .name(admin.getName())
                .role(Roles.fromUserType(admin.getUserType()))
                .build();
    }

    /** Token harus ada, milik akun ADMIN yang belum aktif, dan belum kedaluwarsa. */
    private User requirePendingInvitation(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidAdminInvitationException();
        }
        User invitee = userRepository.findByAdminInvitationToken(token)
                .orElseThrow(InvalidAdminInvitationException::new);

        // Admin nonaktif tidak bisa menerima undangan (undangan tidak dihapus, hanya diblokir).
        boolean pending = UserTypeCode.ADMIN.equals(invitee.getUserType()) && !invitee.isVerified()
                && invitee.isActive();
        boolean expired = invitee.getAdminInvitationExpiresAt() == null
                || invitee.getAdminInvitationExpiresAt().isBefore(LocalDateTime.now());
        if (!pending || expired) {
            throw new InvalidAdminInvitationException();
        }
        return invitee;
    }

    private boolean isBootstrapCodeConfigured() {
        return bootstrapCode != null && !bootstrapCode.isBlank();
    }

    /** Perbandingan waktu-konstan agar kode tidak bisa ditebak lewat selisih waktu respons. */
    private boolean bootstrapCodeMatches(String provided) {
        if (provided == null) {
            return false;
        }
        return MessageDigest.isEqual(
                bootstrapCode.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}
