// filepath: /frontend/src/router/guards.ts
import type { AppRole } from '@/shared/services/tokenStorage';

// Logika route guard dibuat MURNI (tanpa vue-router/localStorage) supaya bisa diuji tanpa browser.
// Ini hanya UX (tidak menampilkan halaman yang pasti ditolak). Proteksi sebenarnya ada di backend:
// /api/admin/** hanya ROLE_ADMIN, API user hanya ROLE_USER (SecurityConfig.java).

export interface GuardSession {
  /** Ada token di storage (walau sudah kedaluwarsa). */
  hadToken: boolean;
  /** Ada token dan belum kedaluwarsa. */
  hasValidToken: boolean;
  role: AppRole | null;
}

export interface GuardRoute {
  name: string | null;
  requiresAuth: boolean;
  /** meta.role; route yang butuh login tanpa meta.role dianggap area USER. */
  role: AppRole | null;
  /** Ada query notifikasi (verified/reset/expired/accepted) yang harus tetap terbaca. */
  hasNotice: boolean;
}

export interface GuardRedirect {
  name: string;
  query?: Record<string, string>;
}

const ENTRY_PAGES = new Set(['auth-signup', 'auth-signin', 'admin-signup', 'admin-signin']);

export function homeRouteFor(role: AppRole): string {
  return role === 'ADMIN' ? 'admin-dashboard' : 'dashboard';
}

export function signinRouteFor(role: AppRole): string {
  return role === 'ADMIN' ? 'admin-signin' : 'auth-signin';
}

export function resolveGuard(route: GuardRoute, session: GuardSession): true | GuardRedirect {
  if (route.requiresAuth) {
    const required: AppRole = route.role ?? 'USER';

    if (!session.hasValidToken) {
      // Punya token tapi sudah habis -> beri tahu user (bukan diam-diam dilempar ke sign in).
      return session.hadToken
        ? { name: signinRouteFor(required), query: { expired: 'true' } }
        : { name: signinRouteFor(required) };
    }

    const current: AppRole = session.role ?? 'USER';
    if (current !== required) {
      // Admin membuka halaman user (atau sebaliknya) -> kembali ke dashboard perannya sendiri.
      return { name: homeRouteFor(current) };
    }
    return true;
  }

  // Yang sudah login tidak perlu melihat form Sign Up / Sign In (user maupun admin) lagi.
  if (session.hasValidToken && !route.hasNotice && route.name !== null && ENTRY_PAGES.has(route.name)) {
    return { name: homeRouteFor(session.role ?? 'USER') };
  }

  return true;
}
