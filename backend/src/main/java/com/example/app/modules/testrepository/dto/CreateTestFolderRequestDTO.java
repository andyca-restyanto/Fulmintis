// backend/src/main/java/com/example/app/modules/testrepository/dto/CreateTestFolderRequestDTO.java
package com.example.app.modules.testrepository.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CreateTestFolderRequestDTO {

    @NotBlank(message = "Nama folder wajib diisi")
    @Size(max = 255, message = "Nama folder maksimal 255 karakter")
    private String folderName;

    // Opsional -- kalau tidak diisi (null), folder jadi root (requirement #2).
    // Kalau diisi, WAJIB folder lain yang project_id-nya sama dengan project
    // tujuan (divalidasi di TestFolderServiceImpl).
    private UUID parentId;
}
