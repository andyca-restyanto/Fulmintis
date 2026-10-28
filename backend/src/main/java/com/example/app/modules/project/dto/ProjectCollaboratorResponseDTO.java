// backend/src/main/java/com/example/app/modules/project/dto/ProjectCollaboratorResponseDTO.java
package com.example.app.modules.project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class ProjectCollaboratorResponseDTO {
    private Long id;
    private UUID userId;
    private String email;
    private String name; // nullable -- lihat User.name (requirement #4)
    private String projectTeam; // "OWNER" | "COLLABORATOR"
    private LocalDateTime createdAt;
}
