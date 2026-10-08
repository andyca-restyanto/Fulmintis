// backend/src/main/java/com/example/app/modules/auth/dto/LoginResponseDTO.java
package com.example.app.modules.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class LoginResponseDTO {
    private String accessToken;
    private String tokenType;
    private long expiresIn; // detik
    private UUID id;
    private String email;
    private String name; // nullable -- lihat User.name (requirement #4)
}
