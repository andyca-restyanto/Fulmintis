// frontend/src/modules/auth/types/login.types.ts

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number; // detik
  id: string;
  email: string;
  name: string | null; // nullable -- match LoginResponseDTO.name (requirement #4)
  role: 'USER' | 'ADMIN'; // match LoginResponseDTO.role -- dipakai memilih dashboard
}
