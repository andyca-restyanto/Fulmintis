// frontend/src/modules/testrun/services/testRun.service.ts
import apiClient from '@/shared/services/apiClient';
import type {
  AddTestCasesToTestRunRequest,
  CreateTestRunRequest,
  SyncTestRunCasesRequest,
  TestResultResponse,
  TestRunDetailResponse,
  TestRunResponse,
  UpdateTestResultRequest,
} from '../types/testRun.types';

export const testRunService = {
  /**
   * POST /api/projects/{projectId}/test-runs -- HANYA boleh OWNER project
   * (requirement #6). Backend balas 403 kalau bukan owner, 404 kalau ada
   * testCaseIds yang tidak valid utk project ini.
   */
  async createTestRun(projectId: string, payload: CreateTestRunRequest): Promise<TestRunDetailResponse> {
    const { data } = await apiClient.post<TestRunDetailResponse>(`/projects/${projectId}/test-runs`, payload);
    return data;
  },

  /** GET /api/projects/{projectId}/test-runs -- listing ringkas, boleh OWNER maupun COLLABORATOR. */
  async listTestRuns(projectId: string): Promise<TestRunResponse[]> {
    const { data } = await apiClient.get<TestRunResponse[]>(`/projects/${projectId}/test-runs`);
    return data;
  },

  /** GET /api/projects/{projectId}/test-runs/{testRunId} -- detail + daftar test case yang di-mapping. */
  async getTestRunDetail(projectId: string, testRunId: string): Promise<TestRunDetailResponse> {
    const { data } = await apiClient.get<TestRunDetailResponse>(`/projects/${projectId}/test-runs/${testRunId}`);
    return data;
  },

  /**
   * POST /api/projects/{projectId}/test-runs/{testRunId}/test-cases --
   * tambah test case ke run yang SUDAH ADA (requirement #2 & #7). Boleh
   * OWNER MAUPUN COLLABORATOR. testCaseId yang sudah pernah ke-mapping
   * di-skip diam-diam oleh backend (idempotent).
   */
  async addTestCasesToRun(
    projectId: string,
    testRunId: string,
    payload: AddTestCasesToTestRunRequest
  ): Promise<TestRunDetailResponse> {
    const { data } = await apiClient.post<TestRunDetailResponse>(
      `/projects/${projectId}/test-runs/${testRunId}/test-cases`,
      payload
    );
    return data;
  },

  /**
   * PUT /api/projects/{projectId}/test-runs/{testRunId}/test-cases -- simpan
   * checklist Manage Cases: kirim daftar AKHIR testCaseIds. Yang baru
   * tercentang ditambah, yang di-uncheck dilepas (hasil eksekusi & evidence
   * ikut terhapus permanen), semuanya 1 transaksi di backend. Boleh OWNER
   * MAUPUN COLLABORATOR. testCaseIds boleh kosong. Backend balas 404 kalau
   * ada id BARU yang tidak valid/archived -- dalam kasus itu TIDAK ADA
   * perubahan yang tersimpan.
   */
  async syncTestCasesInRun(
    projectId: string,
    testRunId: string,
    payload: SyncTestRunCasesRequest
  ): Promise<TestRunDetailResponse> {
    const { data } = await apiClient.put<TestRunDetailResponse>(
      `/projects/${projectId}/test-runs/${testRunId}/test-cases`,
      payload
    );
    return data;
  },

  /**
   * DELETE /api/projects/{projectId}/test-runs/{testRunId}/test-cases/{testCaseId}
   * -- lepas 1 test case dari test run (requirement tambahan #1 & #3).
   * Boleh OWNER MAUPUN COLLABORATOR (BUKAN owner-only seperti
   * deleteTestRun). Hasil eksekusi & evidence test case itu di run ini
   * ikut terhapus permanen. Backend balas 404 kalau test case tsb memang
   * tidak sedang ke-mapping ke run ini; response-nya detail test run
   * terbaru (status run bisa ikut berubah, mis. jadi FINISHED / PENDING).
   */
  async removeTestCaseFromRun(
    projectId: string,
    testRunId: string,
    testCaseId: string
  ): Promise<TestRunDetailResponse> {
    const { data } = await apiClient.delete<TestRunDetailResponse>(
      `/projects/${projectId}/test-runs/${testRunId}/test-cases/${testCaseId}`
    );
    return data;
  },

  /**
   * DELETE /api/projects/{projectId}/test-runs/{testRunId} -- hapus
   * permanen test run + semua test result-nya. HANYA boleh OWNER project.
   */
  async deleteTestRun(projectId: string, testRunId: string): Promise<void> {
    await apiClient.delete(`/projects/${projectId}/test-runs/${testRunId}`);
  },

  /**
   * PATCH .../test-runs/{testRunId}/test-results/{testResultId} -- catat
   * hasil eksekusi 1 test case (status + comment). Boleh OWNER maupun
   * COLLABORATOR.
   */
  async updateTestResult(
    projectId: string,
    testRunId: string,
    testResultId: string,
    payload: UpdateTestResultRequest
  ): Promise<TestResultResponse> {
    const { data } = await apiClient.patch<TestResultResponse>(
      `/projects/${projectId}/test-runs/${testRunId}/test-results/${testResultId}`,
      payload
    );
    return data;
  },

  /**
   * POST .../test-results/{testResultId}/evidence -- upload/replace bukti
   * (image/video), multipart/form-data field "file". Balas 400 kalau file
   * bukan png/jpg/gif/webp/mp4/mov/webm (dicek dari ISI file) atau > 50MB.
   */
  async uploadEvidence(
    projectId: string,
    testRunId: string,
    testResultId: string,
    file: File
  ): Promise<TestResultResponse> {
    const formData = new FormData();
    formData.append('file', file);

    const { data } = await apiClient.post<TestResultResponse>(
      `/projects/${projectId}/test-runs/${testRunId}/test-results/${testResultId}/evidence`,
      formData,
      // File evidence bisa sampai 50MB (video) -- timeout default apiClient
      // (15 detik, lihat shared/services/apiClient.ts) kepotong duluan
      // sebelum upload gede selesai di koneksi lambat, jadi di-override
      // lebih panjang khusus di sini.
      { headers: { 'Content-Type': 'multipart/form-data' }, timeout: 120000 }
    );
    return data;
  },
  /**
   * evidenceUrl dari TestResultResponse (mis.
   * "/api/projects/{id}/test-runs/{id}/test-results/{id}/evidence") adalah
   * endpoint PROTECTED (wajib JWT) -- makanya TIDAK BISA dipasang langsung
   * sbg <img src>/<video src> (browser tidak nempelin header Authorization
   * utk request img/video biasa). Method ini fetch file-nya lewat apiClient
   * (otomatis kebawa header JWT) dan balas Blob-nya; tipe MIME-nya (blob.type)
   * dipakai caller utk memutuskan boleh ditampilkan atau tidak.
   * <p>
   * Caller yang membuat object URL dari blob ini WAJIB memanggil
   * URL.revokeObjectURL() setelah selesai (lihat EvidenceViewerModal.vue).
   */
  async fetchEvidenceBlob(evidenceUrl: string): Promise<Blob> {
    // apiClient.baseURL SENDIRI sudah include "/api" (lihat
    // shared/services/apiClient.ts) -- prefix "/api" di evidenceUrl di-strip
    // dulu di sini supaya path yang dipanggil tidak dobel jadi "/api/api/...".
    const path = evidenceUrl.startsWith('/api') ? evidenceUrl.slice(4) : evidenceUrl;
    const response = await apiClient.get<Blob>(path, { responseType: 'blob' });
    return response.data;
  },
};
