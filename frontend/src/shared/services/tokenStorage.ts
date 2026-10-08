// frontend/src/shared/services/tokenStorage.ts

const ACCESS_TOKEN_KEY = 'access_token';

// Toleransi selisih jam FE vs server (detik): token yang tinggal beberapa
// detik lagi dianggap sudah habis supaya request tidak "kejar-kejaran" dgn
// waktu kedaluwarsa dan gagal 401 di tengah jalan.
const EXPIRY_SKEW_SECONDS = 10;

/**
 * Decode payload JWT TANPA memverifikasi tanda tangan -- FE memang tidak (dan tidak boleh)
 * punya secret-nya. Semua pembacaan klaim di file ini murni untuk UX (tidak menampilkan halaman
 * yang pasti berujung 401/403); keputusan akses SEBENARNYA tetap di backend (JwtAuthenticationFilter
 * + SecurityConfig).
 */
function decodePayload(token: string): Record<string, unknown> | null {
  try {
    const payloadPart = token.split('.')[1];
    if (!payloadPart) return null;
    const base64 = payloadPart.replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
    const payload: unknown = JSON.parse(atob(padded));
    return typeof payload === 'object' && payload !== null ? (payload as Record<string, unknown>) : null;
  } catch {
    return null;
  }
}

/** Klaim "exp" (detik epoch), atau null. */
function readExpiry(token: string): number | null {
  const exp = decodePayload(token)?.exp;
  return typeof exp === 'number' ? exp : null;
}

/** Klaim "sub" (email user) -- UX saja (mis. mengenali job milik sendiri di riwayat). */
function readSubject(token: string): string | null {
  const sub = decodePayload(token)?.sub;
  return typeof sub === 'string' ? sub : null;
}

export type AppRole = 'USER' | 'ADMIN';

/**
 * Klaim "role". Token lama (sebelum fitur admin) tidak punya klaim ini -> USER.
 * Token yang tidak terbaca -> null. Hanya untuk memilih dashboard/redirect di FE;
 * backend menentukan peran dari database, bukan dari klaim ini.
 */
function readRole(token: string): AppRole | null {
  const payload = decodePayload(token);
  if (!payload) return null;
  return payload.role === 'ADMIN' ? 'ADMIN' : 'USER';
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

  /** Peran sesi saat ini ("USER" | "ADMIN"), atau null kalau tidak ada token / token tidak terbaca. Bukan keputusan akses. */
  getRole(): AppRole | null {
    const token = localStorage.getItem(ACCESS_TOKEN_KEY);
    return token ? readRole(token) : null;
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
