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

/** Body respons error backend (GlobalExceptionHandler / handler Spring Security). */
export interface AdminApiError {
  timestamp?: string;
  status?: number;
  message?: string;
  errors?: Record<string, string>;
}
