// frontend/src/modules/dashboard/services/dashboard.service.ts
import apiClient from '@/shared/services/apiClient';
import type { DashboardSummary } from '../types/dashboard.types';

export const dashboardService = {
  async getSummary(): Promise<DashboardSummary> {
    // apiClient otomatis attach header "Authorization: Bearer <token>"
    // (lihat interceptor di shared/services/apiClient.ts). Kalau token tidak
    // ada / invalid / expired, backend balas 401 -> interceptor response
    // otomatis clear token & redirect ke /auth.
    const { data } = await apiClient.get<DashboardSummary>('/dashboard/summary');
    return data;
  },
};
