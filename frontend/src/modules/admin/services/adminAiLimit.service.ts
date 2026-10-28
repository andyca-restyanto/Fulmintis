// filepath: /frontend/src/modules/admin/services/adminAiLimit.service.ts
import apiClient from '@/shared/services/apiClient';
import type { AiLimits, AiLimitsUpdateRequest } from '../types/admin-ai-limits.types';

export const adminAiLimitService = {
  /** PROTECTED (ROLE_ADMIN) -- batas AI efektif, nilai bawaan, dan sumber tiap nilai. */
  async get(): Promise<AiLimits> {
    const { data } = await apiClient.get<AiLimits>('/admin/ai-limits');
    return data;
  },

  /** PROTECTED (ROLE_ADMIN) -- simpan semua nilai. 400 bila ada nilai tidak valid (tidak ada yang tersimpan sebagian). */
  async update(payload: AiLimitsUpdateRequest): Promise<AiLimits> {
    const { data } = await apiClient.put<AiLimits>('/admin/ai-limits', payload);
    return data;
  },

  /** PROTECTED (ROLE_ADMIN) -- kembalikan ke nilai bawaan. Tanpa keys = semua. */
  async reset(keys?: string[]): Promise<AiLimits> {
    const { data } = await apiClient.delete<AiLimits>('/admin/ai-limits', {
      params: keys && keys.length > 0 ? { key: keys } : undefined,
      // Spring membaca ?key=A&key=B (tanpa tanda kurung array seperti default axios).
      paramsSerializer: { indexes: null },
    });
    return data;
  },
};
