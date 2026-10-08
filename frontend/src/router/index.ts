// frontend/src/router/index.ts
import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
import { authRoutes } from '@/modules/auth';
import { dashboardRoutes } from '@/modules/dashboard';
import { projectRoutes } from '@/modules/project';
import { adminRoutes } from '@/modules/admin';
import { tokenStorage, type AppRole } from '@/shared/services/tokenStorage';
import { homeRouteFor, resolveGuard } from './guards';
import { BRAND_NAME } from '@/shared/config/brand';

const routes: RouteRecordRaw[] = [
  {
    // Root URL: user yang masih punya sesi valid langsung ke dashboard,
    // selain itu ke halaman register (Sign Up).
    path: '/',
    redirect: () =>
      tokenStorage.hasValidToken()
        ? { name: homeRouteFor(tokenStorage.getRole() ?? 'USER') }
        : { name: 'auth-signup' },
  },
  ...authRoutes,
  ...dashboardRoutes,
  ...projectRoutes,
  ...adminRoutes,
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
  const hadToken = Boolean(tokenStorage.getToken());
  // hasValidToken() juga membuang token yang sudah kedaluwarsa.
  const hasValidToken = tokenStorage.hasValidToken();
  const metaRole = to.meta.role === 'ADMIN' || to.meta.role === 'USER' ? (to.meta.role as AppRole) : null;

  // Aturan akses (login, pemisahan area user/admin, redirect halaman sign in) ada di guards.ts
  // supaya bisa diuji tanpa browser. Query notifikasi (verified/reset/expired/accepted) dibiarkan
  // lewat di halaman sign in supaya pesannya tetap terbaca.
  return resolveGuard(
    {
      name: typeof to.name === 'string' ? to.name : null,
      requiresAuth: to.meta.requiresAuth === true,
      role: metaRole,
      hasNotice: ['verified', 'reset', 'expired', 'accepted'].some((key) => key in to.query),
    },
    { hadToken, hasValidToken, role: hasValidToken ? tokenStorage.getRole() : null }
  );
});

// Judul tab browser: "Fulmintis" di semua halaman. Dipasang di sini (bukan hanya
// di index.html) supaya selalu mengikuti BRAND_NAME. Kalau nanti ingin judul per
// halaman ("Dashboard · Fulmintis"), cukup ganti isi callback ini.
router.afterEach(() => {
  document.title = BRAND_NAME;
});

export default router;
