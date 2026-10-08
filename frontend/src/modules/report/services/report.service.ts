// frontend/src/modules/report/services/report.service.ts
import apiClient from '@/shared/services/apiClient';
import type { ReportOverviewResponse } from '../types/report.types';
import type { TestRunDetailResponse } from '@/modules/testrun';

export const reportService = {
  // GET /api/projects/{projectId}/report/overview -- data tab Overview
  // (lihat ReportController & ReportServiceImpl backend).
  async getOverview(projectId: string): Promise<ReportOverviewResponse> {
    const { data } = await apiClient.get<ReportOverviewResponse>(`/projects/${projectId}/report/overview`);
    return data;
  },

  // GET /api/projects/{projectId}/report/run-details -- data tab Run
  // Details: SEMUA test run project ini, masing-masing sudah lengkap dgn
  // testResults (utk expand per-baris di ReportsView.vue).
  async getRunDetails(projectId: string): Promise<TestRunDetailResponse[]> {
    const { data } = await apiClient.get<TestRunDetailResponse[]>(`/projects/${projectId}/report/run-details`);
    return data;
  },

  // GET /api/projects/{projectId}/report/export -- file .xlsx siap download
  // (lihat ReportController.exportExcel() backend). responseType 'blob'
  // WAJIB di sini -- tanpa ini axios mencoba parse body binary sbg JSON/text
  // dan file .xlsx yang dihasilkan jadi corrupt saat dibuka.
  async downloadExport(projectId: string): Promise<void> {
    const response = await apiClient.get(`/projects/${projectId}/report/export`, { responseType: 'blob' });

    // Nama file diambil dari header Content-Disposition yang dikirim
    // backend (ReportController.exportExcel(), pola "Test_Report_
    // yyyyMMdd_HHmmss.xlsx") supaya FE tidak perlu duplikasi format
    // timestamp-nya sendiri; fallback dipakai HANYA kalau header itu tidak
    // ada (mis. di-strip proxy) supaya tombol tetap berfungsi.
    const contentDisposition = String(response.headers['content-disposition'] ?? '');
    const filenameMatch = /filename="?([^";]+)"?/i.exec(contentDisposition);
    const filename = filenameMatch?.[1] ?? 'Test_Report.xlsx';

    const url = URL.createObjectURL(response.data as Blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  },
};
