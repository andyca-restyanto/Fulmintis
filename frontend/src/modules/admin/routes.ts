// filepath: /frontend/src/modules/admin/routes.ts
import type { RouteRecordRaw } from 'vue-router';

// Dua kelompok route:
//  1. PUBLIK (sign in/up admin, terima undangan, reset password) -- tanpa login.
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
        path: 'admins/new',
        name: 'admin-invite',
        component: () => import('./views/AdminInviteView.vue'),
      },
      // Menu sidebar -- sementara semuanya halaman "Coming soon".
      {
        path: 'users',
        name: 'admin-users',
        component: () => import('./views/ComingSoonView.vue'),
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
