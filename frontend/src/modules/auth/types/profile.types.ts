// frontend/src/modules/auth/types/profile.types.ts

// ---- Match 100% dgn ProfileResponseDTO (backend) ----
export interface ProfileResponse {
  id: string;
  email: string;
  name: string | null; // nullable -- requirement #4
}

// ---- Match 100% dgn UpdateProfileRequestDTO (backend) ----
export interface UpdateProfileRequest {
  name: string;
}

// ---- Match respons POST /api/auth/change-password (AuthController) ----
// Setelah password berubah, token LAMA otomatis tidak berlaku (backend menaruh
// versi password di JWT), jadi backend membalas token BARU untuk sesi ini.
export interface ChangePasswordResponse {
  message: string;
  accessToken: string;
  tokenType: string;
  expiresIn: number; // detik
}

// ---- Match 100% dgn ChangePasswordRequestDTO (backend) ----
export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
  confirmNewPassword: string;
}
