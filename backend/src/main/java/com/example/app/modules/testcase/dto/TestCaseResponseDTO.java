// backend/src/main/java/com/example/app/modules/testcase/dto/TestCaseResponseDTO.java
package com.example.app.modules.testcase.dto;

import com.example.app.modules.testcase.TestCasePriority;
import com.example.app.modules.testcase.TestCaseScenarioType;
import com.example.app.modules.testcase.TestCaseStatus;
import com.example.app.modules.testcase.TestCaseType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class TestCaseResponseDTO {
    private UUID id;
    private UUID projectId;
    private UUID folderId;
    private String title;
    private TestCasePriority priority;
    private TestCaseType type;
    private TestCaseScenarioType scenarioType;
    private String description;
    private String objective;
    private String precondition;
    private String testStep;
    private String expectedResult;

    // Requirement tambahan #2 & #4 -- status ACTIVE|ARCHIVED, dan kapan/siapa
    // yang men-delete (archive). archivedAt/archivedBy selalu null selama
    // status = ACTIVE. Endpoint GET biasa (non-archived) tidak akan pernah
    // mengembalikan status = ARCHIVED ke COLLABORATOR (lihat
    // TestCaseServiceImpl), field ini tetap disertakan supaya FE OWNER bisa
    // tampilkan badge "Archived" di halaman khusus test case archived.
    private TestCaseStatus status;
    private LocalDateTime archivedAt;
    private String archivedBy;

    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
