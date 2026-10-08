// filepath: /frontend/src/modules/admin/services/adminManagement.service.ts
import apiClient from '@/shared/services/apiClient';
import type { AdminInviteRequest, AdminInviteResponse } from '../types/admin-auth.types';

export const adminManagementService = {
  /** PROTECTED (ROLE_ADMIN) -- undang admin baru lewat email. 409 kalau email sudah terdaftar. */
  async invite(payload: AdminInviteRequest): Promise<AdminInviteResponse> {
    const { data } = await apiClient.post<AdminInviteResponse>('/admin/admins', payload);
    return data;
  },
};
