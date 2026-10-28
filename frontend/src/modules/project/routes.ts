// frontend/src/modules/project/routes.ts
import type { RouteRecordRaw } from 'vue-router';

// meta.menu = kode menu (ProjectMenuCode) yang HARUS ada di availableMenus
// project detail agar route boleh dibuka -- dicek di ProjectLayoutView (route
// guard level project, karena availableMenus baru diketahui setelah
// GET /api/projects/:id selesai). Contoh: 'SETTING' hanya utk OWNER.
const projectRoutes: RouteRecordRaw[] = [
  {
    // ProjectLayoutView fetch GET /api/projects/:projectId sekali, lalu
    // render topbar + sidebar (menu di-filter dari availableMenus) +
    // <RouterView /> utk child route di bawah.
    path: '/projects/:projectId',
    component: () => import('./views/ProjectLayoutView.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        redirect: (to) => ({ name: 'project-dashboard', params: to.params }),
      },
      {
        path: 'dashboard',
        name: 'project-dashboard',
        component: () => import('./views/ProjectDashboardView.vue'),
        meta: { menu: 'DASHBOARD' },
      },
      // "Test Repository", "Test Runs", "Reports", dan "Settings" sudah
      // punya view/module sendiri.
      {
        path: 'test-repository',
        name: 'project-test-repository',
        component: () => import('@/modules/testrepository/views/TestRepositoryView.vue'),
        meta: { menu: 'TEST_REPOSITORY' },
      },
      {
        path: 'test-runs',
        name: 'project-test-runs',
        component: () => import('@/modules/testrun/views/TestRunsView.vue'),
        meta: { menu: 'TEST_RUNS' },
      },
      {
        path: 'test-runs/:testRunId/execute',
        name: 'project-test-run-execute',
        component: () => import('@/modules/testrun/views/TestRunExecuteView.vue'),
        meta: { menu: 'TEST_RUNS' },
      },
      {
        path: 'reports',
        name: 'project-reports',
        component: () => import('@/modules/report/views/ReportsView.vue'),
        meta: { menu: 'REPORT' },
      },
      {
        // Generate kode automation dari test case dengan AI (modul automation). Semua member; Setup di dalamnya OWNER saja.
        path: 'automation',
        name: 'project-automation',
        component: () => import('@/modules/automation/views/AutomationView.vue'),
        meta: { menu: 'AUTOMATION' },
      },
      {
        path: 'settings',
        name: 'project-settings',
        component: () => import('./views/ProjectSettingsView.vue'),
        meta: { menu: 'SETTING' },
      },
    ],
  },
];

export default projectRoutes;
