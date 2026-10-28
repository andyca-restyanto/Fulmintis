// frontend/src/modules/testcase/services/testCaseAi.service.ts
import apiClient from '@/shared/services/apiClient';
import type {
  CommitTestCasesRequest,
  CommitTestCasesResult,
  GenerateTestCasesRequest,
  TestCaseAiGeneration,
  TestCaseAiGenerationCreated,
  TestCaseAiUsage,
} from '../types/testCaseAi.types';

const base = (projectId: string) => `/projects/${projectId}/test-cases/ai-generation`;

/**
 * Semua method melempar error axios kalau gagal -- caller WAJIB try/catch dan memetakan pesannya lewat describeAiError
 * (shared/utils/aiErrors.ts), supaya kegagalan sisi AI selalu tampil sbg pesan umum.
 */
export const testCaseAiService = {
  /** GET /usage -- semua member. aiEnabled=false = fitur belum diaktifkan. */
  async getUsage(projectId: string): Promise<TestCaseAiUsage> {
    const { data } = await apiClient.get<TestCaseAiUsage>(`${base(projectId)}/usage`);
    return data;
  },

  /**
   * POST / -- 202: job dibuat, prosesnya di latar belakang (polling getGeneration). Penolakan: 400 INVALID_REQUEST /
   * REQUIREMENT_TOO_LONG / TOO_MANY_DRAFTS, 404 folder, 409 GENERATION_IN_PROGRESS, 429 AI_PLAN_LIMIT_REACHED, 503 AI_UNAVAILABLE.
   */
  async generate(projectId: string, payload: GenerateTestCasesRequest): Promise<TestCaseAiGenerationCreated> {
    const { data } = await apiClient.post<TestCaseAiGenerationCreated>(base(projectId), payload);
    return data;
  },

  /** GET /{id} -- drafts hanya terisi kalau SUCCEEDED dan belum disimpan. Milik user lain / project lain -> 404. */
  async getGeneration(projectId: string, generationId: string): Promise<TestCaseAiGeneration> {
    const { data } = await apiClient.get<TestCaseAiGeneration>(`${base(projectId)}/${generationId}`);
    return data;
  },

  /** GET /pending -- draft terakhir yang belum disimpan (utk melanjutkan review setelah reload). 204 -> null. */
  async getPending(projectId: string): Promise<TestCaseAiGeneration | null> {
    const response = await apiClient.get<TestCaseAiGeneration | ''>(`${base(projectId)}/pending`);
    if (response.status === 204 || !response.data) {
      return null;
    }
    return response.data;
  },

  /**
   * POST /{id}/commit -- 201. Semua atau tidak sama sekali; hanya SEKALI per generate. Penolakan: 400 INVALID_DRAFTS (dengan
   * errors per item), 400 NO_DRAFTS_SELECTED / TOO_MANY_DRAFTS, 404, 409 GENERATION_ALREADY_COMMITTED / DRAFT_EXPIRED / GENERATION_NOT_READY.
   */
  async commit(projectId: string, generationId: string, payload: CommitTestCasesRequest): Promise<CommitTestCasesResult> {
    const { data } = await apiClient.post<CommitTestCasesResult>(`${base(projectId)}/${generationId}/commit`, payload);
    return data;
  },
};
