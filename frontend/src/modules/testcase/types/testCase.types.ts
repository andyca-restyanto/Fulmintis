// frontend/src/modules/testcase/types/testCase.types.ts

// ---- Match 100% dgn enum backend (TestCasePriority/TestCaseType/TestCaseScenarioType/TestCaseStatus) ----
export type TestCasePriority = 'HIGHEST' | 'HIGH' | 'MEDIUM' | 'LOW';
export type TestCaseType = 'MANUAL' | 'AUTOMATION';
export type TestCaseScenarioType = 'POSITIVE' | 'NEGATIVE';
// Requirement tambahan #2 & #4 -- ACTIVE = normal, ARCHIVED = hasil "delete"
// (soft delete), cuma boleh dilihat OWNER project (lihat TestCasePanel.vue
// & ArchivedTestCasesModal.vue).
export type TestCaseStatus = 'ACTIVE' | 'ARCHIVED';

// ---- Match 100% dgn CreateTestCaseRequestDTO (backend) ----
export interface CreateTestCaseRequest {
  folderId: string;
  title: string;
  priority: TestCasePriority;
  type: TestCaseType;
  // Semua field di bawah ini opsional (lihat CreateTestCaseRequestDTO backend).
  scenarioType?: TestCaseScenarioType | null;
  description?: string | null;
  objective?: string | null;
  precondition?: string | null;
  testStep?: string | null;
  expectedResult?: string | null;
}

// ---- Match 100% dgn UpdateTestCaseRequestDTO (backend) ----
// Requirement tambahan #1: edit test case. Struktur SENGAJA sama persis dgn
// CreateTestCaseRequest (folderId tetap wajib -- backend Luhut mengizinkan
// pindah folder saat edit), supaya EditTestCaseModal.vue bisa reuse pola
// form yang sama dgn CreateTestCaseModal.vue.
export interface UpdateTestCaseRequest {
  folderId: string;
  title: string;
  priority: TestCasePriority;
  type: TestCaseType;
  scenarioType?: TestCaseScenarioType | null;
  description?: string | null;
  objective?: string | null;
  precondition?: string | null;
  testStep?: string | null;
  expectedResult?: string | null;
}

// ---- Match 100% dgn TestCaseResponseDTO (backend) ----
export interface TestCaseResponse {
  id: string;
  projectId: string;
  folderId: string;
  title: string;
  priority: TestCasePriority;
  type: TestCaseType;
  scenarioType: TestCaseScenarioType | null;
  description: string | null;
  objective: string | null;
  precondition: string | null;
  testStep: string | null;
  expectedResult: string | null;
  // Requirement tambahan #2 & #4 -- status ACTIVE|ARCHIVED + siapa/kapan
  // di-archive. archivedAt/archivedBy selalu null selama status ACTIVE.
  status: TestCaseStatus;
  archivedAt: string | null; // ISO date-time string
  archivedBy: string | null;
  createdAt: string;
  createdBy: string;
  updatedAt: string | null;
  updatedBy: string | null;
}

// ---- Match 100% dgn query param GET /test-cases & GET /test-cases/archived (TestCaseSearchCriteria backend) ----
export interface TestCaseSearchParams {
  folderId?: string;
  keyword?: string;
  priority?: TestCasePriority;
  type?: TestCaseType;
  scenarioType?: TestCaseScenarioType;
}
