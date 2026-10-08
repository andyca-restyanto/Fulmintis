// filepath: /frontend/src/modules/admin/routes.ts
import type { RouteRecordRaw } from 'vue-router';

// Dua kelompok route:
//  1. PUBLIK (sign in/up admin, terima undangan, lupa & reset password) -- tanpa login.
//  2. AREA ADMIN di bawah AdminLayoutView -- meta { requiresAuth: true, role: 'ADMIN' }
//     (diwarisi semua child). Guard di router/guards.ts mengarahkan user biasa ke /dashboard;
//     proteksi sebenarnya di backend (/api/admin/** hanya ROLE_ADMIN).
const adminRoutes: RouteRecordRaw[] = [
  {
    path: '/admin/signup',
    name: 'admin-signup',
    component: () => import('./views/AdminSignupView.vue'),
  },
  {
    path: '/admin/signin',
    name: 'admin-signin',
    component: () => import('./views/AdminSigninView.vue'),
  },
  {
    path: '/admin/accept-invitation',
    name: 'admin-accept-invitation',
    component: () => import('./views/AdminAcceptInvitationView.vue'),
  },
  {
    // Halaman lupa password yang sama dengan user; meta.area = 'admin' membuat tombol
    // kembali mengarah ke /admin/signin. Link di email admin mengarah ke /admin/reset-password.
    path: '/admin/forgot-password',
    name: 'admin-forgot-password',
    component: () => import('@/modules/auth/views/ForgotPasswordView.vue'),
    meta: { area: 'admin' },
  },
  {
    // Halaman reset password yang sama dengan user; meta.area = 'admin' membuatnya
    // mengarahkan ke /admin/signin setelah sukses.
    path: '/admin/reset-password',
    name: 'admin-reset-password',
    component: () => import('@/modules/auth/views/ResetPasswordView.vue'),
    meta: { area: 'admin' },
  },
  {
    path: '/admin',
    component: () => import('./views/AdminLayoutView.vue'),
    meta: { requiresAuth: true, role: 'ADMIN' },
    children: [
      {
        path: '',
        redirect: { name: 'admin-dashboard' },
      },
      {
        path: 'dashboard',
        name: 'admin-dashboard',
        component: () => import('./views/AdminDashboardView.vue'),
      },
      {
        // Menu Admin: daftar admin (paginasi lewat ?page=), nonaktifkan/aktifkan, kirim ulang undangan.
        path: 'admins',
        name: 'admin-admins',
        component: () => import('./views/AdminListView.vue'),
        meta: { title: 'Admin' },
      },
      {
        path: 'admins/new',
        name: 'admin-invite',
        component: () => import('./views/AdminInviteView.vue'),
      },
      {
        // Ubah password (tahu password saat ini); dibuka dari dropdown ikon user di header.
        path: 'change-password',
        name: 'admin-change-password',
        component: () => import('./views/AdminChangePasswordView.vue'),
        meta: { title: 'Ubah Password' },
      },
      // Menu sidebar -- selain Admin dan User, sementara halaman "Coming soon".
      {
        // Menu User: daftar user non-admin (paginasi, cari email/nama, filter tier) dan ubah tier.
        path: 'users',
        name: 'admin-users',
        component: () => import('./views/AdminUserListView.vue'),
        meta: { title: 'User' },
      },
      {
        path: 'user-logs',
        name: 'admin-user-logs',
        component: () => import('./views/ComingSoonView.vue'),
        meta: { title: 'Log user' },
      },
      {
        path: 'payments',
        name: 'admin-payments',
        component: () => import('./views/ComingSoonView.vue'),
        meta: { title: 'Payment' },
      },
      {
        path: 'ai-token-usage',
        name: 'admin-ai-token-usage',
        component: () => import('./views/ComingSoonView.vue'),
        meta: { title: 'AI token used' },
      },
    ],
  },
];

export default adminRoutes;
