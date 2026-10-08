// filepath: /frontend/src/modules/admin/services/adminManagement.service.ts
import apiClient from '@/shared/services/apiClient';
import type {
  AdminInviteRequest,
  AdminInviteResponse,
  AdminListItem,
  AdminListPage,
} from '../types/admin-auth.types';

export const adminManagementService = {
  /** PROTECTED (ROLE_ADMIN) -- undang admin baru lewat email. 409 kalau email sudah terdaftar. */
  async invite(payload: AdminInviteRequest): Promise<AdminInviteResponse> {
    const { data } = await apiClient.post<AdminInviteResponse>('/admin/admins', payload);
    return data;
  },

  /** PROTECTED (ROLE_ADMIN) -- satu halaman daftar admin, terbaru dulu. page mulai dari 0; size 1..50. */
  async list(page: number, size: number): Promise<AdminListPage> {
    const { data } = await apiClient.get<AdminListPage>('/admin/admins', { params: { page, size } });
    return data;
  },

  /** PROTECTED (ROLE_ADMIN) -- nonaktifkan admin. 400 diri sendiri, 404 bukan admin, 409 admin aktif terakhir. */
  async deactivate(id: string): Promise<AdminListItem> {
    const { data } = await apiClient.post<AdminListItem>(`/admin/admins/${encodeURIComponent(id)}/deactivate`);
    return data;
  },

  /** PROTECTED (ROLE_ADMIN) -- aktifkan kembali admin yang dinonaktifkan. */
  async activate(id: string): Promise<AdminListItem> {
    const { data } = await apiClient.post<AdminListItem>(`/admin/admins/${encodeURIComponent(id)}/activate`);
    return data;
  },
};
