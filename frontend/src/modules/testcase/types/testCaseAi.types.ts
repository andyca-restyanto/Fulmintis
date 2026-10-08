// frontend/src/modules/testcase/types/testCaseAi.types.ts
// Cocok 100% dengan DTO backend modules/testcase/dto (TestCaseAi*DTO, TestCaseDraft*DTO) -- dicocokkan terhadap JSON asli
// backend lewat uji kontrak. Hasil generate adalah DRAFT (belum tersimpan); test case baru dibuat hanya lewat commit.
import type { AiJobStatus } from '../../../shared/utils/aiPolling';
import type { TestCasePriority, TestCaseScenarioType, TestCaseType } from './testCase.types';

// QUEUED -> RUNNING -> SUCCEEDED | FAILED (TestCaseAiGenerationStatus di backend)
export type TestCaseAiGenerationStatus = AiJobStatus;

// Tier user di backend (AiTier). Dipakai hanya utk label; batas SELALU dibaca dari usage, bukan ditulis ulang di FE.
export type TestCaseAiTier = 'FREE' | 'VIP';

/** GET /test-cases/ai-generation/usage */
export interface TestCaseAiUsage {
  /** false = provider AI belum dikonfigurasi (tombol Generate nonaktif). */
  aiEnabled: boolean;
  tier: TestCaseAiTier;
  maxDraftsPerGeneration: number;
  maxRequirementChars: number;
  /** 0 = tanpa batas. */
  dailyLimit: number;
  usedToday: number;
  /** true = akun uji penyedia AI: data BISA dipakai penyedia utk memperbaiki produknya -> peringatkan user. */
  sandbox: boolean;
}

/** POST /test-cases/ai-generation */
export interface GenerateTestCasesRequest {
  folderId: string;
  requirement: string;
  /** Opsional: 1..maxDraftsPerGeneration; kosong = batas tier. */
  count?: number;
  /** Opsional: kosong = true. */
  includeNegative?: boolean;
}

/** Respons 202 POST /test-cases/ai-generation */
export interface TestCaseAiGenerationCreated {
  id: string;
  status: TestCaseAiGenerationStatus;
}

export interface TestCaseDraftStep {
  action: string;
  expected: string;
}

/** Satu draft hasil AI (BELUM tersimpan). */
export interface TestCaseDraft {
  tempId: string;
  title: string;
  priority: TestCasePriority;
  type: TestCaseType;
  scenarioType: TestCaseScenarioType | null;
  description: string | null;
  objective: string | null;
  precondition: string | null;
  steps: TestCaseDraftStep[];
  /** Judul sama (tanpa membedakan huruf besar/kecil) dgn test case aktif di folder tujuan. Hanya peringatan. */
  duplicateOfExisting: boolean;
}

/** GET /test-cases/ai-generation/{id} dan /pending */
export interface TestCaseAiGeneration {
  id: string;
  status: TestCaseAiGenerationStatus;
  folderId: string;
  /** null kalau folder sudah dihapus. */
  folderName: string | null;
  requestedCount: number;
  /** Bisa lebih sedikit dari requestedCount (mis. keluaran AI terpotong). */
  draftCount: number;
  /** true = keluaran AI terpotong; hanya draft yang lengkap yang ditampilkan. */
  truncated: boolean;
  committed: boolean;
  createdAt: string;
  finishedAt: string | null;
  /** Terisi hanya kalau SUCCEEDED dan belum di-commit/kedaluwarsa; selain itu []. */
  drafts: TestCaseDraft[];
  errorCode: string | null;
  errorMessage: string | null;
}

/** Satu draft hasil review yang akan disimpan: bentuknya sama dgn CreateTestCaseRequest tanpa folderId. */
export interface CommitTestCaseItem {
  title: string;
  priority: TestCasePriority;
  type: TestCaseType;
  scenarioType: TestCaseScenarioType | null;
  description: string | null;
  objective: string | null;
  precondition: string | null;
  /** Teks bernomor "1. ...\n2. ..." (format yang sama dgn form create). */
  testStep: string | null;
  expectedResult: string | null;
}

/** POST /test-cases/ai-generation/{id}/commit */
export interface CommitTestCasesRequest {
  folderId: string;
  testCases: CommitTestCaseItem[];
}

/** Respons 201 commit. */
export interface CommitTestCasesResult {
  savedCount: number;
  folderId: string;
  folderName: string;
}

/** Satu entri `errors` pada 400 INVALID_DRAFTS: index = posisi item pada daftar yang DIKIRIM. */
export interface CommitItemError {
  index: number;
  field: string;
  message: string;
}
