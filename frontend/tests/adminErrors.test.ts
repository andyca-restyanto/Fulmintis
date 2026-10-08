// filepath: /frontend/tests/adminErrors.test.ts
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readAdminError, isAccountDeactivated, offersVerificationResend, NETWORK_ERROR_MESSAGE, DEFAULT_ERROR_MESSAGE } from '../src/modules/admin/utils/adminErrors.ts';

const axiosError = (status: number, data?: unknown) => ({ isAxiosError: true, response: { status, data } });

test('400 validasi -> fieldErrors dan pesan backend', () => {
  const info = readAdminError(axiosError(400, { message: 'Validasi gagal', errors: { confirmPassword: 'Tidak sama' } }));
  assert.equal(info.status, 400);
  assert.equal(info.message, 'Validasi gagal');
  assert.deepEqual(info.fieldErrors, { confirmPassword: 'Tidak sama' });
});

test('403/409 memakai pesan dari backend; fieldErrors kosong', () => {
  const closed = readAdminError(axiosError(403, { message: 'Pendaftaran admin sudah ditutup' }));
  assert.equal(closed.status, 403);
  assert.equal(closed.message, 'Pendaftaran admin sudah ditutup');
  assert.deepEqual(closed.fieldErrors, {});

  assert.equal(readAdminError(axiosError(409, { message: "Email 'x' sudah terdaftar" })).message, "Email 'x' sudah terdaftar");
});

test('field errors hanya diambil dari respons 400', () => {
  const info = readAdminError(axiosError(409, { message: 'Konflik', errors: { email: 'x' } }));
  assert.deepEqual(info.fieldErrors, {});
});

test('tanpa pesan di body -> pakai fallback; fallback bisa diganti', () => {
  assert.equal(readAdminError(axiosError(500, {})).message, DEFAULT_ERROR_MESSAGE);
  assert.equal(readAdminError(axiosError(401), 'Email atau password salah').message, 'Email atau password salah');
});

test('server tidak terjangkau atau bukan error axios -> pesan jaringan, status null', () => {
  for (const err of [{ isAxiosError: true }, new Error('boom'), null, undefined, 'teks']) {
    const info = readAdminError(err);
    assert.equal(info.status, null);
    assert.equal(info.message, NETWORK_ERROR_MESSAGE);
    assert.deepEqual(info.fieldErrors, {});
  }
});

test('403 ACCOUNT_DEACTIVATED dibedakan dari 403 belum terverifikasi', () => {
  const off = readAdminError(axiosError(403, { message: 'Akun dinonaktifkan', code: 'ACCOUNT_DEACTIVATED' }));
  assert.equal(off.code, 'ACCOUNT_DEACTIVATED');
  assert.equal(isAccountDeactivated(off), true);
  assert.equal(offersVerificationResend(off), false);

  const unverified = readAdminError(axiosError(403, { message: 'Belum diverifikasi' }));
  assert.equal(unverified.code, null);
  assert.equal(isAccountDeactivated(unverified), false);
  assert.equal(offersVerificationResend(unverified), true);

  // kode itu hanya berarti pada 403
  const other = readAdminError(axiosError(409, { message: 'x', code: 'ACCOUNT_DEACTIVATED' }));
  assert.equal(isAccountDeactivated(other), false);
  assert.equal(offersVerificationResend(other), false);
});
