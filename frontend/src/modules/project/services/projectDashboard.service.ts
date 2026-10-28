// frontend/src/modules/project/services/projectDashboard.service.ts
import apiClient from '@/shared/services/apiClient';
import type { ProjectDashboardResponse } from '../types/projectDashboard.types';

export const projectDashboardService = {
  /**
   * GET /api/projects/{projectId}/dashboard -- semua angka dashboard project
   * dalam satu panggilan (OWNER maupun COLLABORATOR). Throws (404) kalau
   * project tidak ada / user bukan member -- caller wajib wrap di try/catch.
   */
  async getProjectDashboard(projectId: string): Promise<ProjectDashboardResponse> {
    const { data } = await apiClient.get<ProjectDashboardResponse>(`/projects/${projectId}/dashboard`);
    return data;
  },
};
