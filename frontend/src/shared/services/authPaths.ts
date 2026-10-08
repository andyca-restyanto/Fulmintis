// filepath: /frontend/src/shared/services/authPaths.ts
import type { AppRole } from './tokenStorage';

// Halaman yang bisa dibuka TANPA login (user + admin). Dipakai apiClient agar respons 401 di
// halaman-halaman ini tidak memicu redirect berulang (loop).
const PUBLIC_AUTH_PREFIXES = [
  '/auth',
  '/admin/signin',
  '/admin/signup',
  '/admin/accept-invitation',
  '/admin/reset-password',
];

export function isPublicAuthPath(pathname: string): boolean {
  return PUBLIC_AUTH_PREFIXES.some((prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`));
}

/** URL halaman sign in sesuai peran sesi (admin -> /admin/signin, selain itu /auth/signin). */
export function signinPathForRole(role: AppRole | null): string {
  return role === 'ADMIN' ? '/admin/signin' : '/auth/signin';
}
