// filepath: /frontend/src/modules/admin/types/admin-auth.types.ts
// Kontrak API admin -- HARUS match 100% dengan DTO backend di
// backend/.../modules/admin/dto/ (nama field & nullability).

/** AdminRegistrationStatusResponseDTO */
export interface AdminRegistrationStatus {
  open: boolean;
  bootstrapCodeRequired: boolean;
}

/** AdminRegisterRequestDTO -- pendaftaran ADMIN PERTAMA. */
export interface AdminRegisterRequest {
  name: string;
  email: string;
  password: string;
  confirmPassword: string;
  bootstrapCode: string | null; // null / diabaikan bila backend tidak mewajibkan kode
}

/** AdminRegisterResponseDTO */
export interface AdminRegisterResponse {
  id: string;
  email: string;
  name: string;
  createdAt: string; // ISO date-time string
  message: string;
}

/** AdminInviteRequestDTO */
export interface AdminInviteRequest {
  name: string;
  email: string;
}

/** AdminInviteResponseDTO */
export interface AdminInviteResponse {
  email: string;
  name: string;
  expiresAt: string; // ISO date-time string
  message: string;
}

/** AdminInvitationValidationResponseDTO */
export interface AdminInvitationValidation {
  valid: boolean;
  email: string;
  name: string | null;
}

/** AdminAcceptInvitationRequestDTO */
export interface AdminAcceptInvitationRequest {
  token: string;
  newPassword: string;
  confirmPassword: string;
}

/** AdminProfileResponseDTO */
export interface AdminProfile {
  id: string;
  email: string;
  name: string | null;
  role: 'ADMIN';
}

/**
 * AdminStatus (enum backend). INACTIVE = dinonaktifkan; ACTIVE = aktif & terverifikasi;
 * PENDING = belum terverifikasi (menunggu undangan diterima, atau admin pertama belum verifikasi email).
 */
export type AdminStatus = 'ACTIVE' | 'PENDING' | 'INACTIVE';

/** AdminListItemResponseDTO -- satu baris di daftar admin. */
export interface AdminListItem {
  id: string;
  name: string | null;
  email: string;
  status: AdminStatus;
  /** true bila baris ini adalah admin yang sedang login. */
  self: boolean;
  createdAt: string; // ISO date-time tanpa zona (waktu server)
  /** Hanya terisi untuk PENDING yang menunggu undangan; null untuk status lain. */
  invitationExpiresAt: string | null;
}

/** AdminListResponseDTO -- satu halaman daftar admin (page mulai dari 0). */
export interface AdminListPage {
  items: AdminListItem[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

/** Body respons error backend (GlobalExceptionHandler / handler Spring Security). */
export interface AdminApiError {
  timestamp?: string;
  status?: number;
  /** Pembeda mesin opsional untuk status HTTP yang sama, mis. 'ACCOUNT_DEACTIVATED' pada 403. */
  code?: string;
  message?: string;
  errors?: Record<string, string>;
}
