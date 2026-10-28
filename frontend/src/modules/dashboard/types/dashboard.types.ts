// frontend/src/modules/dashboard/types/dashboard.types.ts

export interface DashboardSummary {
  email: string;
  name: string | null; // nullable -- match DashboardSummaryDTO.name (requirement #4)
  message: string;
  userType: string;
  userTypeLabel: string;
}
