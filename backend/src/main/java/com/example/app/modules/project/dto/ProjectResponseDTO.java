// backend/src/main/java/com/example/app/modules/project/dto/ProjectResponseDTO.java
package com.example.app.modules.project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class ProjectResponseDTO {
    private UUID id;
    private String projectName;
    private String description;
    private LocalDateTime createdAt;
    private String ownerEmail;
    private String projectTeam; // role user yang create ini, selalu "OWNER"
}
