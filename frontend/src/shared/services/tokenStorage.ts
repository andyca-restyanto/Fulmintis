// frontend/src/shared/services/tokenStorage.ts

const ACCESS_TOKEN_KEY = 'access_token';

// Toleransi selisih jam FE vs server (detik): token yang tinggal beberapa
// detik lagi dianggap sudah habis supaya request tidak "kejar-kejaran" dgn
// waktu kedaluwarsa dan gagal 401 di tengah jalan.
const EXPIRY_SKEW_SECONDS = 10;

/**
 * Baca klaim "exp" (detik epoch) dari payload JWT TANPA memverifikasi tanda
 * tangan -- FE memang tidak (dan tidak boleh) punya secret-nya. Ini murni
 * untuk UX (tidak menampilkan halaman yang pasti berujung 401); keputusan
 * akses SEBENARNYA tetap di backend (JwtAuthenticationFilter).
 */
function readExpiry(token: string): number | null {
  try {
    const payloadPart = token.split('.')[1];
    if (!payloadPart) return null;
    const base64 = payloadPart.replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
    const payload = JSON.parse(atob(padded)) as { exp?: unknown };
    return typeof payload.exp === 'number' ? payload.exp : null;
  } catch {
    return null;
  }
}

/**
 * Klaim "sub" (email user) dari payload JWT -- UX saja (mis. mengenali job milik sendiri di riwayat),
 * TIDAK PERNAH dasar keputusan akses. Tanda tangan tidak diverifikasi (FE tidak punya secret-nya).
 */
function readSubject(token: string): string | null {
  try {
    const payloadPart = token.split('.')[1];
    if (!payloadPart) return null;
    const base64 = payloadPart.replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
    const payload = JSON.parse(atob(padded)) as { sub?: unknown };
    return typeof payload.sub === 'string' ? payload.sub : null;
  } catch {
    return null;
  }
}

export const tokenStorage = {
  getToken(): string | null {
    return localStorage.getItem(ACCESS_TOKEN_KEY);
  },
  setToken(token: string): void {
    localStorage.setItem(ACCESS_TOKEN_KEY, token);
  },
  clearToken(): void {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
  },

  /** Email (klaim "sub") user yang sedang login, atau null. Hanya untuk tampilan -- bukan keputusan akses. */
  getTokenSubject(): string | null {
    const token = localStorage.getItem(ACCESS_TOKEN_KEY);
    return token ? readSubject(token) : null;
  },

  /** true kalau ada token TAPI sudah kedaluwarsa (atau tidak terbaca). */
  isTokenExpired(token: string | null = localStorage.getItem(ACCESS_TOKEN_KEY)): boolean {
    if (!token) return false;
    const exp = readExpiry(token);
    if (exp === null) return true; // token rusak -> anggap tidak berlaku
    return exp - EXPIRY_SKEW_SECONDS <= Math.floor(Date.now() / 1000);
  },

  /** true kalau ada token dan belum kedaluwarsa. Token kedaluwarsa otomatis dibuang. */
  hasValidToken(): boolean {
    const token = localStorage.getItem(ACCESS_TOKEN_KEY);
    if (!token) return false;
    if (this.isTokenExpired(token)) {
      this.clearToken();
      return false;
    }
    return true;
  },
};
