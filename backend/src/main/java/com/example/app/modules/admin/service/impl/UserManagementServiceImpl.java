// filepath: /backend/src/main/java/com/example/app/modules/admin/service/impl/UserManagementServiceImpl.java
package com.example.app.modules.admin.service.impl;

import com.example.app.modules.admin.dto.UserListItemResponseDTO;
import com.example.app.modules.admin.dto.UserListResponseDTO;
import com.example.app.modules.admin.exception.InvalidUserQueryException;
import com.example.app.modules.admin.exception.InvalidUserTierException;
import com.example.app.modules.admin.exception.UserNotFoundException;
import com.example.app.modules.admin.service.UserManagementService;
import com.example.app.modules.admin.service.UserSpecifications;
import com.example.app.modules.auth.entity.User;
import com.example.app.modules.auth.repository.UserRepository;
import com.example.app.modules.usertype.UserTypeCode;
import com.example.app.modules.usertype.entity.UserType;
import com.example.app.modules.usertype.repository.UserTypeRepository;
import com.example.app.modules.usertype.service.UserTypeLookupService;
import com.example.app.shared.activitylog.ActivityLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserManagementServiceImpl implements UserManagementService {

    static final int DEFAULT_PAGE_SIZE = 10;
    static final int MAX_PAGE_SIZE = 50;
    static final int MAX_QUERY_LENGTH = 100;
    private static final int MAX_ACTIVITY_LENGTH = 255; // activity_log.activity VARCHAR(255)

    /** Tier yang boleh dipilih admin. ADMIN sengaja tidak termasuk: itu bukan tier berlangganan. */
    static final Set<String> ASSIGNABLE_TIERS =
            Set.of(UserTypeCode.FREE, UserTypeCode.VIP_MONTHLY, UserTypeCode.VIP_YEARLY);

    private final UserRepository userRepository;
    private final UserTypeRepository userTypeRepository;
    private final UserTypeLookupService userTypeLookupService; // datasource "master_data"
    private final ActivityLogService activityLogService;

    @Override
    @Transactional(readOnly = true)
    public UserListResponseDTO listUsers(int page, int size, String query, String tier) {
        int safePage = Math.max(page, 0);
        int safeSize = size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);

        String trimmedQuery = query == null ? "" : query.trim();
        if (trimmedQuery.length() > MAX_QUERY_LENGTH) {
            throw new InvalidUserQueryException(MAX_QUERY_LENGTH);
        }
        String trimmedTier = tier == null ? "" : tier.trim();
        if (!trimmedTier.isEmpty() && !ASSIGNABLE_TIERS.contains(trimmedTier)) {
            throw new InvalidUserTierException();
        }

        // Syarat ditambahkan hanya bila filternya terisi; semuanya digabung dengan AND.
        Specification<User> spec = UserSpecifications.notAdmin();
        if (!trimmedTier.isEmpty()) {
            spec = spec.and(UserSpecifications.tierIs(trimmedTier));
        }
        if (!trimmedQuery.isEmpty()) {
            spec = spec.and(UserSpecifications.matchesQuery(trimmedQuery));
        }

        // Terbaru dulu; id sebagai pembeda agar urutan stabil untuk waktu dibuat yang sama.
        Pageable pageable = PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<User> result = userRepository.findAll(spec, pageable);

        // In-memory join lintas database: satu query label untuk seluruh halaman (bukan per baris).
        List<String> codes = result.getContent().stream().map(User::getUserType).distinct().toList();
        Map<String, String> labels = codes.isEmpty() ? Map.of() : userTypeLookupService.getLabelsByCodes(codes);

        return UserListResponseDTO.builder()
                .items(result.getContent().stream().map(u -> toItem(u, labels.get(u.getUserType()))).toList())
                .page(safePage)
                .size(safeSize)
                .totalItems(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    @Override
    @Transactional
    public UserListItemResponseDTO updateTier(String actorEmail, UUID userId, String newTier) {
        String tier = newTier == null ? "" : newTier.trim();
        if (!ASSIGNABLE_TIERS.contains(tier)) {
            throw new InvalidUserTierException();
        }

        User target = userRepository.findById(userId)
                .filter(u -> !UserTypeCode.ADMIN.equals(u.getUserType()))
                .orElseThrow(UserNotFoundException::new);

        UserType tierType = userTypeRepository.findByCode(tier)
                .orElseThrow(() -> new IllegalStateException(
                        "user_type dengan code " + tier + " tidak ditemukan di database master_data. "
                                + "Pastikan UserTypeSeeder sudah jalan (restart backend)."));

        String oldTier = target.getUserType();
        if (tier.equals(oldTier)) {
            return toItem(target, tierType.getLabel()); // tier sama: idempoten
        }

        target.setUserType(tier);
        User saved = userRepository.save(target);

        activityLogService.log(actorEmail, activity(saved.getEmail(), oldTier, tier));
        return toItem(saved, tierType.getLabel());
    }

    private UserListItemResponseDTO toItem(User user, String label) {
        return UserListItemResponseDTO.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .userType(user.getUserType())
                .userTypeLabel(label != null ? label : user.getUserType())
                .verified(user.isVerified())
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
                .build();
    }

    /** "USER_TIER_CHANGED:email:LAMA->BARU", dipotong agar muat di kolom activity (255). */
    private static String activity(String targetEmail, String oldTier, String newTier) {
        String text = "USER_TIER_CHANGED:" + targetEmail + ":" + oldTier + "->" + newTier;
        return text.length() <= MAX_ACTIVITY_LENGTH ? text : text.substring(0, MAX_ACTIVITY_LENGTH);
    }
}
