// backend/src/main/java/com/example/app/modules/testrepository/controller/TestFolderController.java
package com.example.app.modules.testrepository.controller;

import com.example.app.modules.testrepository.dto.CreateTestFolderRequestDTO;
import com.example.app.modules.testrepository.dto.TestFolderResponseDTO;
import com.example.app.modules.testrepository.service.TestFolderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * PROTECTED (wajib JWT valid) -- nested di bawah /api/projects/{projectId},
 * tidak di-permitAll() di SecurityConfig jadi otomatis kena
 * anyRequest().authenticated().
 */
@RestController
@RequestMapping("/api/projects/{projectId}/test-folders")
@RequiredArgsConstructor
public class TestFolderController {

    private final TestFolderService testFolderService;

    /**
     * Flow FE: user di menu Test Repository klik "+ / Create Test Suite" ->
     * isi folder name -> pilih parent folder (opsional) -> Save.
     * <p>
     * Balas 403 kalau user BUKAN owner project ini (requirement #3), 404
     * kalau project tidak ada/user bukan member, ATAU parentId yang dikirim
     * tidak valid utk project ini.
     */
    @PostMapping
    public ResponseEntity<TestFolderResponseDTO> createFolder(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateTestFolderRequestDTO request,
            Authentication authentication
    ) {
        String email = authentication.getName(); // di-set oleh JwtAuthenticationFilter dari subject token
        TestFolderResponseDTO response = testFolderService.createFolder(email, projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Daftar semua folder di project ini (flat list) -- dipakai FE utk
     * render tree menu Test Repository, dan sebagai sumber data dropdown
     * "Choose parent folder" saat create folder baru. Bisa diakses OWNER
     * maupun COLLABORATOR (beda dengan createFolder() yang OWNER-only).
     */
    @GetMapping
    public List<TestFolderResponseDTO> listFolders(
            @PathVariable UUID projectId,
            Authentication authentication
    ) {
        String email = authentication.getName();
        return testFolderService.listFolders(email, projectId);
    }
}
