// filepath: /frontend/src/modules/admin/utils/adminErrors.ts
import type { AdminApiError } from '../types/admin-auth.types';

export interface AdminErrorInfo {
  /** Status HTTP, atau null kalau server tidak terjangkau. */
  status: number | null;
  /** Pesan siap tampil (dari backend bila ada). */
  message: string;
  /** Pembeda mesin dari backend (mis. 'ACCOUNT_DEACTIVATED'), atau null. */
  code: string | null;
  /** Error per field untuk respons 400 validasi (key = nama field DTO). */
  fieldErrors: Record<string, string>;
}

export const NETWORK_ERROR_MESSAGE = 'Tidak dapat terhubung ke server';
export const DEFAULT_ERROR_MESSAGE = 'Terjadi kesalahan, coba lagi';
export const ACCOUNT_DEACTIVATED_CODE = 'ACCOUNT_DEACTIVATED';

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
    return { status: null, message: NETWORK_ERROR_MESSAGE, code: null, fieldErrors: {} };
  }
  const status = err.response.status ?? null;
  const data = err.response.data;
  const fieldErrors = status === 400 && data?.errors ? { ...data.errors } : {};
  return { status, message: data?.message ?? fallback, code: data?.code ?? null, fieldErrors };
}

/** Admin nonaktif (403 ber-code) -- BUKAN "belum verifikasi", jadi tidak boleh menawarkan kirim ulang verifikasi. */
export function isAccountDeactivated(info: AdminErrorInfo): boolean {
  return info.status === 403 && info.code === ACCOUNT_DEACTIVATED_CODE;
}

/** Form "kirim ulang verifikasi" hanya untuk 403 yang BUKAN akun nonaktif. */
export function offersVerificationResend(info: AdminErrorInfo): boolean {
  return info.status === 403 && !isAccountDeactivated(info);
}
