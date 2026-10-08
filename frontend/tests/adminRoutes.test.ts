// filepath: /frontend/tests/adminRoutes.test.ts
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
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
  for (const name of ['admin-signup', 'admin-signin', 'admin-accept-invitation', 'admin-forgot-password', 'admin-reset-password']) {
    const route = all.find((r) => r.name === name);
    assert.ok(route, name);
    assert.notEqual(route?.meta?.requiresAuth, true, name);
  }
});

test('halaman lupa & reset password admin memakai meta.area admin', () => {
  assert.equal(all.find((r) => r.name === 'admin-forgot-password')?.meta?.area, 'admin');
  assert.equal(all.find((r) => r.name === 'admin-reset-password')?.meta?.area, 'admin');
  // Halaman user TIDAK ikut ber-area admin (kalau iya, tombol kembalinya salah arah).
  assert.notEqual(all.find((r) => r.name === 'auth-forgot-password')?.meta?.area, 'admin');
  assert.notEqual(all.find((r) => r.name === 'auth-reset-password')?.meta?.area, 'admin');
});

test('lupa password admin memakai path /admin/forgot-password (sesuai tujuan link email & authPaths)', () => {
  assert.equal(all.find((r) => r.name === 'admin-forgot-password')?.path, '/admin/forgot-password');
  assert.equal(all.find((r) => r.name === 'admin-reset-password')?.path, '/admin/reset-password');
});

test('semua route area admin mewarisi requiresAuth + role ADMIN dari induknya', () => {
  const parent = adminRoutes.find((r) => r.path === '/admin');
  assert.ok(parent);
  assert.equal(parent.meta?.requiresAuth, true);
  assert.equal(parent.meta?.role, 'ADMIN');
  const childNames = (parent.children ?? []).map((c) => c.name).filter(Boolean);
  assert.deepEqual(
    childNames.sort(),
    [
      'admin-admins', 'admin-ai-token-usage', 'admin-change-password', 'admin-dashboard', 'admin-invite',
      'admin-payments', 'admin-user-logs', 'admin-users',
    ]
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

test('ubah password admin ada di area admin (di bawah /admin, judul "Ubah Password")', () => {
  const parent = adminRoutes.find((r) => r.path === '/admin');
  const route = parent?.children?.find((c) => c.name === 'admin-change-password');
  assert.ok(route);
  assert.equal(route.path, 'change-password');
  assert.equal(route.meta?.title, 'Ubah Password');
});

test('setiap tujuan router.push({ name }) di view admin & lupa/reset password adalah nama route yang ada', () => {
  const files = [
    '../src/modules/admin/views/AdminSigninView.vue',
    '../src/modules/admin/views/AdminSignupView.vue',
    '../src/modules/admin/views/AdminAcceptInvitationView.vue',
    '../src/modules/admin/views/AdminLayoutView.vue',
    '../src/modules/admin/views/AdminInviteView.vue',
    '../src/modules/admin/views/AdminChangePasswordView.vue',
    '../src/modules/admin/views/AdminListView.vue',
    '../src/modules/admin/views/AdminUserListView.vue',
    '../src/modules/admin/views/AdminDashboardView.vue',
    '../src/modules/auth/views/ForgotPasswordView.vue',
    '../src/modules/auth/views/ResetPasswordView.vue',
  ];
  let checked = 0;
  for (const file of files) {
    const source = readFileSync(new URL(file, import.meta.url), 'utf8');
    for (const push of source.matchAll(/router\.push\(\{\s*name:\s*([^,}]+)/g)) {
      for (const literal of push[1]!.matchAll(/'([a-z][a-z-]*)'/g)) {
        assert.ok(names.has(literal[1]!), `${file}: route "${literal[1]}" tidak ada`);
        checked++;
      }
    }
  }
  assert.ok(checked >= 10, `hanya ${checked} tujuan terperiksa`);
});

test('link "Forgot Password?" di sign in admin menuju halaman lupa password ADMIN', () => {
  const source = readFileSync(new URL('../src/modules/admin/views/AdminSigninView.vue', import.meta.url), 'utf8');
  assert.match(source, /name:\s*'admin-forgot-password'/);
  assert.doesNotMatch(source, /name:\s*'auth-forgot-password'/);
});

test('menu Admin: route daftar admin di bawah /admin, tepat sebelum admins/new, judul "Admin"', () => {
  const parent = adminRoutes.find((r) => r.path === '/admin');
  const kids = parent?.children ?? [];
  const list = kids.find((c) => c.name === 'admin-admins');
  assert.ok(list);
  assert.equal(list.path, 'admins');
  assert.equal(list.meta?.title, 'Admin');
  assert.equal(kids.find((c) => c.name === 'admin-invite')?.path, 'admins/new');
  assert.ok(kids.indexOf(list) < kids.findIndex((c) => c.name === 'admin-invite'));
});

test('AdminSigninView membedakan akun nonaktif dari akun belum terverifikasi', () => {
  const source = readFileSync(new URL('../src/modules/admin/views/AdminSigninView.vue', import.meta.url), 'utf8');
  assert.match(source, /isAccountDeactivated\(info\)/);
  assert.match(source, /offersVerificationResend\(info\)/);
  assert.ok(source.indexOf('isAccountDeactivated(info)') < source.indexOf('offersVerificationResend(info)'));
});

test('service daftar admin mengirim page & size ke /admin/admins', () => {
  const source = readFileSync(new URL('../src/modules/admin/services/adminManagement.service.ts', import.meta.url), 'utf8');
  assert.match(source, /'\/admin\/admins'[\s\S]*params:\s*\{\s*page,\s*size\s*\}/);
  assert.match(source, /deactivate/);
  assert.match(source, /activate/);
});

test('menu User memakai halaman daftar user (bukan Coming soon) dengan path /admin/users', () => {
  const parent = adminRoutes.find((r) => r.path === '/admin');
  const route = parent?.children?.find((c) => c.name === 'admin-users');
  assert.ok(route);
  assert.equal(route.path, 'users');
  assert.equal(route.meta?.title, 'User');
  assert.match(String(route.component), /AdminUserListView/);
  // menu lain yang belum dibuat tetap Coming soon
  for (const name of ['admin-user-logs', 'admin-payments', 'admin-ai-token-usage']) {
    assert.match(String(parent?.children?.find((c) => c.name === name)?.component), /ComingSoonView/, name);
  }
});
