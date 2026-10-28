// frontend/src/modules/project/services/projectCollaborator.service.ts
import apiClient from '@/shared/services/apiClient';
import type {
  AddProjectCollaboratorRequest,
  ProjectCollaboratorResponse,
  UpdateProjectCollaboratorRequest,
  UserSearchResult,
} from '../types/collaborator.types';

export const projectCollaboratorService = {
  /** GET /api/projects/{id}/collaborators -- boleh dilihat semua member. */
  async listCollaborators(projectId: string): Promise<ProjectCollaboratorResponse[]> {
    const { data } = await apiClient.get<ProjectCollaboratorResponse[]>(
      `/projects/${projectId}/collaborators`
    );
    return data;
  },

  /**
   * GET /api/projects/{id}/collaborators/search?email= -- cocok PERSIS dengan
   * email (bukan pencarian sebagian) dan hanya user yang sudah verified;
   * balas paling banyak 1 hasil, kosong kalau tidak ada / sudah jadi member.
   * HANYA OWNER project yang boleh (403 kalau bukan, 404 kalau bukan member).
   * Dibatasi rate limit di backend (429).
   */
  async searchUsersToAdd(projectId: string, email: string): Promise<UserSearchResult[]> {
    const { data } = await apiClient.get<UserSearchResult[]>(
      `/projects/${projectId}/collaborators/search`,
      { params: { email } }
    );
    return data;
  },

  /**
   * POST /api/projects/{id}/collaborators -- tambah team member (requirement
   * #2), HANYA OWNER yang boleh (requirement #3). Balas 409 kalau user itu
   * sudah jadi member, 404 kalau email belum terdaftar -- caller try/catch.
   */
  async addCollaborator(
    projectId: string,
    payload: AddProjectCollaboratorRequest
  ): Promise<ProjectCollaboratorResponse> {
    const { data } = await apiClient.post<ProjectCollaboratorResponse>(
      `/projects/${projectId}/collaborators`,
      payload
    );
    return data;
  },

  /**
   * PATCH /api/projects/{id}/collaborators/{collaborationId} -- ubah role
   * member, OWNER-only. 404 kalau id bukan milik project ini; 409 kalau ini
   * akan menghilangkan OWNER terakhir. `collaborationId` = `id` (number/Long)
   * di ProjectCollaboratorResponse.
   */
  async updateCollaboratorRole(
    projectId: string,
    collaborationId: number,
    payload: UpdateProjectCollaboratorRequest
  ): Promise<ProjectCollaboratorResponse> {
    const { data } = await apiClient.patch<ProjectCollaboratorResponse>(
      `/projects/${projectId}/collaborators/${collaborationId}`,
      payload
    );
    return data;
  },

  /** DELETE /api/projects/{id}/collaborators/{collaborationId} -- OWNER-only, 204. 409 kalau OWNER terakhir. */
  async removeCollaborator(projectId: string, collaborationId: number): Promise<void> {
    await apiClient.delete(`/projects/${projectId}/collaborators/${collaborationId}`);
  },
};
