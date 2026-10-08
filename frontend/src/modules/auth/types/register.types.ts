// frontend/src/modules/auth/types/register.types.ts

export interface RegisterRequest {
  email: string;
  password: string;
  confirmPassword: string;
}

export interface RegisterResponse {
  id: string;
  email: string;
  createdAt: string; // ISO date-time string
  message: string;
}

export interface ApiValidationErrorResponse {
  timestamp: string;
  status: number;
  message: string;
  errors?: Record<string, string>;
}
