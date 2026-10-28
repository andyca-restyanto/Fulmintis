// filepath: /backend/src/main/java/com/example/app/modules/project/dto/UpdateProjectCollaboratorRequestDTO.java
package com.example.app.modules.project.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateProjectCollaboratorRequestDTO {

    // "OWNER" atau "COLLABORATOR" -- divalidasi ke tabel project_team
    // (database master_data) di ProjectCollaboratorServiceImpl, sama seperti
    // AddProjectCollaboratorRequestDTO.
    @NotBlank(message = "Role wajib dipilih")
    private String projectTeam;
}
