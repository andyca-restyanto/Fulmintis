// backend/src/main/java/com/example/app/modules/project/dto/AddProjectCollaboratorRequestDTO.java
package com.example.app.modules.project.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddProjectCollaboratorRequestDTO {

    @NotBlank(message = "Email wajib diisi")
    @Email(message = "Format email tidak valid")
    private String email;

    // "OWNER" atau "COLLABORATOR" -- BUKAN Java enum (sama pola dgn
    // ProjectCollaboration.projectTeam), karena source of truth-nya tabel
    // project_team di database master_data. Divalidasi ke situ di
    // ProjectCollaboratorServiceImpl, bukan di level DTO.
    @NotBlank(message = "Role wajib dipilih")
    private String projectTeam;
}
