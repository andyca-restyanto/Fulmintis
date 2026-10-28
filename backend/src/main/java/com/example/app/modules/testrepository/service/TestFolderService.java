// backend/src/main/java/com/example/app/modules/testrepository/service/TestFolderService.java
package com.example.app.modules.testrepository.service;

import com.example.app.modules.testrepository.dto.CreateTestFolderRequestDTO;
import com.example.app.modules.testrepository.dto.TestFolderResponseDTO;

import java.util.List;
import java.util.UUID;

public interface TestFolderService {

    /**
     * Buat test suite/folder baru di dalam 1 project (menu Test Repository).
     *
     * @throws com.example.app.modules.project.exception.ProjectNotFoundException
     *         kalau project tidak ada / user bukan member (404).
     * @throws com.example.app.modules.project.exception.ProjectAccessForbiddenException
     *         kalau user member tapi BUKAN OWNER project ini -- requirement #3 (403).
     * @throws com.example.app.modules.testrepository.exception.TestFolderNotFoundException
     *         kalau parentId dikirim tapi tidak valid utk project ini (404).
     */
    TestFolderResponseDTO createFolder(String userEmail, UUID projectId, CreateTestFolderRequestDTO request);

    /**
     * Daftar semua folder di 1 project (flat list, urut dari yang paling
     * lama dibuat) -- dipakai FE utk render struktur tree Test Repository,
     * termasuk dropdown "Choose parent folder" saat create folder baru.
     * Boleh diakses SEMUA member project (OWNER maupun COLLABORATOR), beda
     * dengan createFolder() yang dibatasi OWNER saja.
     */
    List<TestFolderResponseDTO> listFolders(String userEmail, UUID projectId);
}
