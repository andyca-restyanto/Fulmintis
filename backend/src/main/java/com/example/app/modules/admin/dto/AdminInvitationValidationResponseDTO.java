// filepath: /backend/src/main/java/com/example/app/modules/admin/dto/AdminInvitationValidationResponseDTO.java
package com.example.app.modules.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/** Match: AdminInvitationValidation (FE). */
@Getter
@Builder
@AllArgsConstructor
public class AdminInvitationValidationResponseDTO {
    private boolean valid;
    private String email;
    private String name;
}
