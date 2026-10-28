// frontend/src/modules/testcase/services/testCase.service.ts
import apiClient from '@/shared/services/apiClient';
import type {
  CreateTestCaseRequest,
  TestCaseResponse,
  TestCaseSearchParams,
  UpdateTestCaseRequest,
} from '../types/testCase.types';
import type { TestCaseImportResult } from '../types/testCaseImport.types';

export const testCaseService = {
  /**
   * POST /api/projects/{projectId}/test-cases -- boleh OWNER maupun
   * COLLABORATOR (requirement #4). Backend balas 404 kalau folderId tidak
   * valid utk project ini (requirement #2) -- caller wajib try/catch.
   */
  async createTestCase(projectId: string, payload: CreateTestCaseRequest): Promise<TestCaseResponse> {
    const { data } = await apiClient.post<TestCaseResponse>(`/projects/${projectId}/test-cases`, payload);
    return data;
  },

  /**
   * GET /api/projects/{projectId}/test-cases -- search & filter (requirement
   * #6). Semua param opsional; axios otomatis buang param yang undefined
   * dari query string, jadi panggil tanpa argumen kedua = list semua test
   * case ACTIVE di project ini. SELALU status=ACTIVE dari sisi backend --
   * test case archived TIDAK PERNAH nyelip ke sini (requirement tambahan #4).
   */
  async searchTestCases(projectId: string, params: TestCaseSearchParams = {}): Promise<TestCaseResponse[]> {
    const { data } = await apiClient.get<TestCaseResponse[]>(`/projects/${projectId}/test-cases`, { params });
    return data;
  },

  /**
   * GET /api/projects/{projectId}/test-cases/{testCaseId} -- detail 1 test
   * case. Backend balas 404 kalau id tidak valid utk project ini, ATAU
   * (requirement tambahan #4) test case-nya ARCHIVED dan yang login
   * COLLABORATOR (anti-enumeration, disamakan dgn "tidak ada").
   */
  async getTestCase(projectId: string, testCaseId: string): Promise<TestCaseResponse> {
    const { data } = await apiClient.get<TestCaseResponse>(`/projects/${projectId}/test-cases/${testCaseId}`);
    return data;
  },

  /**
   * PUT /api/projects/{projectId}/test-cases/{testCaseId} -- edit test case
   * (requirement tambahan #1). Boleh OWNER maupun COLLABORATOR, sama seperti
   * create.
   */
  async updateTestCase(
    projectId: string,
    testCaseId: string,
    payload: UpdateTestCaseRequest
  ): Promise<TestCaseResponse> {
    const { data } = await apiClient.put<TestCaseResponse>(
      `/projects/${projectId}/test-cases/${testCaseId}`,
      payload
    );
    return data;
  },

  /**
   * DELETE /api/projects/{projectId}/test-cases/{testCaseId} -- "delete"
   * test case (requirement tambahan #1 & #2): backend SOFT DELETE (flag
   * status jadi ARCHIVED), bukan hapus permanen. HANYA boleh OWNER
   * (requirement #3) -- backend balas 403 kalau bukan owner, 409 kalau
   * test case-nya sudah archived sebelumnya. Balasan sukses 204 No Content
   * (tidak ada body), makanya return type-nya void.
   */
  async archiveTestCase(projectId: string, testCaseId: string): Promise<void> {
    await apiClient.delete(`/projects/${projectId}/test-cases/${testCaseId}`);
  },

  /**
   * GET /api/projects/{projectId}/test-cases/archived -- daftar test case
   * yang sudah di-delete/archive (requirement tambahan #4). HANYA boleh
   * dipanggil OWNER project -- backend balas 403 kalau COLLABORATOR yang
   * coba akses. Filter param SAMA seperti searchTestCases, semua opsional.
   */
  async listArchivedTestCases(projectId: string, params: TestCaseSearchParams = {}): Promise<TestCaseResponse[]> {
    const { data } = await apiClient.get<TestCaseResponse[]>(`/projects/${projectId}/test-cases/archived`, {
      params,
    });
    return data;
  },

  /**
   * GET /api/projects/{projectId}/test-cases/import/template -- file template .xlsx
   * (header, baris contoh, dropdown, petunjuk). Dibalas sbg Blob; caller yang
   * memicu unduhan (lihat ImportTestCasesModal).
   */
  async downloadImportTemplate(projectId: string): Promise<Blob> {
    const { data } = await apiClient.get<Blob>(`/projects/${projectId}/test-cases/import/template`, {
      responseType: 'blob',
    });
    return data;
  },

  /**
   * POST /api/projects/{projectId}/test-cases/import -- multipart, field "file".
   * - dryRun=true : hanya memeriksa & memetakan (TIDAK menyimpan), baris bermasalah ada di result.errors (200).
   * - dryRun=false: menyimpan SEMUA baris, atau tidak sama sekali. Ada baris bermasalah -> axios melempar
   *   error 400 yang err.response.data-nya = TestCaseImportResult (lihat extractImportFailure).
   * File tidak bisa diproses (bukan .xlsx, kolom wajib tidak ada, > 500 baris, > 5 MB) -> 400 { message }.
   * Caller wajib try/catch.
   */
  async importTestCases(
    projectId: string,
    folderId: string,
    file: File,
    dryRun: boolean
  ): Promise<TestCaseImportResult> {
    const formData = new FormData();
    formData.append('file', file);

    const { data } = await apiClient.post<TestCaseImportResult>(
      `/projects/${projectId}/test-cases/import`,
      formData,
      // dryRun dikirim EKSPLISIT di kedua panggilan (default backend true, tapi jangan bergantung padanya).
      // Parsing file di server lebih lama dari request biasa -> timeout dilonggarkan dari default 15 detik.
      { params: { folderId, dryRun }, headers: { 'Content-Type': 'multipart/form-data' }, timeout: 60000 }
    );
    return data;
  },
};
