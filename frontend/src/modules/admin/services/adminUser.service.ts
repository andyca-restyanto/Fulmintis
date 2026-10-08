// filepath: /frontend/src/modules/admin/services/adminUser.service.ts
import apiClient from '@/shared/services/apiClient';
import type {
  UpdateUserTierRequest,
  UserListFilters,
  UserListItem,
  UserListPage,
  UserTier,
} from '../types/admin-user.types';
import { buildListParams } from '../utils/adminUser';

export const adminUserService = {
  /**
   * PROTECTED (ROLE_ADMIN) -- satu halaman daftar user non-admin, terbaru dulu.
   * page mulai dari 0; size 1..50. q (email/nama) dan tier hanya dikirim bila terisi.
   */
  async list(page: number, size: number, filters: UserListFilters): Promise<UserListPage> {
    const { data } = await apiClient.get<UserListPage>('/admin/users', { params: buildListParams(page, size, filters) });
    return data;
  },

  /** PROTECTED (ROLE_ADMIN) -- ubah tier user. 400 tier tidak valid, 404 id tidak ada / akun admin. */
  async updateTier(id: string, userType: UserTier): Promise<UserListItem> {
    const body: UpdateUserTierRequest = { userType };
    const { data } = await apiClient.patch<UserListItem>(`/admin/users/${encodeURIComponent(id)}/tier`, body);
    return data;
  },
};
