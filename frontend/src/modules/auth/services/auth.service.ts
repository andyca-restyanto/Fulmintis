// frontend/src/modules/auth/services/auth.service.ts
import apiClient from '@/shared/services/apiClient';
import type { RegisterRequest, RegisterResponse } from '../types/register.types';
import type { LoginRequest, LoginResponse } from '../types/login.types';
import type {
  ForgotPasswordRequest,
  ResendVerificationRequest,
  ResetPasswordRequest,
  GenericMessageResponse,
} from '../types/reset-password.types';
import type {
  ProfileResponse,
  UpdateProfileRequest,
  ChangePasswordRequest,
  ChangePasswordResponse,
} from '../types/profile.types';

const AUTH_BASE_URL = '/auth';

export const authService = {
  async register(payload: RegisterRequest): Promise<RegisterResponse> {
    const { data } = await apiClient.post<RegisterResponse>(
      `${AUTH_BASE_URL}/register`,
      payload
    );
    return data;
  },

  async login(payload: LoginRequest): Promise<LoginResponse> {
    const { data } = await apiClient.post<LoginResponse>(
      `${AUTH_BASE_URL}/login`,
      payload
    );
    return data;
  },

  /** Respons SELALU generik (tidak membocorkan email terdaftar atau tidak). */
  async resendVerification(payload: ResendVerificationRequest): Promise<GenericMessageResponse> {
    const { data } = await apiClient.post<GenericMessageResponse>(
      `${AUTH_BASE_URL}/resend-verification`,
      payload
    );
    return data;
  },

  async forgotPassword(payload: ForgotPasswordRequest): Promise<GenericMessageResponse> {
    const { data } = await apiClient.post<GenericMessageResponse>(
      `${AUTH_BASE_URL}/forgot-password`,
      payload
    );
    return data;
  },

  /** Throws kalau token invalid/expired (400) -- caller wrap di try/catch. */
  async validateResetToken(token: string): Promise<void> {
    await apiClient.get(`${AUTH_BASE_URL}/reset-password/validate`, {
      params: { token },
    });
  },

  async resetPassword(payload: ResetPasswordRequest): Promise<GenericMessageResponse> {
    const { data } = await apiClient.post<GenericMessageResponse>(
      `${AUTH_BASE_URL}/reset-password`,
      payload
    );
    return data;
  },

  /** PROTECTED (wajib JWT) -- profile user yang sedang login. */
  async getProfile(): Promise<ProfileResponse> {
    const { data } = await apiClient.get<ProfileResponse>(`${AUTH_BASE_URL}/profile`);
    return data;
  },

  /** PROTECTED (wajib JWT) -- update nama tampilan user. */
  async updateProfile(payload: UpdateProfileRequest): Promise<ProfileResponse> {
    const { data } = await apiClient.patch<ProfileResponse>(
      `${AUTH_BASE_URL}/profile`,
      payload
    );
    return data;
  },

  /** PROTECTED (wajib JWT) -- ganti password saat sedang login (tau password lama). */
  async changePassword(payload: ChangePasswordRequest): Promise<ChangePasswordResponse> {
    const { data } = await apiClient.post<ChangePasswordResponse>(
      `${AUTH_BASE_URL}/change-password`,
      payload
    );
    return data;
  },
};
