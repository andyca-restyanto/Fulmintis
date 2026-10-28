// backend/src/main/java/com/example/app/modules/project/dto/ProjectListItemResponseDTO.java
package com.example.app.modules.project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response utk GET /api/projects -- daftar project yang user login jadi
 * member-nya (OWNER maupun COLLABORATOR), dipakai frontend utk render
 * daftar project sebelum user klik salah satu (lihat ProjectDetailResponseDTO
 * utk response setelah project diklik).
 */
@Getter
@Builder
@AllArgsConstructor
public class ProjectListItemResponseDTO {
    private UUID id;
    private String projectName;
    private String description;
    private LocalDateTime createdAt;

    // Role user YANG SEDANG LOGIN di project ini (bukan role owner project),
    // contoh: "OWNER" kalau dia yang create, "COLLABORATOR" kalau diundang.
    private String myProjectTeam;
}
