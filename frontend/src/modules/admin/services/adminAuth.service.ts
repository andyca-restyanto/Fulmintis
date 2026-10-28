// filepath: /frontend/src/modules/admin/services/adminAuth.service.ts
import apiClient from '@/shared/services/apiClient';
import type { LoginRequest, LoginResponse } from '@/modules/auth/types/login.types';
import type {
  AdminAcceptInvitationRequest,
  AdminInvitationValidation,
  AdminProfile,
  AdminRegisterRequest,
  AdminRegisterResponse,
  AdminRegistrationStatus,
} from '../types/admin-auth.types';
import type { GenericMessageResponse } from '@/modules/auth/types/reset-password.types';

const ADMIN_AUTH_URL = '/admin/auth';

export const adminAuthService = {
  /** PUBLIK -- apakah pendaftaran admin pertama masih terbuka. */
  async getRegistrationStatus(): Promise<AdminRegistrationStatus> {
    const { data } = await apiClient.get<AdminRegistrationStatus>(`${ADMIN_AUTH_URL}/registration-status`);
    return data;
  },

  /** PUBLIK -- hanya berhasil selama belum ada admin (selain itu 403). */
  async register(payload: AdminRegisterRequest): Promise<AdminRegisterResponse> {
    const { data } = await apiClient.post<AdminRegisterResponse>(`${ADMIN_AUTH_URL}/register`, payload);
    return data;
  },

  /** PUBLIK -- login khusus admin (akun non-admin = 401 yang sama dgn password salah). */
  async login(payload: LoginRequest): Promise<LoginResponse> {
    const { data } = await apiClient.post<LoginResponse>(`${ADMIN_AUTH_URL}/login`, payload);
    return data;
  },

  /** PUBLIK -- throws (400) kalau link undangan tidak valid / kedaluwarsa / sudah dipakai. */
  async validateInvitation(token: string): Promise<AdminInvitationValidation> {
    const { data } = await apiClient.get<AdminInvitationValidation>(`${ADMIN_AUTH_URL}/invitation/validate`, {
      params: { token },
    });
    return data;
  },

  /** PUBLIK -- penerima undangan membuat password. */
  async acceptInvitation(payload: AdminAcceptInvitationRequest): Promise<GenericMessageResponse> {
    const { data } = await apiClient.post<GenericMessageResponse>(`${ADMIN_AUTH_URL}/accept-invitation`, payload);
    return data;
  },

  /** PROTECTED (ROLE_ADMIN) -- profil admin yang sedang login. */
  async getMe(): Promise<AdminProfile> {
    const { data } = await apiClient.get<AdminProfile>('/admin/me');
    return data;
  },
};
