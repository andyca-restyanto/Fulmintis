// filepath: /backend/src/main/java/com/example/app/modules/admin/service/impl/AdminManagementServiceImpl.java
package com.example.app.modules.admin.service.impl;

import com.example.app.modules.admin.dto.AdminInviteRequestDTO;
import com.example.app.modules.admin.dto.AdminInviteResponseDTO;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminManagementServiceImpl implements AdminManagementService {

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
}
