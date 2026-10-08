// backend/src/main/java/com/example/app/modules/testcase/service/TestCaseService.java
package com.example.app.modules.testcase.service;

import com.example.app.modules.testcase.dto.CreateTestCaseRequestDTO;
import com.example.app.modules.testcase.dto.TestCaseResponseDTO;
import com.example.app.modules.testcase.dto.TestCaseSearchCriteria;
import com.example.app.modules.testcase.dto.UpdateTestCaseRequestDTO;

import java.util.List;
import java.util.UUID;

public interface TestCaseService {

    /**
     * Buat test case baru di dalam 1 folder.
     *
     * @throws com.example.app.modules.project.exception.ProjectNotFoundException
     *         kalau project tidak ada / user bukan member (404).
     * @throws com.example.app.modules.testrepository.exception.TestFolderNotFoundException
     *         kalau folderId tidak valid utk project ini (404) -- requirement #2.
     *         Requirement #4 (OWNER & COLLABORATOR boleh create) otomatis
     *         terpenuhi begitu lolos cek membership project, karena cuma 2
     *         role itu yang ada di sistem ini (lihat ProjectTeamCode).
     */
    TestCaseResponseDTO createTestCase(String userEmail, UUID projectId, CreateTestCaseRequestDTO request);

    /**
     * Search & filter test case dalam 1 project (requirement #6). Semua
     * kriteria di TestCaseSearchCriteria opsional -- panggil dengan semua
     * field null utk list semua test case di project ini. SELALU hanya
     * mengembalikan test case berstatus ACTIVE -- test case ARCHIVED tidak
     * pernah muncul di sini (requirement #4), baik utk OWNER maupun
     * COLLABORATOR. Utk lihat test case archived, OWNER pakai
     * {@link #listArchivedTestCases}.
     */
    List<TestCaseResponseDTO> searchTestCases(String userEmail, UUID projectId, TestCaseSearchCriteria criteria);

    /**
     * Ambil 1 test case by id -- dipakai a.l. utk pre-fill form "Edit Test
     * Case" di FE.
     *
     * @throws com.example.app.modules.project.exception.ProjectNotFoundException
     *         kalau project tidak ada / user bukan member (404).
     * @throws com.example.app.modules.testcase.exception.TestCaseNotFoundException
     *         kalau testCaseId tidak ada / bukan milik project ini, ATAU
     *         (requirement #4) test case-nya ARCHIVED dan user yang minta
     *         BUKAN OWNER project (404, anti-enumeration).
     */
    TestCaseResponseDTO getTestCaseById(String userEmail, UUID projectId, UUID testCaseId);

    /**
     * Edit test case yang sudah ada (requirement #1). Boleh dipanggil OWNER
     * maupun COLLABORATOR (sama seperti create), TAPI kalau test case-nya
     * ARCHIVED dan yang minta COLLABORATOR -> ditolak seolah tidak ada
     * (requirement #4, konsisten dgn getTestCaseById).
     *
     * @throws com.example.app.modules.project.exception.ProjectNotFoundException
     *         kalau project tidak ada / user bukan member (404).
     * @throws com.example.app.modules.testcase.exception.TestCaseNotFoundException
     *         kalau testCaseId tidak valid utk project ini / disembunyikan
     *         dari COLLABORATOR karena archived (404).
     * @throws com.example.app.modules.testrepository.exception.TestFolderNotFoundException
     *         kalau folderId baru yang dikirim tidak valid utk project ini (404).
     */
    TestCaseResponseDTO updateTestCase(String userEmail, UUID projectId, UUID testCaseId, UpdateTestCaseRequestDTO request);

    /**
     * "Delete" test case (requirement #1 & #2) -- SOFT DELETE: flag status
     * jadi ARCHIVED (+ catat archivedAt/archivedBy), row TIDAK dihapus dari
     * database. HANYA boleh OWNER project (requirement #3).
     *
     * @throws com.example.app.modules.project.exception.ProjectNotFoundException
     *         kalau project tidak ada / user bukan member (404).
     * @throws com.example.app.modules.project.exception.ProjectAccessForbiddenException
     *         kalau user yang minta BUKAN OWNER project (403, requirement #3)
     *         -- 403 bukan 404 krn user sudah terbukti member yg sah.
     * @throws com.example.app.modules.testcase.exception.TestCaseNotFoundException
     *         kalau testCaseId tidak ada / bukan milik project ini (404).
     * @throws com.example.app.modules.testcase.exception.TestCaseAlreadyArchivedException
     *         kalau test case-nya SUDAH berstatus ARCHIVED sebelumnya (409).
     */
    void archiveTestCase(String userEmail, UUID projectId, UUID testCaseId);

    /**
     * List test case yang berstatus ARCHIVED dalam 1 project (requirement
     * #4) -- HANYA boleh diakses OWNER project, filter opsional yang sama
     * dgn {@link #searchTestCases}.
     *
     * @throws com.example.app.modules.project.exception.ProjectNotFoundException
     *         kalau project tidak ada / user bukan member (404).
     * @throws com.example.app.modules.project.exception.ProjectAccessForbiddenException
     *         kalau user yang minta BUKAN OWNER project (403, requirement #4)
     *         -- COLLABORATOR tahu endpoint ini ada tapi ditolak aksesnya.
     */
    List<TestCaseResponseDTO> listArchivedTestCases(String userEmail, UUID projectId, TestCaseSearchCriteria criteria);
}
