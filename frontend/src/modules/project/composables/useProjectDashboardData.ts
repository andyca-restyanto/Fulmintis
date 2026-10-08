// frontend/src/modules/project/composables/useProjectDashboardData.ts
import { ref, watch, onMounted, type Ref } from 'vue';
import axios from 'axios';
import { projectDashboardService } from '../services/projectDashboard.service';
import type { ProjectDashboardResponse } from '../types/projectDashboard.types';

/**
 * Memuat data dashboard satu project. Dimuat ulang setiap view dibuka
 * (onMounted) dan setiap projectId berganti, sehingga hasil eksekusi terbaru
 * langsung terlihat setelah user kembali dari halaman Test Run.
 */
export function useProjectDashboardData(projectId: Ref<string>) {
  const data = ref<ProjectDashboardResponse | null>(null);
  const isLoading = ref(false);
  const errorMessage = ref('');

  // Penanda permintaan terbaru: respons dari project/permintaan lama yang
  // datang terlambat TIDAK boleh menimpa data yang sedang tampil.
  let latestRequestId = 0;

  async function load() {
    const requestId = ++latestRequestId;
    isLoading.value = true;
    errorMessage.value = '';
    try {
      const response = await projectDashboardService.getProjectDashboard(projectId.value);
      if (requestId !== latestRequestId) return;
      data.value = response;
    } catch (err) {
      if (requestId !== latestRequestId) return;
      errorMessage.value = axios.isAxiosError(err)
        ? ((err.response?.data as { message?: string } | undefined)?.message ??
          'Gagal memuat data dashboard, coba lagi.')
        : 'Tidak dapat terhubung ke server.';
    } finally {
      if (requestId === latestRequestId) isLoading.value = false;
    }
  }

  onMounted(load);

  // Ganti project: kosongkan dulu (jangan tampilkan angka project lain), lalu muat.
  watch(projectId, () => {
    data.value = null;
    load();
  });

  return { data, isLoading, errorMessage, reload: load };
}
