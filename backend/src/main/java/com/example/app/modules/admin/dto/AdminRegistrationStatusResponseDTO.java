// filepath: /backend/src/main/java/com/example/app/modules/admin/dto/AdminRegistrationStatusResponseDTO.java
package com.example.app.modules.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/** Match: AdminRegistrationStatus (FE). */
@Getter
@Builder
@AllArgsConstructor
public class AdminRegistrationStatusResponseDTO {
    private boolean open;
    private boolean bootstrapCodeRequired;
}
