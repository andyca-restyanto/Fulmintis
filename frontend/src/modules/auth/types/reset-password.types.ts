// frontend/src/modules/auth/types/reset-password.types.ts

export interface ForgotPasswordRequest {
  email: string;
}

// ---- Match 100% dgn ResendVerificationRequestDTO (backend) ----
export interface ResendVerificationRequest {
  email: string;
}

export interface ResetPasswordRequest {
  token: string;
  newPassword: string;
  confirmNewPassword: string;
}

export interface GenericMessageResponse {
  message: string;
}
