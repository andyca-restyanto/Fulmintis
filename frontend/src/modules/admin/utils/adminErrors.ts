// filepath: /frontend/src/modules/admin/utils/adminErrors.ts
import type { AdminApiError } from '../types/admin-auth.types';

export interface AdminErrorInfo {
  /** Status HTTP, atau null kalau server tidak terjangkau. */
  status: number | null;
  /** Pesan siap tampil (dari backend bila ada). */
  message: string;
  /** Error per field untuk respons 400 validasi (key = nama field DTO). */
  fieldErrors: Record<string, string>;
}

export const NETWORK_ERROR_MESSAGE = 'Tidak dapat terhubung ke server';
export const DEFAULT_ERROR_MESSAGE = 'Terjadi kesalahan, coba lagi';

interface AxiosLikeError {
  isAxiosError: true;
  response?: { status?: number; data?: AdminApiError };
}

function isAxiosLike(err: unknown): err is AxiosLikeError {
  return typeof err === 'object' && err !== null && (err as { isAxiosError?: unknown }).isAxiosError === true;
}

/** Normalisasi error axios jadi bentuk yang sama untuk semua halaman admin. */
export function readAdminError(err: unknown, fallback: string = DEFAULT_ERROR_MESSAGE): AdminErrorInfo {
  if (!isAxiosLike(err) || !err.response) {
    return { status: null, message: NETWORK_ERROR_MESSAGE, fieldErrors: {} };
  }
  const status = err.response.status ?? null;
  const data = err.response.data;
  const fieldErrors = status === 400 && data?.errors ? { ...data.errors } : {};
  return { status, message: data?.message ?? fallback, fieldErrors };
}
