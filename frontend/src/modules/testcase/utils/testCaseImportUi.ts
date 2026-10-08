// frontend/src/modules/testcase/utils/testCaseImportUi.ts
// Logika murni (tanpa Vue/axios) utk fitur import test case dari Excel -- dipisah supaya mudah diuji.

import type { TestCaseImportResult } from '../types/testCaseImport.types';

/** Batas sisi FE = batas backend (ImportFileGuard.MAX_FILE_BYTES). Backend tetap penjaga akhir. */
export const IMPORT_MAX_FILE_BYTES = 5 * 1024 * 1024;
export const IMPORT_MAX_ROWS = 500;

/** Nama field sistem (ImportColumnMapping.field) -> nama kolom di template. */
const FIELD_LABELS: Record<string, string> = {
  title: 'Title',
  priority: 'Priority',
  type: 'Test type',
  scenarioType: 'Scenario type',
  description: 'Description',
  objective: 'Objective',
  precondition: 'Pre-condition',
  testStep: 'Test step',
  expectedResult: 'Expected results',
};

export function fieldLabel(field: string): string {
  return FIELD_LABELS[field] ?? field;
}

export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/**
 * Pemeriksaan cepat di browser SEBELUM file dikirim (menghemat satu perjalanan
 * jaringan). Backend tetap memeriksa ulang ekstensi, ukuran, dan ISI file.
 * @returns pesan error, atau null kalau file boleh dikirim
 */
export function validateImportFile(file: { name: string; size: number }): string | null {
  if (!file.name.toLowerCase().endsWith('.xlsx')) {
    return 'Format file harus .xlsx (Excel). File .xls atau .csv tidak didukung.';
  }
  if (file.size <= 0) {
    return 'File Excel tidak boleh kosong.';
  }
  if (file.size > IMPORT_MAX_FILE_BYTES) {
    return `Ukuran file maksimal ${formatFileSize(IMPORT_MAX_FILE_BYTES)} (file ini ${formatFileSize(file.size)}).`;
  }
  return null;
}

/** Tombol "Import" boleh ditekan hanya kalau hasil pratinjau tidak punya error dan ada baris valid. */
export function canConfirmImport(result: TestCaseImportResult | null): boolean {
  return result !== null && result.errors.length === 0 && result.validRows > 0;
}

/** Teks notifikasi sukses: jumlah test case yang BENAR-BENAR diimpor (importedCount dari backend). */
export function importSuccessMessage(result: Pick<TestCaseImportResult, 'importedCount' | 'folderName'>): string {
  return `Import berhasil: ${result.importedCount} test case ditambahkan ke folder "${result.folderName}".`;
}

export type ImportFailure =
  | { kind: 'result'; result: TestCaseImportResult } // 400 dgn daftar error per baris (tidak ada yang tersimpan)
  | { kind: 'message'; message: string }; // file tidak bisa diproses / error lain

/**
 * Mengurai respons gagal dari backend. Ada DUA bentuk 400:
 *  - body hasil import lengkap (punya array `errors`) -> tampilkan daftar error per baris
 *  - { status, message } -> file tidak bisa diproses sama sekali (bukan .xlsx, kolom wajib tidak ada, dst)
 */
export function extractImportFailure(status: number | undefined, data: unknown, fallback: string): ImportFailure {
  if (status === 429) {
    return { kind: 'message', message: 'Terlalu banyak percobaan import. Tunggu sebentar lalu coba lagi.' };
  }
  if (data && typeof data === 'object') {
    const body = data as Partial<TestCaseImportResult> & { message?: unknown };
    if (Array.isArray(body.errors) && typeof body.totalRows === 'number') {
      return { kind: 'result', result: body as TestCaseImportResult };
    }
    if (typeof body.message === 'string' && body.message.trim() !== '') {
      return { kind: 'message', message: body.message };
    }
  }
  return { kind: 'message', message: fallback };
}
