// frontend/src/modules/auth/routes.ts
import type { RouteRecordRaw } from 'vue-router';

const authRoutes: RouteRecordRaw[] = [
  {
    path: '/auth',
    name: 'auth-signup',
    component: () => import('./views/AuthView.vue'),
  },
  {
    path: '/auth/signin',
    name: 'auth-signin',
    component: () => import('./views/AuthView.vue'),
  },
  {
    path: '/auth/forgot-password',
    name: 'auth-forgot-password',
    component: () => import('./views/ForgotPasswordView.vue'),
  },
  {
    path: '/auth/reset-password',
    name: 'auth-reset-password',
    component: () => import('./views/ResetPasswordView.vue'),
  },
];

export default authRoutes;
