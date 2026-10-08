// filepath: /backend/src/main/java/com/example/app/modules/testcase/service/TestCaseAiService.java
package com.example.app.modules.testcase.service;

import com.example.app.modules.testcase.dto.TestCaseAiCommitRequestDTO;
import com.example.app.modules.testcase.dto.TestCaseAiCommitResponseDTO;
import com.example.app.modules.testcase.dto.TestCaseAiCreatedDTO;
import com.example.app.modules.testcase.dto.TestCaseAiGenerateRequestDTO;
import com.example.app.modules.testcase.dto.TestCaseAiGenerationResponseDTO;
import com.example.app.modules.testcase.dto.TestCaseAiUsageResponseDTO;

import java.util.Optional;
import java.util.UUID;

/**
 * Generate test case dgn AI, DUA langkah: (1) generate menghasilkan DRAFT yang belum tersimpan; (2) setelah user mereview, commit
 * menyimpannya (semua atau tidak sama sekali). OWNER maupun COLLABORATOR boleh (sama seperti membuat test case manual).
 * Draft bersifat PRIBADI: hanya pemintanya yang boleh melihat dan menyimpan.
 */
public interface TestCaseAiService {

    TestCaseAiUsageResponseDTO getUsage(String userEmail, UUID projectId);

    /**
     * Memvalidasi lalu membuat job QUEUED dan KEMBALI SEGERA (proses AI di latar belakang; klien polling {@link #getGeneration}).
     * Penolakan: requirement kosong/terlalu panjang atau jumlah draft di luar batas tier (400), folder bukan milik project (404),
     * AI tidak aktif / circuit terbuka / batas global (503 umum), batas harian (429), sudah ada job berjalan (409).
     */
    TestCaseAiCreatedDTO submit(String userEmail, UUID projectId, TestCaseAiGenerateRequestDTO request);

    TestCaseAiGenerationResponseDTO getGeneration(String userEmail, UUID projectId, UUID generationId);

    /** Draft terakhir yang SUCCEEDED, belum disimpan, dan masih ada -- utk melanjutkan review setelah reload. */
    Optional<TestCaseAiGenerationResponseDTO> getPending(String userEmail, UUID projectId);

    /**
     * Menyimpan draft hasil review dalam SATU transaksi (semua atau tidak sama sekali). Setiap item divalidasi ulang di server; hanya
     * boleh SEKALI per generate (klik ganda / dua tab tidak menggandakan test case).
     */
    TestCaseAiCommitResponseDTO commit(String userEmail, UUID projectId, UUID generationId, TestCaseAiCommitRequestDTO request);
}
