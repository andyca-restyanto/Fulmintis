// filepath: /frontend/src/modules/auth/utils/registerErrors.ts
// Memetakan respons error POST /api/auth/register ke tampilan form (fungsi murni, diuji).

export const EMAIL_NOT_ALLOWED_CODE = 'EMAIL_NOT_ALLOWED';

export interface RegisterErrorBody {
  message?: string;
  code?: string;
  errors?: Record<string, string>;
}

export interface RegisterErrorView {
  /** Error per field (key = nama field DTO), ditampilkan di bawah input. */
  fieldErrors: Record<string, string>;
  /** Pesan umum di bawah form. */
  general: string;
}

export function mapRegisterError(status: number | undefined, data: RegisterErrorBody | undefined): RegisterErrorView {
  if (status === 400 && data?.errors) {
    return { fieldErrors: { ...data.errors }, general: '' };
  }
  // Email di luar daftar izin: tampilkan di field email supaya jelas apa yang harus diganti.
  if (status === 403 && data?.code === EMAIL_NOT_ALLOWED_CODE) {
    return {
      fieldErrors: { email: data.message ?? 'Email ini tidak diizinkan untuk mendaftar. Hubungi admin.' },
      general: '',
    };
  }
  if (status === 409) {
    return { fieldErrors: {}, general: data?.message ?? 'Email sudah terdaftar' };
  }
  return { fieldErrors: {}, general: data?.message ?? 'Terjadi kesalahan, coba lagi' };
}
