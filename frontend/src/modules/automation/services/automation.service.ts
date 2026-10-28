// frontend/src/modules/automation/services/automation.service.ts
import apiClient from '@/shared/services/apiClient';
import type {
  AutomationGeneration,
  AutomationGenerationCreated,
  AutomationGenerationSummary,
  AutomationOptions,
  AutomationSetup,
  AutomationUsage,
  SaveAutomationSetupRequest,
} from '../types/automation.types';

const base = (projectId: string) => `/projects/${projectId}/automation`;

/**
 * Semua method melempar error axios kalau gagal -- caller WAJIB try/catch dan memetakan pesannya lewat
 * describeAutomationError (utils/automationErrors.ts), supaya kegagalan sisi AI selalu tampil sbg pesan umum.
 */
export const automationService = {
  /** GET /options -- framework, bahasa, pola, dan matriks kompatibilitas (satu sumber kebenaran). */
  async getOptions(projectId: string): Promise<AutomationOptions> {
    const { data } = await apiClient.get<AutomationOptions>(`${base(projectId)}/options`);
    return data;
  },

  /** GET /setup -- semua member. Belum diatur -> { configured: false }. */
  async getSetup(projectId: string): Promise<AutomationSetup> {
    const { data } = await apiClient.get<AutomationSetup>(`${base(projectId)}/setup`);
    return data;
  },

  /** PUT /setup -- OWNER. Upsert: maksimal 1 setup per project. 400 UNSUPPORTED_COMBINATION utk kombinasi tidak valid. */
  async saveSetup(projectId: string, payload: SaveAutomationSetupRequest): Promise<AutomationSetup> {
    const { data } = await apiClient.put<AutomationSetup>(`${base(projectId)}/setup`, payload);
    return data;
  },

  /** DELETE /setup -- OWNER. 204; idempoten. */
  async deleteSetup(projectId: string): Promise<void> {
    await apiClient.delete(`${base(projectId)}/setup`);
  },

  /** GET /usage -- tier, batas, pemakaian hari ini, dan aiEnabled. */
  async getUsage(projectId: string): Promise<AutomationUsage> {
    const { data } = await apiClient.get<AutomationUsage>(`${base(projectId)}/usage`);
    return data;
  },

  /**
   * POST /generations -- 202: job dibuat dan DIPROSES DI LATAR BELAKANG (bisa puluhan detik). Klien lalu polling
   * getGeneration(id) sampai SUCCEEDED/FAILED (lihat utils/automationPolling.ts).
   */
  async generate(projectId: string, testCaseIds: string[]): Promise<AutomationGenerationCreated> {
    const { data } = await apiClient.post<AutomationGenerationCreated>(`${base(projectId)}/generations`, { testCaseIds });
    return data;
  },

  /** GET /generations -- riwayat project (terbaru dulu, maks 20), tanpa isi berkas. */
  async listGenerations(projectId: string): Promise<AutomationGenerationSummary[]> {
    const { data } = await apiClient.get<AutomationGenerationSummary[]>(`${base(projectId)}/generations`);
    return data;
  },

  /** GET /generations/{id} -- detail; `files` terisi hanya kalau SUCCEEDED. */
  async getGeneration(projectId: string, generationId: string): Promise<AutomationGeneration> {
    const { data } = await apiClient.get<AutomationGeneration>(`${base(projectId)}/generations/${generationId}`);
    return data;
  },

  /**
   * GET /generations/{id}/download -- zip hasil (hanya SUCCEEDED; selain itu 409). Dibalas sbg Blob karena endpoint
   * butuh JWT (tidak bisa <a href> biasa). Caller memicu unduhan lalu revokeObjectURL.
   */
  async downloadGeneration(projectId: string, generationId: string): Promise<Blob> {
    const { data } = await apiClient.get<Blob>(`${base(projectId)}/generations/${generationId}/download`, {
      responseType: 'blob',
    });
    return data;
  },
};
