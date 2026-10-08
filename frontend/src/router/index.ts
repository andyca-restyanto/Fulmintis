// frontend/src/router/index.ts
import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
import { authRoutes } from '@/modules/auth';
import { dashboardRoutes } from '@/modules/dashboard';
import { projectRoutes } from '@/modules/project';
import { tokenStorage } from '@/shared/services/tokenStorage';
import { BRAND_NAME } from '@/shared/config/brand';

const routes: RouteRecordRaw[] = [
  {
    // Root URL: user yang masih punya sesi valid langsung ke dashboard,
    // selain itu ke halaman register (Sign Up).
    path: '/',
    redirect: () => (tokenStorage.hasValidToken() ? { name: 'dashboard' } : { name: 'auth-signup' }),
  },
  ...authRoutes,
  ...dashboardRoutes,
  ...projectRoutes,
  // modul lain (misal testrepository berdiri sendiri nanti) tinggal
  // di-spread di sini juga, contoh:
  // ...testRepositoryRoutes,
  {
    // Catch-all: URL yang tidak dikenal -> halaman 404 (harus PALING BAWAH).
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/shared/views/NotFoundView.vue'),
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

// ---- Route guard ----
// Mencegah user membuka halaman yang butuh login (meta.requiresAuth) cuma
// dengan paste URL di address bar tanpa login dulu. Ini pengecekan di sisi
// FE (UX cepat, tanpa nunggu API call gagal dulu) -- proteksi SEBENARNYA
// tetap di backend (endpoint /api/dashboard/** menolak request tanpa JWT
// valid dengan 401, lihat SecurityConfig.java & JwtAuthenticationFilter).
router.beforeEach((to) => {
  const requiresAuth = to.meta.requiresAuth === true;
  const hadToken = Boolean(tokenStorage.getToken());
  // hasValidToken() juga membuang token yang sudah kedaluwarsa.
  const hasValidToken = tokenStorage.hasValidToken();

  if (requiresAuth && !hasValidToken) {
    // Punya token tapi sudah habis -> beri tahu user (bukan diam-diam
    // dilempar ke sign in tanpa alasan).
    return hadToken
      ? { name: 'auth-signin', query: { expired: 'true' } }
      : { name: 'auth-signin' };
  }

  // User yang sudah login tidak perlu melihat form Sign Up / Sign In lagi.
  // Kalau ada query notifikasi (verified/reset/expired) biarkan lewat supaya
  // pesannya tetap terbaca.
  const isEntryAuthPage = to.name === 'auth-signup' || to.name === 'auth-signin';
  const hasNotice = 'verified' in to.query || 'reset' in to.query || 'expired' in to.query;
  if (isEntryAuthPage && hasValidToken && !hasNotice) {
    return { name: 'dashboard' };
  }

  return true;
});

// Judul tab browser: "Fulmintis" di semua halaman. Dipasang di sini (bukan hanya
// di index.html) supaya selalu mengikuti BRAND_NAME. Kalau nanti ingin judul per
// halaman ("Dashboard · Fulmintis"), cukup ganti isi callback ini.
router.afterEach(() => {
  document.title = BRAND_NAME;
});

export default router;
