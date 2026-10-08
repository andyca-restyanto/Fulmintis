<!-- frontend/src/modules/testcase/components/ArchivedTestCasesModal.vue -->
<script setup lang="ts">
import { ref, watch } from 'vue';
import axios from 'axios';
import { testCaseService } from '../services/testCase.service';
import { priorityLabel, priorityBadgeClass, typeLabel, scenarioLabel, scenarioBadgeClass, formatDateTime } from '../utils/testCaseDisplay';
import type { TestCaseResponse } from '../types/testCase.types';
import XIcon from '@/shared/components/icons/XIcon.vue';
import EyeIcon from '@/shared/components/icons/EyeIcon.vue';
import FolderIcon from '@/shared/components/icons/FolderIcon.vue';

// Requirement tambahan #4: test case yang sudah di-archive HANYA boleh
// dilihat Project Owner -- komponen ini SENGAJA cuma pernah dirender kalau
// isOwner true di TestCasePanel.vue (tombol pembuka disembunyikan total dari
// COLLABORATOR). Backend (GET /test-cases/archived) tetap jadi penjaga
// akhir (403 kalau somehow yang request bukan OWNER).
const props = defineProps<{
  open: boolean;
  projectId: string;
  folderId: string;
  folderName: string;
}>();

const emit = defineEmits<{
  (e: 'close'): void;
}>();

const archivedTestCases = ref<TestCaseResponse[]>([]);
const isLoading = ref(true);
const loadError = ref('');

async function loadArchivedTestCases() {
  isLoading.value = true;
  loadError.value = '';
  try {
    archivedTestCases.value = await testCaseService.listArchivedTestCases(props.projectId, {
      folderId: props.folderId,
    });
  } catch (err) {
    if (axios.isAxiosError(err) && err.response?.status === 403) {
      // Harusnya jarang kejadian -- tombol pembuka modal ini sudah
      // disembunyikan dari COLLABORATOR, ini cuma penjaga terakhir.
      loadError.value = 'Hanya Project Owner yang bisa melihat test case yang sudah diarsipkan.';
    } else {
      loadError.value = 'Gagal memuat daftar test case yang diarsipkan.';
    }
  } finally {
    isLoading.value = false;
  }
}

// Fetch ulang tiap kali modal dibuka (folder aktif bisa saja beda dari
// terakhir kali modal ini dibuka).
watch(
  () => [props.open, props.folderId] as const,
  ([isOpen]) => {
    if (isOpen) {
      loadArchivedTestCases();
    }
  },
  { immediate: true }
);

function handleClose() {
  emit('close');
}

function displayId(index: number): string {
  return `TC-${String(index + 1).padStart(3, '0')}`;
}
</script>

<template>
  <div
    v-if="open"
    class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4 py-8"
    @click.self="handleClose"
  >
    <div class="flex max-h-[85vh] w-full max-w-4xl flex-col rounded-2xl bg-white shadow-xl">
      <div class="flex items-start justify-between border-b border-gray-100 px-8 py-6">
        <div>
          <div class="flex items-center gap-2">
            <EyeIcon :size="18" class="text-gray-500" />
            <h2 class="text-xl font-bold text-gray-900">Archived Test Cases</h2>
          </div>
          <p class="mt-1 text-sm text-gray-400">Folder: {{ folderName }} -- hanya terlihat oleh Project Owner</p>
        </div>
        <button type="button" class="text-gray-400 hover:text-gray-600" @click="handleClose">
          <XIcon :size="20" />
        </button>
      </div>

      <div class="flex-1 overflow-auto px-2 py-2">
        <p v-if="isLoading" class="px-6 py-6 text-sm text-gray-400">Memuat test case yang diarsipkan...</p>
        <p v-else-if="loadError" class="px-6 py-6 text-sm text-red-500">{{ loadError }}</p>

        <div v-else-if="archivedTestCases.length === 0" class="flex flex-col items-center justify-center py-16 text-center">
          <FolderIcon :size="36" class="mb-3 text-gray-200" />
          <p class="text-gray-400">Belum ada test case yang diarsipkan di folder ini.</p>
        </div>

        <table v-else class="w-full text-left text-sm">
          <thead>
            <tr class="border-b border-gray-100 text-xs font-medium uppercase tracking-wide text-gray-400">
              <th class="px-6 py-3 font-medium">ID</th>
              <th class="px-6 py-3 font-medium">Title</th>
              <th class="px-6 py-3 font-medium">Priority</th>
              <th class="px-6 py-3 font-medium">Type</th>
              <th class="px-6 py-3 font-medium">Scenario</th>
              <th class="px-6 py-3 font-medium">Archived At</th>
              <th class="px-6 py-3 font-medium">Archived By</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="(testCase, index) in archivedTestCases"
              :key="testCase.id"
              class="border-b border-gray-50 hover:bg-gray-50"
            >
              <td class="px-6 py-4 text-gray-400">{{ displayId(index) }}</td>
              <td class="px-6 py-4 font-medium text-gray-900">{{ testCase.title }}</td>
              <td class="px-6 py-4">
                <span class="rounded-full px-2.5 py-1 text-xs font-medium" :class="priorityBadgeClass(testCase.priority)">
                  {{ priorityLabel(testCase.priority) }}
                </span>
              </td>
              <td class="px-6 py-4">
                <span class="rounded-full border border-gray-200 px-2.5 py-1 text-xs font-medium text-gray-600">
                  {{ typeLabel(testCase.type) }}
                </span>
              </td>
              <td class="px-6 py-4">
                <span class="rounded-full px-2.5 py-1 text-xs font-medium" :class="scenarioBadgeClass(testCase.scenarioType)">
                  {{ scenarioLabel(testCase.scenarioType) }}
                </span>
              </td>
              <td class="px-6 py-4 text-gray-500">{{ formatDateTime(testCase.archivedAt) }}</td>
              <td class="px-6 py-4 text-gray-500">{{ testCase.archivedBy ?? '-' }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="flex justify-end border-t border-gray-100 px-8 py-4">
        <button
          type="button"
          class="rounded-xl border border-gray-200 px-5 py-2.5 text-sm font-semibold text-gray-700 hover:bg-gray-50"
          @click="handleClose"
        >
          Close
        </button>
      </div>
    </div>
  </div>
</template>
