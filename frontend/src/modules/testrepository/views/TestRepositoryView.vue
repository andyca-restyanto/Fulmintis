<!-- frontend/src/modules/testrepository/views/TestRepositoryView.vue -->
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { testFolderService } from '../services/testFolder.service';
import { buildFolderTree } from '../utils/testFolderTree';
import type { TestFolderResponse } from '../types/testFolder.types';
import { useProjectDetail } from '@/modules/project/composables/useProjectDetail';
import NewFolderModal from '../components/NewFolderModal.vue';
import TestFolderTreeItem from '../components/TestFolderTreeItem.vue';
import { testCaseService } from '@/modules/testcase/services/testCase.service';
import type { TestCaseResponse } from '@/modules/testcase/types/testCase.types';
import TestCasePanel from '@/modules/testcase/components/TestCasePanel.vue';
import PlusIcon from '@/shared/components/icons/PlusIcon.vue';
import FolderIcon from '@/shared/components/icons/FolderIcon.vue';

const route = useRoute();

// Detail project (termasuk myProjectTeam) sudah di-fetch oleh
// ProjectLayoutView & di-share lewat provide/inject -- dipakai di sini utk
// tahu apakah user ini OWNER (requirement #3: tombol create cuma utk owner).
const project = useProjectDetail();

const projectId = computed(() => route.params.projectId as string);
const isOwner = computed(() => project.value?.myProjectTeam === 'OWNER');

const folders = ref<TestFolderResponse[]>([]);
const isLoading = ref(true);
const loadError = ref('');
const isModalOpen = ref(false);
const selectedFolderId = ref<string | null>(null);

const folderTree = computed(() => buildFolderTree(folders.value));
const hasFolders = computed(() => folders.value.length > 0);

const selectedFolderName = computed(
  () => folders.value.find((f) => f.id === selectedFolderId.value)?.folderName ?? null
);

// Dipakai CUMA utk badge jumlah test case per folder di sidebar (lihat
// testCaseCounts) -- daftar test case yang beneran ditampilkan di tabel
// kanan di-fetch terpisah oleh TestCasePanel sendiri (lewat search API
// Luhut, termasuk keyword/scenario filter).
const allTestCases = ref<TestCaseResponse[]>([]);
const testCaseCounts = computed(() => {
  const counts: Record<string, number> = {};
  for (const testCase of allTestCases.value) {
    counts[testCase.folderId] = (counts[testCase.folderId] ?? 0) + 1;
  }
  return counts;
});

async function loadFolders() {
  isLoading.value = true;
  loadError.value = '';
  try {
    folders.value = await testFolderService.listFolders(projectId.value);
  } catch {
    loadError.value = 'Gagal memuat daftar folder.';
  } finally {
    isLoading.value = false;
  }
}

async function loadAllTestCaseCounts() {
  try {
    allTestCases.value = await testCaseService.searchTestCases(projectId.value);
  } catch {
    // Non-fatal -- badge jumlah cuma tidak muncul, tabel utama (TestCasePanel)
    // tetap fetch sendiri dan tidak terganggu kalau ini gagal.
  }
}

onMounted(() => {
  loadFolders();
  loadAllTestCaseCounts();
});

function handleFolderCreated(created: TestFolderResponse) {
  folders.value.push(created);
  isModalOpen.value = false;
}

function handleSelectFolder(id: string) {
  selectedFolderId.value = id;
}

function handleTestCaseCreated(created: TestCaseResponse) {
  // Update badge count sidebar secara optimistic, tanpa refetch semua test case.
  allTestCases.value.push(created);
}

// Requirement tambahan #2: test case yang di-archive (delete) hilang dari
// listing ACTIVE -- badge jumlah di sidebar folder harus ikut berkurang,
// sama seperti handleTestCaseCreated tapi kebalikannya (optimistic, tanpa
// refetch semua test case).
function handleTestCaseArchived(testCaseId: string) {
  allTestCases.value = allTestCases.value.filter((testCase) => testCase.id !== testCaseId);
}
</script>

<template>
  <div class="flex h-full">
    <!-- Test Suites panel -->
    <div class="flex w-80 shrink-0 flex-col border-r border-gray-100">
      <div class="flex items-center justify-between border-b border-gray-100 px-5 py-4">
        <h2 class="text-base font-bold text-gray-900">Test Suites</h2>
        <button
          v-if="isOwner"
          type="button"
          title="Create test suite"
          class="rounded-lg p-1 text-gray-500 hover:bg-gray-50 hover:text-gray-900"
          @click="isModalOpen = true"
        >
          <PlusIcon :size="18" />
        </button>
      </div>

      <div class="flex-1 overflow-y-auto px-2 py-3">
        <p v-if="isLoading" class="px-3 py-2 text-sm text-gray-400">Memuat...</p>
        <p v-else-if="loadError" class="px-3 py-2 text-sm text-red-500">{{ loadError }}</p>
        <p v-else-if="!hasFolders" class="px-3 py-2 text-sm text-gray-400">
          No folders yet. Create one!
        </p>
        <div v-else class="space-y-0.5">
          <TestFolderTreeItem
            v-for="node in folderTree"
            :key="node.id"
            :node="node"
            :depth="0"
            :selected-id="selectedFolderId"
            :counts="testCaseCounts"
            @select="handleSelectFolder"
          />
        </div>
      </div>
    </div>

    <!-- Right panel -->
    <div class="min-w-0 flex-1">
      <TestCasePanel
        v-if="selectedFolderId && selectedFolderName"
        :key="selectedFolderId"
        :project-id="projectId"
        :folder-id="selectedFolderId"
        :folder-name="selectedFolderName"
        @test-case-created="handleTestCaseCreated"
        @test-case-archived="handleTestCaseArchived"
        @test-cases-imported="loadAllTestCaseCounts"
      />
      <div v-else class="flex h-full items-center justify-center">
        <div class="text-center">
          <FolderIcon :size="48" class="mx-auto mb-3 text-gray-200" />
          <p class="text-gray-400">Select a folder to view test cases</p>
        </div>
      </div>
    </div>
  </div>

  <NewFolderModal
    :open="isModalOpen"
    :project-id="projectId"
    :folders="folders"
    @close="isModalOpen = false"
    @created="handleFolderCreated"
  />
</template>
