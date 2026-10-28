// filepath: /frontend/tests/adminAiLimits.test.ts
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import {
  AI_LIMIT_FIELDS, AI_LIMIT_TIERS, allLimitKeys, buildUpdateRequest, changedKeys, defaultValue, displayValue, fieldsOf,
  formFromLimits, isFormDirty, isOverridden, limitKey, limitLabel, overriddenKeys, parseLimitValue, readValue,
  unlimitedChanges, validateForm,
} from '../src/modules/admin/utils/adminAiLimits.ts';
import type { AiLimits, AiTierLimits } from '../src/modules/admin/types/admin-ai-limits.types.ts';

const tier = (daily: number, tcDaily: number): AiTierLimits => ({
  automation: { dailyLimit: daily, maxTestCases: 5, maxOutputTokens: 4096, timeoutSeconds: 120 },
  testcase: { dailyLimit: tcDaily, maxDrafts: 3, maxOutputTokens: 4096, timeoutSeconds: 90 },
});

// Bentuk persis respons GET /admin/ai-limits (bawaan kode backend).
function sample(): AiLimits {
  const tiers = { FREE: tier(3, 3), VIP_MONTHLY: tier(30, 15), VIP_YEARLY: tier(30, 15) };
  const source: Record<string, 'DATABASE' | 'DEFAULT'> = {};
  for (const key of allLimitKeys()) source[key] = 'DEFAULT';
  return {
    global: { dailyLimit: 0, quotaCircuitMinutes: 10 },
    tiers,
    source,
    defaults: { global: { dailyLimit: 0, quotaCircuitMinutes: 10 }, tiers: structuredClone(tiers) },
  };
}

test('3 tier terpisah: VIP Monthly dan VIP Yearly masing-masing punya kolom sendiri', () => {
  assert.deepEqual(AI_LIMIT_TIERS.map((t) => t.id), ['FREE', 'VIP_MONTHLY', 'VIP_YEARLY']);
  assert.equal(allLimitKeys().length, 2 + 8 * 3);
  assert.equal(new Set(allLimitKeys()).size, 26);
  assert.ok(allLimitKeys().includes('VIP_YEARLY.TESTCASE_MAX_DRAFTS'));
});

test('definisi field cocok dengan AiLimitKey.java (rentang & 0 = tanpa batas)', () => {
  const src = readFileSync(new URL('../../backend/src/main/java/com/example/app/shared/ai/AiLimitKey.java', import.meta.url), 'utf8');
  for (const f of AI_LIMIT_FIELDS) {
    const m = new RegExp(`${f.id}\\((true|false), ([\\d_]+), ([\\d_]+)\\)`).exec(src);
    assert.ok(m, `${f.id} tidak ada di backend`);
    assert.equal(m![1] === 'true', f.group === 'global', f.id);
    assert.equal(Number(m![2].replace(/_/g, '')), f.min, `${f.id} min`);
    assert.equal(Number(m![3].replace(/_/g, '')), f.max, `${f.id} max`);
    assert.equal(f.unlimitedAtZero, f.min === 0, `${f.id} unlimitedAtZero`);
  }
  assert.equal((src.match(/\(\s*(true|false), \d/g) ?? []).length, AI_LIMIT_FIELDS.length);
});

test('readValue dan formFromLimits membaca semua nilai; defaultValue dari defaults', () => {
  const l = sample();
  assert.equal(readValue(l, 'VIP_YEARLY', 'AUTOMATION_DAILY_LIMIT'), 30);
  assert.equal(readValue(l, 'FREE', 'TESTCASE_MAX_DRAFTS'), 3);
  assert.equal(readValue(l, 'GLOBAL', 'QUOTA_CIRCUIT_MINUTES'), 10);
  const form = formFromLimits(l);
  assert.equal(Object.keys(form).length, 26);
  assert.equal(form['FREE.AUTOMATION_DAILY_LIMIT'], '3');
  assert.equal(form['GLOBAL.GLOBAL_DAILY_LIMIT'], '0');
  assert.equal(defaultValue(l, 'VIP_MONTHLY.TESTCASE_DAILY_LIMIT'), 15);
  assert.throws(() => readValue(l, 'FREE', 'NGAWUR'));
});

test('parseLimitValue hanya menerima bilangan bulat tidak negatif', () => {
  assert.equal(parseLimitValue('10'), 10);
  assert.equal(parseLimitValue(' 007 '), 7);
  for (const bad of ['', ' ', '-1', '1.5', '1e3', 'abc', '1 000', '9999999999']) assert.equal(parseLimitValue(bad), null, bad);
});

test('validateForm: rentang per field, kosong, dan form awal valid', () => {
  const l = sample();
  const form = formFromLimits(l);
  assert.deepEqual(validateForm(form), {});
  form['FREE.AUTOMATION_TIMEOUT_SECONDS'] = '5';
  form['VIP_YEARLY.TESTCASE_MAX_DRAFTS'] = '51';
  form['GLOBAL.QUOTA_CIRCUIT_MINUTES'] = '';
  form['FREE.AUTOMATION_MAX_TEST_CASES'] = '0';
  const errors = validateForm(form);
  assert.deepEqual(Object.keys(errors).sort(), [
    'FREE.AUTOMATION_MAX_TEST_CASES', 'FREE.AUTOMATION_TIMEOUT_SECONDS', 'GLOBAL.QUOTA_CIRCUIT_MINUTES', 'VIP_YEARLY.TESTCASE_MAX_DRAFTS',
  ]);
  assert.match(errors['FREE.AUTOMATION_TIMEOUT_SECONDS'], /antara 10 dan 600/);
  assert.match(errors['GLOBAL.QUOTA_CIRCUIT_MINUTES'], /bilangan bulat/);
});

test('changedKeys/isFormDirty hanya menandai nilai yang berbeda', () => {
  const l = sample();
  const form = formFromLimits(l);
  assert.deepEqual(changedKeys(form, l), []);
  assert.equal(isFormDirty(form, l), false);
  form['VIP_YEARLY.AUTOMATION_DAILY_LIMIT'] = '100';
  assert.deepEqual(changedKeys(form, l), ['VIP_YEARLY.AUTOMATION_DAILY_LIMIT']);
  assert.equal(isFormDirty(form, l), true);
  form['VIP_YEARLY.AUTOMATION_DAILY_LIMIT'] = '030'; // sama secara numerik
  assert.equal(isFormDirty(form, l), false);
  form['FREE.TESTCASE_DAILY_LIMIT'] = 'abc'; // tidak valid tetap dihitung dirty agar tombol simpan menampilkan galat
  assert.equal(isFormDirty(form, l), true);
  assert.deepEqual(changedKeys(form, l), []);
});

test('nilai 0 pada batas harian wajib dikonfirmasi, pada parameter lain tidak ada konfirmasi (ditolak validasi)', () => {
  const l = sample();
  const form = formFromLimits(l);
  form['FREE.AUTOMATION_DAILY_LIMIT'] = '0';
  form['GLOBAL.GLOBAL_DAILY_LIMIT'] = '0'; // sudah 0 -> bukan perubahan
  assert.deepEqual(unlimitedChanges(form, l), ['FREE.AUTOMATION_DAILY_LIMIT']);
  form['VIP_YEARLY.AUTOMATION_DAILY_LIMIT'] = '100'; // naik biasa: tidak perlu konfirmasi
  form['FREE.AUTOMATION_MAX_TEST_CASES'] = '0';
  assert.deepEqual(unlimitedChanges(form, l), ['FREE.AUTOMATION_DAILY_LIMIT']);
  assert.ok(validateForm(form)['FREE.AUTOMATION_MAX_TEST_CASES']);
});

test('buildUpdateRequest: bentuk DTO persis kontrak, VIP Monthly/Yearly independen, menolak form tidak valid', () => {
  const l = sample();
  const form = formFromLimits(l);
  form['VIP_YEARLY.AUTOMATION_DAILY_LIMIT'] = '100';
  form['GLOBAL.GLOBAL_DAILY_LIMIT'] = '500';
  const req = buildUpdateRequest(form, false);
  assert.deepEqual(Object.keys(req).sort(), ['confirmUnlimited', 'global', 'tiers']);
  assert.deepEqual(req.global, { dailyLimit: 500, quotaCircuitMinutes: 10 });
  assert.deepEqual(Object.keys(req.tiers), ['FREE', 'VIP_MONTHLY', 'VIP_YEARLY']);
  assert.equal(req.tiers.VIP_YEARLY.automation.dailyLimit, 100);
  assert.equal(req.tiers.VIP_MONTHLY.automation.dailyLimit, 30);
  assert.deepEqual(Object.keys(req.tiers.FREE.automation).sort(), ['dailyLimit', 'maxOutputTokens', 'maxTestCases', 'timeoutSeconds']);
  assert.deepEqual(Object.keys(req.tiers.FREE.testcase).sort(), ['dailyLimit', 'maxDrafts', 'maxOutputTokens', 'timeoutSeconds']);
  assert.equal(req.confirmUnlimited, false);
  assert.equal(buildUpdateRequest(form, true).confirmUnlimited, true);
  form['FREE.TESTCASE_TIMEOUT_SECONDS'] = '1';
  assert.throws(() => buildUpdateRequest(form, false));
});

test('sumber nilai: isOverridden/overriddenKeys', () => {
  const l = sample();
  assert.deepEqual(overriddenKeys(l), []);
  l.source['FREE.AUTOMATION_DAILY_LIMIT'] = 'DATABASE';
  assert.equal(isOverridden(l, 'FREE.AUTOMATION_DAILY_LIMIT'), true);
  assert.equal(isOverridden(l, 'FREE.TESTCASE_DAILY_LIMIT'), false);
  assert.deepEqual(overriddenKeys(l), ['FREE.AUTOMATION_DAILY_LIMIT']);
});

test('teks tampilan: 0 pada batas harian = Tanpa batas; label terbaca', () => {
  assert.equal(displayValue('AUTOMATION_DAILY_LIMIT', 0), 'Tanpa batas');
  assert.equal(displayValue('AUTOMATION_DAILY_LIMIT', 3), '3');
  assert.equal(displayValue('AUTOMATION_MAX_OUTPUT_TOKENS', 8192), (8192).toLocaleString('id-ID'));
  assert.equal(limitLabel('VIP_YEARLY.TESTCASE_DAILY_LIMIT'), 'VIP Yearly, Test case: Generate per hari (per user)');
  assert.equal(limitLabel('GLOBAL.GLOBAL_DAILY_LIMIT'), 'Generate per hari (seluruh sistem)');
  assert.equal(fieldsOf('global').length, 2);
  assert.equal(limitKey('FREE', 'X'), 'FREE.X');
});

test('route, menu, dan view terpasang', () => {
  const routes = readFileSync(new URL('../src/modules/admin/routes.ts', import.meta.url), 'utf8');
  assert.match(routes, /path: 'ai-limits',\s*name: 'admin-ai-limits',\s*component: \(\) => import\('\.\/views\/AdminAiLimitView\.vue'\)/);
  const view = readFileSync(new URL('../src/modules/admin/views/AdminAiLimitView.vue', import.meta.url), 'utf8');
  assert.match(view, /confirm-label="'unlimited'|confirm === 'unlimited'/);
  assert.match(view, /adminAiLimitService\.reset\(keys\)/);
  const svc = readFileSync(new URL('../src/modules/admin/services/adminAiLimit.service.ts', import.meta.url), 'utf8');
  assert.match(svc, /'\/admin\/ai-limits'/);
  assert.match(svc, /indexes: null/);
});

test('nilai bawaan di luar rentang yang TIDAK diubah tidak menghalangi simpan; yang diubah tetap diperiksa', async () => {
  const m = await import('../src/modules/admin/utils/adminAiLimits.ts');
  const tier = (d: number, timeout: number) => ({
    automation: { dailyLimit: d, maxTestCases: 5, maxOutputTokens: 4096, timeoutSeconds: timeout },
    testcase: { dailyLimit: d, maxDrafts: 3, maxOutputTokens: 4096, timeoutSeconds: 90 },
  });
  // timeout 5 detik (dari environment) < min 10 pada form
  const limits = {
    global: { dailyLimit: 0, quotaCircuitMinutes: 10 },
    tiers: { FREE: tier(3, 5), VIP_MONTHLY: tier(30, 180), VIP_YEARLY: tier(30, 180) },
  };
  const form = m.formFromLimits(limits as never);
  assert.notDeepEqual(m.validateForm(form), {}); // tanpa limits: diblokir (perilaku lama)
  assert.deepEqual(m.validateForm(form, limits as never), {});
  form['FREE.AUTOMATION_DAILY_LIMIT'] = '10';
  assert.deepEqual(m.validateForm(form, limits as never), {});
  assert.equal(m.buildUpdateRequest(form, false, limits as never).tiers.FREE.automation.dailyLimit, 10);
  form['FREE.AUTOMATION_TIMEOUT_SECONDS'] = '7'; // diubah tetapi masih di luar rentang
  assert.ok(m.validateForm(form, limits as never)['FREE.AUTOMATION_TIMEOUT_SECONDS']);
});
