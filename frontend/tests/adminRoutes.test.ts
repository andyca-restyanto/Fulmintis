// filepath: /frontend/tests/adminRoutes.test.ts
import { test } from 'node:test';
import assert from 'node:assert/strict';
import type { RouteRecordRaw } from 'vue-router';
import adminRoutes from '../src/modules/admin/routes.ts';
import authRoutes from '../src/modules/auth/routes.ts';
import dashboardRoutes from '../src/modules/dashboard/routes.ts';
import { homeRouteFor, signinRouteFor } from '../src/router/guards.ts';

function collect(routes: RouteRecordRaw[], out: RouteRecordRaw[] = []): RouteRecordRaw[] {
  for (const route of routes) {
    out.push(route);
    if (route.children) collect(route.children, out);
  }
  return out;
}

const all = collect([...authRoutes, ...dashboardRoutes, ...adminRoutes]);
const names = new Set(all.map((r) => r.name).filter((n): n is string => typeof n === 'string'));

test('route tujuan redirect guard benar-benar ada', () => {
  for (const role of ['USER', 'ADMIN'] as const) {
    assert.ok(names.has(homeRouteFor(role)), homeRouteFor(role));
    assert.ok(names.has(signinRouteFor(role)), signinRouteFor(role));
  }
});

test('nama route tidak ada yang ganda', () => {
  const list = all.map((r) => r.name).filter((n) => typeof n === 'string');
  assert.equal(new Set(list).size, list.length);
});

test('route publik admin tidak butuh login', () => {
  for (const name of ['admin-signup', 'admin-signin', 'admin-accept-invitation', 'admin-reset-password']) {
    const route = all.find((r) => r.name === name);
    assert.ok(route, name);
    assert.notEqual(route?.meta?.requiresAuth, true, name);
  }
});

test('halaman reset password admin memakai meta.area admin', () => {
  assert.equal(all.find((r) => r.name === 'admin-reset-password')?.meta?.area, 'admin');
});

test('semua route area admin mewarisi requiresAuth + role ADMIN dari induknya', () => {
  const parent = adminRoutes.find((r) => r.path === '/admin');
  assert.ok(parent);
  assert.equal(parent.meta?.requiresAuth, true);
  assert.equal(parent.meta?.role, 'ADMIN');
  const childNames = (parent.children ?? []).map((c) => c.name).filter(Boolean);
  assert.deepEqual(
    childNames.sort(),
    ['admin-ai-token-usage', 'admin-dashboard', 'admin-invite', 'admin-payments', 'admin-user-logs', 'admin-users']
  );
  // Tidak ada route admin terproteksi di luar induk itu.
  const outside = adminRoutes.filter((r) => r.path !== '/admin');
  for (const route of outside) assert.notEqual(route.meta?.requiresAuth, true, String(route.name));
});

test('keempat menu sidebar punya judul untuk halaman Coming soon', () => {
  const titles = Object.fromEntries(
    all.filter((r) => ['admin-users', 'admin-user-logs', 'admin-payments', 'admin-ai-token-usage'].includes(String(r.name)))
      .map((r) => [r.name, r.meta?.title])
  );
  assert.deepEqual(titles, {
    'admin-users': 'User',
    'admin-user-logs': 'Log user',
    'admin-payments': 'Payment',
    'admin-ai-token-usage': 'AI token used',
  });
});

test('route user lama tidak diberi role ADMIN', () => {
  for (const route of dashboardRoutes) assert.notEqual(route.meta?.role, 'ADMIN');
});
