// frontend/src/modules/report/types/report.types.ts

// Match 100% dgn ReportOverviewResponseDTO (backend).
export interface ReportOverviewResponse {
  totalTestRuns: number;
  totalRunningTestRuns: number;
  totalExecutions: number;
  totalPassed: number;
  totalFailed: number;
  totalBlocked: number;
  totalPending: number;
  passRatePercentage: number;
  executionRatePercentage: number;
}
