// frontend/src/modules/automation/types/automation.types.ts

// ---- Match 100% dgn dto backend: modules/automation/dto/* ----
// Semua endpoint di bawah /api/projects/{projectId}/automation. OWNER mengatur setup, semua member boleh generate.

export type AutomationFramework = 'PLAYWRIGHT' | 'CYPRESS' | 'SELENIUM';
export type AutomationLanguage = 'JAVA' | 'JAVASCRIPT' | 'TYPESCRIPT' | 'PYTHON';
export type AutomationPattern = 'PAGE_OBJECT_MODEL' | 'SIMPLE';
// QUEUED -> RUNNING -> SUCCEEDED | FAILED (AutomationGenerationStatus di backend)
export type AutomationGenerationStatus = 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED';

/**
 * AutomationOptionsResponseDTO -- GET /options. SATU sumber kebenaran: matriks `compatibility` dari backend
 * menentukan kombinasi framework x bahasa yang valid; FE TIDAK menulis ulang aturannya.
 */
export interface AutomationOptions {
  frameworks: AutomationFramework[];
  languages: AutomationLanguage[];
  /** framework -> bahasa yang valid untuknya. */
  compatibility: Record<AutomationFramework, AutomationLanguage[]>;
  patterns: AutomationPattern[];
}

/** AutomationSetupResponseDTO -- GET/PUT /setup. configured=false = belum diatur (semua field lain null). */
export interface AutomationSetup {
  configured: boolean;
  framework: AutomationFramework | null;
  language: AutomationLanguage | null;
  pattern: AutomationPattern | null;
  structureNotes: string | null;
  updatedAt: string | null;
  updatedBy: string | null;
}

/** AutomationSetupRequestDTO -- PUT /setup (OWNER, upsert; maksimal 1 setup per project). */
export interface SaveAutomationSetupRequest {
  framework: AutomationFramework;
  language: AutomationLanguage;
  /** Kosong = PAGE_OBJECT_MODEL. */
  pattern?: AutomationPattern;
  /** Maks. 4000 karakter. */
  structureNotes?: string;
}

/** GenerateAutomationRequestDTO -- POST /generations. */
export interface GenerateAutomationRequest {
  testCaseIds: string[];
}

/** AutomationGenerationCreatedDTO -- respons 202 POST /generations. */
export interface AutomationGenerationCreated {
  id: string;
  status: AutomationGenerationStatus;
}

/** AutomationFileDTO -- path selalu relatif, pemisah "/". */
export interface AutomationFile {
  path: string;
  content: string;
}

/** AutomationGenerationSummaryDTO -- satu baris riwayat (TANPA isi berkas). */
export interface AutomationGenerationSummary {
  id: string;
  status: AutomationGenerationStatus;
  framework: AutomationFramework;
  language: AutomationLanguage;
  pattern: AutomationPattern;
  testCaseCount: number;
  requestedBy: string;
  createdAt: string;
  finishedAt: string | null;
  /** Terisi hanya kalau FAILED. */
  errorCode: string | null;
  errorMessage: string | null;
}

/** AutomationGenerationResponseDTO -- detail. `files` berisi hasil hanya kalau SUCCEEDED, selain itu []. */
export interface AutomationGeneration {
  id: string;
  status: AutomationGenerationStatus;
  framework: AutomationFramework;
  language: AutomationLanguage;
  pattern: AutomationPattern;
  testCaseCount: number;
  requestedBy: string;
  createdAt: string;
  finishedAt: string | null;
  files: AutomationFile[];
  notes: string | null;
  errorCode: string | null;
  errorMessage: string | null;
}

export type AutomationTier = 'FREE' | 'VIP';

/**
 * AutomationUsageResponseDTO -- GET /usage.
 * aiEnabled=false = provider AI belum dikonfigurasi: tombol Generate harus nonaktif
 * (backend tetap menolak dgn AI_UNAVAILABLE). Tidak memuat nama provider/model.
 */
export interface AutomationUsage {
  aiEnabled: boolean;
  tier: AutomationTier;
  maxTestCasesPerGeneration: number;
  /** 0 = tanpa batas. */
  dailyLimit: number;
  usedToday: number;
}

/**
 * Body error dari modul automation (AutomationApiException lewat GlobalExceptionHandler).
 * errorCode: AUTOMATION_SETUP_REQUIRED | TOO_MANY_TEST_CASES | INVALID_TEST_CASE_SELECTION |
 * UNSUPPORTED_COMBINATION | SETUP_CONFLICT | GENERATION_IN_PROGRESS | GENERATION_NOT_READY |
 * GENERATION_NOT_FOUND | AI_PLAN_LIMIT_REACHED | AI_UNAVAILABLE.
 * Respons 429 dari rate limit umum TIDAK punya errorCode.
 */
export interface AutomationApiError {
  timestamp?: string;
  status?: number;
  message?: string;
  errorCode?: string;
}
