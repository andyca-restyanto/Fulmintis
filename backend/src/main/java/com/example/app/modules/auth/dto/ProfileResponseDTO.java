// backend/src/main/java/com/example/app/modules/auth/dto/ProfileResponseDTO.java
package com.example.app.modules.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class ProfileResponseDTO {
    private UUID id;
    private String email;
    private String name; // nullable -- lihat User.name
}
