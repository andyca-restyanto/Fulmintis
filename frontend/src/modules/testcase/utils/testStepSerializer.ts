// frontend/src/modules/testcase/utils/testStepSerializer.ts

export interface TestStepRow {
  action: string;
  expectedResult: string;
}

export function createEmptyStepRow(): TestStepRow {
  return { action: '', expectedResult: '' };
}

/**
 * Backend (`test_case.test_step` & `test_case.expected_result`) cuma
 * punya 1 kolom TEXT polos masing-masing -- BUKAN tabel/array step
 * terpisah. Jadi baris-baris step yang diedit sebagai tabel di UI
 * (TestStepsEditor.vue) di-"gepengkan" jadi teks bernomor sebelum dikirim
 * ke API, nomor step & expected result-nya SELALU align (baris ke-N di
 * satu kolom = baris ke-N di kolom lain).
 * <p>
 * Baris yang action & expectedResult-nya SAMA-SAMA kosong di-skip (tidak
 * ikut ke-serialize), supaya baris kosong bawaan form tidak ikut terkirim.
 */
export function serializeSteps(rows: TestStepRow[]): { testStep: string | null; expectedResult: string | null } {
  const filledRows = rows.filter((row) => row.action.trim() || row.expectedResult.trim());

  if (filledRows.length === 0) {
    return { testStep: null, expectedResult: null };
  }

  const testStep = filledRows.map((row, index) => `${index + 1}. ${row.action.trim()}`).join('\n');
  const expectedResult = filledRows.map((row, index) => `${index + 1}. ${row.expectedResult.trim()}`).join('\n');

  return { testStep, expectedResult };
}

// Angka urut "1. ", "2. " dst yang ditambahkan serializeSteps() di depan
// tiap baris -- di-strip lagi di sini pas parse balik.
const STEP_NUMBER_PREFIX = /^\d+\.\s?/;

/**
 * Kebalikan dari serializeSteps() -- dipakai EditTestCaseModal.vue utk
 * pre-fill tabel step dari testStep/expectedResult (TEXT flat) yang sudah
 * tersimpan di backend. testStep & expectedResult di-split per baris,
 * nomor urut "N. " di depan tiap baris di-strip, lalu di-pasangkan index
 * per index (baris ke-N di 1 kolom = baris ke-N di kolom lain, sama seperti
 * asumsi serializeSteps()).
 * <p>
 * Kalau jumlah baris testStep & expectedResult beda (mis. data lama yang
 * diedit manual di database), sisi yang lebih pendek di-"pad" string kosong
 * supaya tabel tetap align, tidak ada baris yang hilang.
 */
export function parseSteps(testStep: string | null, expectedResult: string | null): TestStepRow[] {
  const actionLines = (testStep ?? '').split('\n').map((line) => line.trim()).filter(Boolean);
  const resultLines = (expectedResult ?? '').split('\n').map((line) => line.trim()).filter(Boolean);

  const rowCount = Math.max(actionLines.length, resultLines.length);
  if (rowCount === 0) {
    return [createEmptyStepRow()];
  }

  const rows: TestStepRow[] = [];
  for (let i = 0; i < rowCount; i++) {
    rows.push({
      action: (actionLines[i] ?? '').replace(STEP_NUMBER_PREFIX, ''),
      expectedResult: (resultLines[i] ?? '').replace(STEP_NUMBER_PREFIX, ''),
    });
  }

  return rows;
}
