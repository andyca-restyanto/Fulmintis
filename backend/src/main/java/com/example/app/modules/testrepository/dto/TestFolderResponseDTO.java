// backend/src/main/java/com/example/app/modules/testrepository/dto/TestFolderResponseDTO.java
package com.example.app.modules.testrepository.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class TestFolderResponseDTO {
    private UUID id;
    private String folderName;
    private UUID projectId;

    // null = folder ini root (tidak punya parent), lihat requirement #2.
    private UUID parentId;

    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
