// frontend/src/modules/testcase/types/testCaseImport.types.ts
import type { TestCasePriority, TestCaseScenarioType, TestCaseType } from './testCase.types';

// ---- Match 100% dgn dto backend: modules/testcase/dto/* (import Excel) ----
// Endpoint:
//   POST /api/projects/{projectId}/test-cases/import?folderId=&dryRun=true|false  (multipart, field "file")
//   GET  /api/projects/{projectId}/test-cases/import/template

/** ImportColumnMappingDTO -- satu pemetaan kolom Excel -> field sistem. */
export interface ImportColumnMapping {
  /** Teks header persis seperti tertulis di file Excel user. */
  excelColumn: string;
  /** title | priority | type | scenarioType | description | objective | precondition | testStep | expectedResult */
  field: string;
}

/** ImportIssueDTO -- satu error/peringatan. */
export interface ImportIssue {
  /** Nomor baris DI EXCEL (header = baris 1). */
  row: number;
  /** Nama kolom menurut template (mis. "Priority", "Test step"). */
  column: string;
  message: string;
}

/** ImportPreviewRowDTO -- satu baris valid pada pratinjau. */
export interface ImportPreviewRow {
  row: number;
  title: string;
  priority: TestCasePriority;
  type: TestCaseType;
  /** null kalau kolom Scenario type kosong. */
  scenarioType: TestCaseScenarioType | null;
  stepCount: number;
}

/**
 * TestCaseImportResultDTO.
 * - dryRun=true : tidak ada yang disimpan (importedCount = 0); baris bermasalah ada di `errors`, respons 200.
 * - dryRun=false tanpa error: SEMUA baris valid disimpan; importedCount = jumlah test case dibuat.
 * - dryRun=false dengan error: respons 400 dgn body yang sama; tidak ada yang tersimpan.
 */
export interface TestCaseImportResult {
  folderId: string;
  folderName: string;
  dryRun: boolean;
  /** Baris data tidak kosong (baris contoh dari template tidak dihitung). */
  totalRows: number;
  validRows: number;
  importedCount: number;
  columnMapping: ImportColumnMapping[];
  /** Header Excel yang tidak dikenali (diabaikan). */
  unmappedColumns: string[];
  errors: ImportIssue[];
  warnings: ImportIssue[];
  /** Maks 20 baris valid pertama. */
  preview: ImportPreviewRow[];
}
