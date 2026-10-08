// frontend/src/modules/project/composables/useProjectDetail.ts
import { inject, type InjectionKey, type Ref } from 'vue';
import type { ProjectDetail } from '../types/project.types';

export const PROJECT_DETAIL_KEY: InjectionKey<Ref<ProjectDetail | null>> = Symbol('project-detail');

/**
 * Dipakai di view-view anak dari ProjectLayoutView (route
 * /projects/:projectId/**, misal ProjectDashboardView) utk ambil detail
 * project yang sedang dibuka TANPA fetch ulang -- ProjectLayoutView sudah
 * fetch sekali lewat GET /api/projects/{id} (project.service.ts) lalu
 * provide hasilnya lewat provide/inject di sini.
 */
export function useProjectDetail(): Ref<ProjectDetail | null> {
  const project = inject(PROJECT_DETAIL_KEY);
  if (!project) {
    throw new Error(
      'useProjectDetail() harus dipanggil di dalam view yang dirender lewat ProjectLayoutView (route /projects/:projectId/**)'
    );
  }
  return project;
}
