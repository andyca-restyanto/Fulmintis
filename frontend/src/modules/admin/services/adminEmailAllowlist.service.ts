// filepath: /frontend/src/modules/admin/services/adminEmailAllowlist.service.ts
import apiClient from '@/shared/services/apiClient';
import type {
  AddAllowedEmailRequest,
  AllowedEmailRule,
  AllowlistResponse,
  AllowlistSettingsRequest,
} from '../types/admin-email-allowlist.types';

export const adminEmailAllowlistService = {
  /** PROTECTED (ROLE_ADMIN) -- status aktif + seluruh aturan. */
  async get(): Promise<AllowlistResponse> {
    const { data } = await apiClient.get<AllowlistResponse>('/admin/email-allowlist');
    return data;
  },

  /** PROTECTED (ROLE_ADMIN) -- tambah aturan. 400 format tidak valid, 409 duplikat. */
  async add(payload: AddAllowedEmailRequest): Promise<AllowedEmailRule> {
    const { data } = await apiClient.post<AllowedEmailRule>('/admin/email-allowlist', payload);
    return data;
  },

  /** PROTECTED (ROLE_ADMIN) -- hapus aturan. 404 bila sudah tidak ada. */
  async remove(id: number): Promise<void> {
    await apiClient.delete(`/admin/email-allowlist/${id}`);
  },

  /** PROTECTED (ROLE_ADMIN) -- hidupkan/matikan pembatasan. */
  async setEnabled(enabled: boolean): Promise<AllowlistResponse> {
    const payload: AllowlistSettingsRequest = { enabled };
    const { data } = await apiClient.put<AllowlistResponse>('/admin/email-allowlist/settings', payload);
    return data;
  },
};
