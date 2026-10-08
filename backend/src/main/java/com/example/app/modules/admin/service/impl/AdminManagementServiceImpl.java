// filepath: /backend/src/main/java/com/example/app/modules/admin/service/impl/AdminManagementServiceImpl.java
package com.example.app.modules.admin.service.impl;

import com.example.app.modules.admin.dto.AdminInviteRequestDTO;
import com.example.app.modules.admin.dto.AdminInviteResponseDTO;
import com.example.app.modules.admin.dto.AdminListItemResponseDTO;
import com.example.app.modules.admin.dto.AdminListResponseDTO;
import com.example.app.modules.admin.dto.AdminStatus;
import com.example.app.modules.admin.exception.AdminNotFoundException;
import com.example.app.modules.admin.exception.CannotDeactivateSelfException;
import com.example.app.modules.admin.exception.LastActiveAdminException;
import com.example.app.modules.admin.service.AdminManagementService;
import com.example.app.modules.auth.entity.User;
import com.example.app.modules.auth.exception.EmailAlreadyExistsException;
import com.example.app.modules.auth.repository.UserRepository;
import com.example.app.modules.usertype.UserTypeCode;
import com.example.app.modules.usertype.entity.UserType;
import com.example.app.modules.usertype.repository.UserTypeRepository;
import com.example.app.shared.activitylog.ActivityLogService;
import com.example.app.shared.email.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminManagementServiceImpl implements AdminManagementService {

    /** Kunci advisory untuk menyerialkan penonaktifan admin (nilai bebas, harus tetap & beda dari kunci lain). */
    static final long ADMIN_MANAGEMENT_LOCK_KEY = 7_201_810_002L;
    static final int DEFAULT_PAGE_SIZE = 10;
    static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_ACTIVITY_LENGTH = 255; // activity_log.activity VARCHAR(255)

    private final UserRepository userRepository;
    private final UserTypeRepository userTypeRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final ActivityLogService activityLogService;

    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

    @Value("${app.security.admin-invitation-expiry-minutes:1440}")
    private long invitationExpiryMinutes;

    @Override
    @Transactional
    public AdminInviteResponseDTO invite(String inviterEmail, AdminInviteRequestDTO request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        String name = request.getName().trim();

        Optional<User> existing = userRepository.findByEmail(normalizedEmail);

        User invitee;
        if (existing.isPresent()) {
            User current = existing.get();
            boolean pendingInvitation = UserTypeCode.ADMIN.equals(current.getUserType())
                    && current.isActive() // admin nonaktif: aktifkan kembali dulu, bukan diundang ulang
                    && !current.isVerified()
                    && current.getAdminInvitationToken() != null;
            if (!pendingInvitation) {
                // User biasa, admin aktif, atau admin bootstrap yang belum verifikasi: tidak
                // ada "promosi" akun dan tidak menimpa akun yang sudah ada.
                throw new EmailAlreadyExistsException(normalizedEmail);
            }
            invitee = current; // undang ulang: nama diperbarui, token baru di bawah
            invitee.setName(name);
        } else {
            UserType adminType = userTypeRepository.findByCode(UserTypeCode.ADMIN)
                    .orElseThrow(() -> new IllegalStateException(
                            "user_type dengan code ADMIN tidak ditemukan di database master_data. "
                                    + "Pastikan UserTypeSeeder sudah jalan (restart backend)."
                    ));
            invitee = User.builder()
                    .email(normalizedEmail)
                    .name(name)
                    // Password acak yang tidak diketahui siapa pun: akun tidak bisa dipakai
                    // login sampai penerima membuat passwordnya lewat link undangan.
                    .password(passwordEncoder.encode(UUID.randomUUID() + UUID.randomUUID().toString()))
                    .verified(false)
                    .userType(adminType.getCode())
                    .build();
        }

        String token = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(invitationExpiryMinutes);
        invitee.setAdminInvitationToken(token);
        invitee.setAdminInvitationExpiresAt(expiresAt);
        User saved = userRepository.save(invitee);

        String link = frontendBaseUrl + "/admin/accept-invitation?token=" + token;
        emailService.sendAdminInvitationEmail(saved.getEmail(), saved.getName(), link, inviterEmail);

        activityLogService.log(inviterEmail, "ADMIN_INVITE_CREATED");

        return AdminInviteResponseDTO.builder()
                .email(saved.getEmail())
                .name(saved.getName())
                .expiresAt(expiresAt)
                .message("Undangan sudah dikirim ke " + saved.getEmail() + ".")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AdminListResponseDTO listAdmins(String currentAdminEmail, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);

        // Terbaru dulu (undangan baru langsung terlihat di halaman 1); id sebagai pembeda agar
        // urutan stabil untuk baris dengan waktu dibuat yang sama.
        Pageable pageable = PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<User> result = userRepository.findAllByUserType(UserTypeCode.ADMIN, pageable);

        return AdminListResponseDTO.builder()
                .items(result.getContent().stream().map(u -> toItem(u, currentAdminEmail)).toList())
                .page(safePage)
                .size(safeSize)
                .totalItems(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    @Override
    @Transactional
    public AdminListItemResponseDTO deactivate(String actorEmail, UUID adminId) {
        // Serialkan: dua admin yang saling menonaktifkan di saat bersamaan tidak boleh sama-sama
        // lolos dan menyisakan nol admin aktif. Kunci dilepas otomatis saat commit/rollback.
        userRepository.lockAdvisory(ADMIN_MANAGEMENT_LOCK_KEY);

        User target = requireAdmin(adminId);

        if (target.getEmail().equalsIgnoreCase(actorEmail)) {
            throw new CannotDeactivateSelfException();
        }
        if (!target.isActive()) {
            return toItem(target, actorEmail); // sudah nonaktif: idempoten
        }
        // Hanya admin aktif+terverifikasi yang dihitung sebagai "admin aktif". Dihitung SETELAH kunci
        // diambil, jadi penonaktifan yang baru di-commit request lain ikut terlihat.
        boolean targetCounts = target.isVerified();
        if (targetCounts && userRepository.countByUserTypeAndActiveTrueAndVerifiedTrue(UserTypeCode.ADMIN) <= 1) {
            throw new LastActiveAdminException();
        }

        target.setActive(false);
        // Link reset password yang sudah terkirim tidak boleh jadi jalan masuk. Token undangan
        // sengaja TIDAK dihapus: selama nonaktif undangan ditolak (requirePendingInvitation), dan
        // setelah diaktifkan kembali undangan yang belum kedaluwarsa bisa dipakai / dikirim ulang.
        target.setResetPasswordToken(null);
        target.setResetPasswordTokenExpiresAt(null);
        User saved = userRepository.save(target);

        activityLogService.log(actorEmail, activity("ADMIN_DEACTIVATED", saved.getEmail()));
        return toItem(saved, actorEmail);
    }

    @Override
    @Transactional
    public AdminListItemResponseDTO activate(String actorEmail, UUID adminId) {
        User target = requireAdmin(adminId);
        if (target.isActive()) {
            return toItem(target, actorEmail); // sudah aktif: idempoten
        }
        target.setActive(true);
        User saved = userRepository.save(target);

        activityLogService.log(actorEmail, activity("ADMIN_REACTIVATED", saved.getEmail()));
        return toItem(saved, actorEmail);
    }

    /** Id harus ada DAN bertipe ADMIN; akun user biasa dijawab sama dengan "tidak ada". */
    private User requireAdmin(UUID id) {
        return userRepository.findById(id)
                .filter(u -> UserTypeCode.ADMIN.equals(u.getUserType()))
                .orElseThrow(AdminNotFoundException::new);
    }

    private AdminListItemResponseDTO toItem(User user, String currentAdminEmail) {
        AdminStatus status;
        if (!user.isActive()) {
            status = AdminStatus.INACTIVE;
        } else if (user.isVerified()) {
            status = AdminStatus.ACTIVE;
        } else {
            status = AdminStatus.PENDING;
        }
        // Masa berlaku undangan hanya bermakna selama menunggu undangan diterima.
        LocalDateTime invitationExpiresAt = status == AdminStatus.PENDING && user.getAdminInvitationToken() != null
                ? user.getAdminInvitationExpiresAt()
                : null;

        return AdminListItemResponseDTO.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .status(status)
                .self(user.getEmail().equalsIgnoreCase(currentAdminEmail))
                .createdAt(user.getCreatedAt())
                .invitationExpiresAt(invitationExpiresAt)
                .build();
    }

    /** "AKSI:email-target", dipotong agar muat di kolom activity (255). */
    private static String activity(String action, String targetEmail) {
        String text = action + ":" + targetEmail;
        return text.length() <= MAX_ACTIVITY_LENGTH ? text : text.substring(0, MAX_ACTIVITY_LENGTH);
    }
}
