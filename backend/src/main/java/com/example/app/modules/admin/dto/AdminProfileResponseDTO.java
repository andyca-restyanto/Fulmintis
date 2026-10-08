// filepath: /backend/src/main/java/com/example/app/modules/admin/dto/AdminProfileResponseDTO.java
package com.example.app.modules.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/** Match: AdminProfile (FE). */
@Getter
@Builder
@AllArgsConstructor
public class AdminProfileResponseDTO {
    private UUID id;
    private String email;
    private String name;
    private String role;
}
