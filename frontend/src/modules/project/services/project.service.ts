// frontend/src/modules/project/services/project.service.ts
import apiClient from '@/shared/services/apiClient';
import type {
  CreateProjectRequest,
  ProjectDetail,
  ProjectListItem,
  ProjectResponse,
} from '../types/project.types';

export const projectService = {
  async createProject(payload: CreateProjectRequest): Promise<ProjectResponse> {
    const { data } = await apiClient.post<ProjectResponse>('/projects', payload);
    return data;
  },

  /** GET /api/projects -- daftar project milik user login (OWNER maupun COLLABORATOR). */
  async listMyProjects(): Promise<ProjectListItem[]> {
    const { data } = await apiClient.get<ProjectListItem[]>('/projects');
    return data;
  },

  /**
   * GET /api/projects/{projectId} -- detail project + menu navigasi yang
   * boleh diakses user ini. Throws (404) kalau project tidak ada / user
   * bukan member -- caller wajib wrap di try/catch.
   */
  async getProjectDetail(projectId: string): Promise<ProjectDetail> {
    const { data } = await apiClient.get<ProjectDetail>(`/projects/${projectId}`);
    return data;
  },
};
