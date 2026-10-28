// frontend/src/modules/testrun/types/testRun.types.ts
import type { TestCasePriority, TestCaseType, TestCaseScenarioType } from '@/modules/testcase';

// ---- Match 100% dgn enum backend (TestRunStatus/TestResultStatus) ----
export type TestRunStatus = 'PENDING' | 'RUNNING' | 'FINISHED';
export type TestResultStatus = 'NEW' | 'PASSED' | 'FAILED' | 'PENDING' | 'BLOCKED';
// Match 100% dgn enum TestStepResultStatus (backend) -- hasil per-step.
export type TestStepResultStatus = 'NEW' | 'PASSED' | 'FAILED';

// ---- Match 100% dgn CreateTestRunRequestDTO (backend) ----
// Sesuai flow yang diminta: title & description diisi di step "Details",
// testCaseIds diisi di step "Select Cases" -- TAPI keduanya dikirim dalam
// 1x submit "Create Test Run" (lihat CreateTestRunModal.vue).
export interface CreateTestRunRequest {
  title: string;
  description?: string | null;
  testCaseIds?: string[];
}

// ---- Match 100% dgn AddTestCasesToTestRunRequestDTO (backend) ----
export interface AddTestCasesToTestRunRequest {
  testCaseIds: string[];
}

// ---- Match 100% dgn SyncTestRunCasesRequestDTO (backend) ----
// Daftar AKHIR test case yang harus ada di run (checklist Manage Cases).
// Boleh kosong (= kosongkan run), tapi TIDAK boleh null/undefined.
export interface SyncTestRunCasesRequest {
  testCaseIds: string[];
}

// ---- Match 100% dgn UpdateTestResultRequestDTO (backend) ----
export interface UpdateTestResultRequest {
  status: TestResultStatus;
  comment?: string | null;
  // OPSIONAL: undefined/tidak dikirim = hasil per-step yang tersimpan TIDAK
  // diubah; array kosong = hapus semua tanda step.
  stepResults?: TestStepResultStatus[];
  // OPSIONAL: versi test result yang terakhir dilihat klien (dari
  // TestResultResponse.version). Kalau dikirim dan ternyata sudah berubah
  // (tester lain menyimpan lebih dulu) backend membalas 409 -- bukan menimpa
  // diam-diam. undefined = tanpa cek konflik.
  version?: number;
}

// ---- Match 100% dgn TestResultResponseDTO (backend) ----
export interface TestResultResponse {
  id: string;
  testRunId: string;
  testCaseId: string;
  testCaseTitle: string;
  // Detail test case utk halaman Execute (di-denormalisasi backend, sama
  // seperti testCaseTitle). testCaseTestStep & testCaseExpectedResult
  // berformat "1. ...\n2. ..." -- parse pakai parseSteps() modul testcase.
  testCaseDescription: string | null;
  testCasePrecondition: string | null;
  testCaseTestStep: string | null;
  testCaseExpectedResult: string | null;
  // Utk sheet "All Results"/per-run di Export Excel & tabel Run Details
  // (kolom Priority/Type/Scenario) -- di-denormalisasi backend sama seperti
  // field testCase* lainnya.
  testCasePriority: TestCasePriority;
  testCaseType: TestCaseType;
  testCaseScenarioType: TestCaseScenarioType | null;
  status: TestResultStatus;
  // NULL kalau belum ada evidence yang di-upload. Kalau ada, langsung path
  // relatif API siap pakai sbg href/src (lihat TestRunController backend).
  evidenceUrl: string | null;
  comment: string | null;
  // Hasil per-step, urutan = urutan step. Tidak pernah null (kosong = belum
  // ada yang ditandai); panjangnya bisa beda dgn jumlah step saat ini.
  stepResults: TestStepResultStatus[];
  // Versi optimistic lock (@Version di backend), naik tiap kali baris ini
  // disimpan. Kirim balik di UpdateTestResultRequest.version.
  version: number;
  createdAt: string;
  createdBy: string;
  updatedAt: string | null;
  updatedBy: string | null;
}

// ---- Match 100% dgn TestRunResponseDTO (backend, listing ringkas) ----
export interface TestRunResponse {
  id: string;
  projectId: string;
  title: string;
  description: string | null;
  status: TestRunStatus;
  testCaseCount: number;
  // Breakdown status -- dipakai TestRunCard.vue utk progress bar & angka
  // "X passed / Y failed / Z blocked" tanpa perlu fetch detail penuh.
  passedCount: number;
  failedCount: number;
  blockedCount: number;
  createdAt: string;
  createdBy: string;
  updatedAt: string | null;
  updatedBy: string | null;
}

// ---- Match 100% dgn TestRunDetailResponseDTO (backend, detail + mapped cases) ----
export interface TestRunDetailResponse {
  id: string;
  projectId: string;
  title: string;
  description: string | null;
  status: TestRunStatus;
  testResults: TestResultResponse[];
  createdAt: string;
  createdBy: string;
  updatedAt: string | null;
  updatedBy: string | null;
}
