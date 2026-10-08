// frontend/src/shared/services/apiClient.ts
import axios, {
  AxiosError,
  type AxiosInstance,
  type InternalAxiosRequestConfig,
} from 'axios';
import { tokenStorage } from './tokenStorage';

// Ambil dari .env, contoh: VITE_API_BASE_URL=http://localhost:8080/api
const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api';

const apiClient: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// ---- Request interceptor: attach token kalau ada ----
apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = tokenStorage.getToken();
    if (token) {
      config.headers.set('Authorization', `Bearer ${token}`);
    }
    return config;
  },
  (error: AxiosError) => Promise.reject(error)
);

// ---- Response interceptor: normalisasi error + handle 401 ----
apiClient.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    // Login yang gagal (password salah) JUGA 401, tapi itu bukan "sesi
    // berakhir" -- biarkan LoginForm yang menampilkan pesannya.
    const isLoginAttempt = (error.config?.url ?? '').includes('/auth/login');

    if (error.response?.status === 401 && !isLoginAttempt) {
      // Token invalid/expired/dicabut (mis. password diganti) -> bersihkan
      // sesi & arahkan ke SIGN IN (bukan /auth yang halaman sign-up).
      const hadSession = Boolean(tokenStorage.getToken());
      tokenStorage.clearToken();

      // Hindari redirect loop kalau memang lagi di halaman auth
      if (!window.location.pathname.startsWith('/auth')) {
        window.location.href = hadSession ? '/auth/signin?expired=true' : '/auth/signin';
      }
    }

    return Promise.reject(error);
  }
);

export default apiClient;
