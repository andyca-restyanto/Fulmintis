// filepath: /frontend/tests/adminEmailAllowlist.test.ts
import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  isEnabledButEmpty,
  normalizeRuleValue,
  ruleTypeLabel,
  validateRuleValue,
} from '../src/modules/admin/utils/adminEmailAllowlist.ts';
import { EMAIL_NOT_ALLOWED_CODE, mapRegisterError } from '../src/modules/auth/utils/registerErrors.ts';

test('normalisasi: trim + huruf kecil; domain tanpa @ diberi @', () => {
  assert.equal(normalizeRuleValue('EMAIL', '  Budi@Perusahaan.COM '), 'budi@perusahaan.com');
  assert.equal(normalizeRuleValue('DOMAIN', 'Perusahaan.com'), '@perusahaan.com');
  assert.equal(normalizeRuleValue('DOMAIN', '@perusahaan.com'), '@perusahaan.com');
  assert.equal(normalizeRuleValue('DOMAIN', '   '), '');
});

test('validasi email', () => {
  assert.equal(validateRuleValue('EMAIL', 'budi@perusahaan.com'), '');
  assert.equal(validateRuleValue('EMAIL', 'a.b+c@mail.x-y.co.id'), '');
  assert.notEqual(validateRuleValue('EMAIL', 'bukan-email'), '');
  assert.notEqual(validateRuleValue('EMAIL', 'a@b'), '');
  assert.notEqual(validateRuleValue('EMAIL', 'a b@x.com'), '');
  assert.equal(validateRuleValue('EMAIL', '   '), 'Nilai wajib diisi');
});

test('validasi domain', () => {
  assert.equal(validateRuleValue('DOMAIN', '@perusahaan.com'), '');
  assert.equal(validateRuleValue('DOMAIN', 'perusahaan.com'), '');
  assert.notEqual(validateRuleValue('DOMAIN', '@x'), '');
  assert.notEqual(validateRuleValue('DOMAIN', '@'), '');
  assert.notEqual(validateRuleValue('DOMAIN', 'a@b.com'), '');
});

test('nilai lebih dari 255 karakter ditolak', () => {
  assert.equal(validateRuleValue('EMAIL', 'a'.repeat(250) + '@b.com'), 'Nilai maksimal 255 karakter');
});

test('label jenis dan peringatan aktif-tapi-kosong', () => {
  assert.equal(ruleTypeLabel('EMAIL'), 'Email');
  assert.equal(ruleTypeLabel('DOMAIN'), 'Domain');
  assert.equal(isEnabledButEmpty(true, 0), true);
  assert.equal(isEnabledButEmpty(true, 2), false);
  assert.equal(isEnabledButEmpty(false, 0), false);
});

test('register 403 EMAIL_NOT_ALLOWED tampil di field email, bukan pesan umum', () => {
  const v = mapRegisterError(403, { code: EMAIL_NOT_ALLOWED_CODE, message: 'Email ini tidak diizinkan untuk mendaftar. Hubungi admin.' });
  assert.deepEqual(v.fieldErrors, { email: 'Email ini tidak diizinkan untuk mendaftar. Hubungi admin.' });
  assert.equal(v.general, '');
});

test('register: 403 tanpa kode itu, 400, 409, dan lainnya tetap seperti sebelumnya', () => {
  assert.deepEqual(mapRegisterError(403, { message: 'x' }), { fieldErrors: {}, general: 'x' });
  assert.deepEqual(mapRegisterError(400, { errors: { email: 'e' } }), { fieldErrors: { email: 'e' }, general: '' });
  assert.deepEqual(mapRegisterError(409, undefined), { fieldErrors: {}, general: 'Email sudah terdaftar' });
  assert.deepEqual(mapRegisterError(500, undefined), { fieldErrors: {}, general: 'Terjadi kesalahan, coba lagi' });
});
