// frontend/src/modules/testrepository/services/testFolder.service.ts
import apiClient from '@/shared/services/apiClient';
import type { CreateTestFolderRequest, TestFolderResponse } from '../types/testFolder.types';

export const testFolderService = {
  /** GET /api/projects/{projectId}/test-folders -- flat list, semua member boleh akses. */
  async listFolders(projectId: string): Promise<TestFolderResponse[]> {
    const { data } = await apiClient.get<TestFolderResponse[]>(`/projects/${projectId}/test-folders`);
    return data;
  },

  /**
   * POST /api/projects/{projectId}/test-folders -- HANYA boleh OWNER project
   * (requirement #3). Backend balas 403 kalau bukan owner, 404 kalau
   * parentId tidak valid utk project ini -- caller wajib try/catch.
   */
  async createFolder(projectId: string, payload: CreateTestFolderRequest): Promise<TestFolderResponse> {
    const { data } = await apiClient.post<TestFolderResponse>(`/projects/${projectId}/test-folders`, payload);
    return data;
  },
};
