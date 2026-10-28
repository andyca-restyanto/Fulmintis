// filepath: /backend/src/main/java/com/example/app/modules/testcase/dto/TestCaseAiGenerationResponseDTO.java
package com.example.app.modules.testcase.dto;

import com.example.app.modules.testcase.TestCaseAiGenerationStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Detail satu generate. Match dgn TestCaseAiGeneration (FE).
 * {@code drafts} terisi hanya kalau SUCCEEDED dan belum di-commit/kedaluwarsa; selain itu daftar kosong.
 * {@code requestedCount} vs {@code draftCount} + {@code truncated}: AI bisa mengembalikan lebih sedikit dari yang diminta
 * (mis. keluaran terpotong -- hanya draft yang lengkap diselamatkan).
 */
@Getter
@Builder
public class TestCaseAiGenerationResponseDTO {
    private UUID id;
    private TestCaseAiGenerationStatus status;
    private UUID folderId;
    private String folderName;
    private int requestedCount;
    private int draftCount;
    private boolean truncated;
    private boolean committed;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
    private List<TestCaseDraftDTO> drafts;
    private String errorCode;
    private String errorMessage;
}
