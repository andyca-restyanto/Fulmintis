// backend/src/main/java/com/example/app/modules/project/dto/CreateProjectRequestDTO.java
package com.example.app.modules.project.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateProjectRequestDTO {

    @NotBlank(message = "Nama project wajib diisi")
    private String projectName;

    // Deskripsi opsional -- tidak wajib diisi
    private String description;
}
