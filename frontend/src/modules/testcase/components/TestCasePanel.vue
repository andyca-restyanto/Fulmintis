<!-- frontend/src/modules/testcase/components/TestCasePanel.vue -->
<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue';
import { testCaseService } from '../services/testCase.service';
import { priorityLabel, priorityBadgeClass, typeLabel, scenarioLabel, scenarioBadgeClass } from '../utils/testCaseDisplay';
import type { TestCaseResponse, TestCaseScenarioType } from '../types/testCase.types';
import type { TestCaseImportResult } from '../types/testCaseImport.types';
import type { CommitTestCasesResult, TestCaseAiUsage } from '../types/testCaseAi.types';
import { importSuccessMessage } from '../utils/testCaseImportUi';
import { aiBlockReason, savedMessage } from '../utils/testCaseAiGate';
import { testCaseAiService } from '../services/testCaseAi.service';
import { useToast } from '@/shared/composables/useToast';
import { useProjectDetail } from '@/modules/project/composables/useProjectDetail';
import CreateTestCaseModal from './CreateTestCaseModal.vue';
import EditTestCaseModal from './EditTestCaseModal.vue';
import DeleteTestCaseModal from './DeleteTestCaseModal.vue';
import ArchivedTestCasesModal from './ArchivedTestCasesModal.vue';
import ImportTestCasesModal from './ImportTestCasesModal.vue';
import AiGenerateTestCasesModal from './AiGenerateTestCasesModal.vue';
import SearchIcon from '@/shared/components/icons/SearchIcon.vue';
import SparklesIcon from '@/shared/components/icons/SparklesIcon.vue';
import PlusIcon from '@/shared/components/icons/PlusIcon.vue';
import ChevronDownIcon from '@/shared/components/icons/ChevronDownIcon.vue';
import CheckIcon from '@/shared/components/icons/CheckIcon.vue';
import FolderIcon from '@/shared/components/icons/FolderIcon.vue';
import PencilIcon from '@/shared/components/icons/PencilIcon.vue';
import TrashIcon from '@/shared/components/icons/TrashIcon.vue';
import EyeIcon from '@/shared/components/icons/EyeIcon.vue';
import UploadIcon from '@/shared/components/icons/UploadIcon.vue';

const props = defineProps<{
  projectId: string;
  folderId: string;
  folderName: string;
}>();

const emit = defineEmits<{
  // Dilempar ke parent (TestRepositoryView) supaya badge jumlah test case
  // di sidebar folder ikut update tanpa perlu refetch semua folder.
  (e: 'test-case-created', testCase: TestCaseResponse): void;
  // Requirement tambahan #2: test case yang sudah diarsipkan hilang dari
  // listing ACTIVE -- badge jumlah di sidebar harus ikut berkurang.
  (e: 'test-case-archived', testCaseId: string): void;
  // Import dari Excel selesai: parent memuat ulang jumlah test case per folder
  // di sidebar (jumlah yang masuk banyak, bukan satu per satu seperti create).
  (e: 'test-cases-imported', importedCount: number): void;
}>();

// Detail project (termasuk myProjectTeam) sudah di-fetch ProjectLayoutView &
// di-share lewat provide/inject -- dipakai di sini utk tahu apakah user ini
// OWNER: requirement #3 (delete cuma boleh owner) & requirement #4 (test
// case archived cuma boleh dilihat owner).
const project = useProjectDetail();
const isOwner = computed(() => project.value?.myProjectTeam === 'OWNER');

const testCases = ref<TestCaseResponse[]>([]);
const isLoading = ref(true);
const loadError = ref('');
const isCreateModalOpen = ref(false);
const isArchivedModalOpen = ref(false);
const isImportModalOpen = ref(false);
const { pushToast } = useToast();

// ---- Generate test case dgn AI. Status fitur & jatah harian dibaca dari backend (usage): tombol nonaktif dgn alasan yang jelas
// kalau fitur belum diaktifkan atau jatah harian habis. Usage yang gagal dimuat juga memblokir (lebih baik nonaktif + alasan
// daripada membuka modal yang pasti gagal). ----
const isAiModalOpen = ref(false);
const aiUsage = ref<TestCaseAiUsage | null>(null);
const aiUsageFailed = ref(false);
const aiBlock = computed(() => aiBlockReason(aiUsage.value, aiUsageFailed.value));
// Teks alasan di bawah toolbar (tooltip saja tidak terlihat di layar sentuh). "Memuat..." tidak perlu ditampilkan.
const aiBlockVisible = computed(() => aiBlock.value !== null && (aiUsage.value !== null || aiUsageFailed.value));

async function loadAiUsage() {
  try {
    aiUsage.value = await testCaseAiService.getUsage(props.projectId);
    aiUsageFailed.value = false;
  } catch {
    aiUsageFailed.value = true;
  }
}

function closeAiModal() {
  isAiModalOpen.value = false;
  void loadAiUsage();
}

// Hasil review yang disimpan masuk ke folder tujuan; sama seperti import: induk memuat ulang jumlah per folder, daftar dimuat ulang.
function handleAiSaved(result: CommitTestCasesResult) {
  isAiModalOpen.value = false;
  emit('test-cases-imported', result.savedCount);
  loadTestCases();
  pushToast({ type: 'success', message: savedMessage(result) });
  void loadAiUsage();
}

const editingTestCase = ref<TestCaseResponse | null>(null);
const isEditModalOpen = ref(false);

const deletingTestCase = ref<TestCaseResponse | null>(null);
const isDeleteModalOpen = ref(false);

const keyword = ref('');
let debounceTimer: ReturnType<typeof setTimeout> | undefined;

const scenarioFilter = ref<TestCaseScenarioType | null>(null);
const isScenarioDropdownOpen = ref(false);
const scenarioFilterLabel = computed(() => {
  if (scenarioFilter.value === 'POSITIVE') return '✅ Positive';
  if (scenarioFilter.value === 'NEGATIVE') return '❌ Negative';
  return 'All Scenarios';
});

async function loadTestCases() {
  isLoading.value = true;
  loadError.value = '';
  try {
    // Endpoint search ini SELALU status=ACTIVE dari sisi backend (requirement
    // tambahan #4) -- tidak perlu filter tambahan apa pun di FE.
    testCases.value = await testCaseService.searchTestCases(props.projectId, {
      folderId: props.folderId,
      keyword: keyword.value.trim() || undefined,
      scenarioType: scenarioFilter.value ?? undefined,
    });
  } catch {
    loadError.value = 'Gagal memuat daftar test case.';
  } finally {
    isLoading.value = false;
  }
}

// Ganti folder -> reset filter & langsung fetch punya folder yang baru.
watch(
  () => props.folderId,
  () => {
    keyword.value = '';
    scenarioFilter.value = null;
    loadTestCases();
  }
);

// Keyword search di-debounce 350ms supaya tidak nembak API tiap ketikan.
watch(keyword, () => {
  if (debounceTimer) clearTimeout(debounceTimer);
  debounceTimer = setTimeout(loadTestCases, 350);
});

// Filter scenario langsung fetch ulang (bukan free-text, jadi tidak perlu debounce).
watch(scenarioFilter, loadTestCases);

onMounted(() => {
  loadTestCases();
  void loadAiUsage();
});
watch(() => props.projectId, () => void loadAiUsage());
onBeforeUnmount(() => {
  if (debounceTimer) clearTimeout(debounceTimer);
});

function selectScenarioFilter(value: TestCaseScenarioType | null) {
  scenarioFilter.value = value;
  isScenarioDropdownOpen.value = false;
}

function handleTestCaseCreated(created: TestCaseResponse) {
  isCreateModalOpen.value = false;
  emit('test-case-created', created);
  // Refetch supaya hasil create langsung kelihatan sesuai filter aktif saat ini.
  loadTestCases();
}

// ---- Import dari Excel -- boleh OWNER maupun COLLABORATOR (sama seperti
// membuat test case; backend tetap penjaga akhir). Semua baris masuk ke folder
// yang sedang dibuka. ----
function handleImported(result: TestCaseImportResult) {
  isImportModalOpen.value = false;
  emit('test-cases-imported', result.importedCount);
  // Muat ulang agar test case hasil import langsung tampil sesuai filter aktif.
  loadTestCases();
  // Jumlah di notifikasi = importedCount dari backend (yang BENAR-BENAR tersimpan).
  pushToast({ type: 'success', message: importSuccessMessage(result) });
}

// ---- Edit (requirement tambahan #1) -- boleh OWNER maupun COLLABORATOR,
// tombolnya SELALU ditampilkan di tiap baris (beda dgn Delete). ----
function openEditModal(testCase: TestCaseResponse) {
  editingTestCase.value = testCase;
  isEditModalOpen.value = true;
}

function closeEditModal() {
  isEditModalOpen.value = false;
  editingTestCase.value = null;
}

function handleTestCaseUpdated(updated: TestCaseResponse) {
  const index = testCases.value.findIndex((tc) => tc.id === updated.id);
  if (index !== -1) {
    testCases.value.splice(index, 1, updated);
  }
  closeEditModal();
}

// ---- Delete / archive (requirement tambahan #1, #2, #3) -- tombolnya
// HANYA dirender kalau isOwner true (lihat <template>), backend tetap jadi
// penjaga akhir (403 kalau bukan owner). ----
function openDeleteModal(testCase: TestCaseResponse) {
  deletingTestCase.value = testCase;
  isDeleteModalOpen.value = true;
}

function closeDeleteModal() {
  isDeleteModalOpen.value = false;
  deletingTestCase.value = null;
}

function handleTestCaseDeleted(testCaseId: string) {
  // Optimistic: langsung buang dari list ACTIVE yang sedang tampil --
  // konsisten dgn requirement #2 (test case archived tidak lagi muncul di
  // listing biasa).
  testCases.value = testCases.value.filter((tc) => tc.id !== testCaseId);
  emit('test-case-archived', testCaseId);
  closeDeleteModal();
}

// Nomor tampilan "TC-001" dst -- ID display FE-only (bukan dari backend,
// backend cuma kasih UUID), diurut sesuai urutan tampil di tabel ini.
function displayId(index: number): string {
  return `TC-${String(index + 1).padStart(3, '0')}`;
}
</script>

<template>
  <div class="flex h-full flex-col">
    <!-- Header + toolbar -->
    <div class="flex flex-wrap items-center justify-between gap-3 border-b border-gray-100 px-6 py-4">
      <div>
        <h2 class="text-lg font-bold text-gray-900">{{ folderName }}</h2>
        <p class="text-sm text-gray-400">{{ testCases.length }} test case{{ testCases.length === 1 ? '' : 's' }}</p>
      </div>

      <div class="flex flex-wrap items-center gap-2">
        <div class="relative">
          <SearchIcon :size="16" class="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
          <input
            v-model="keyword"
            type="text"
            placeholder="Search cases..."
            class="w-48 rounded-xl border border-gray-200 py-2 pl-9 pr-3 text-sm placeholder-gray-400 focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-100"
          />
        </div>

        <div class="relative">
          <button
            type="button"
            class="flex items-center gap-2 rounded-xl border border-gray-200 px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
            @click="isScenarioDropdownOpen = !isScenarioDropdownOpen"
          >
            {{ scenarioFilterLabel }}
            <ChevronDownIcon :size="14" class="text-gray-400" />
          </button>
          <div
            v-if="isScenarioDropdownOpen"
            class="absolute right-0 z-10 mt-2 w-44 rounded-xl border border-gray-100 bg-white py-1.5 shadow-lg"
          >
            <button
              type="button"
              class="flex w-full items-center justify-between gap-2 px-4 py-2 text-left text-sm text-gray-700 hover:bg-gray-50"
              @click="selectScenarioFilter(null)"
            >
              All Scenarios
              <CheckIcon v-if="scenarioFilter === null" :size="14" />
            </button>
            <button
              type="button"
              class="flex w-full items-center justify-between gap-2 px-4 py-2 text-left text-sm text-gray-700 hover:bg-gray-50"
              @click="selectScenarioFilter('POSITIVE')"
            >
              ✅ Positive
              <CheckIcon v-if="scenarioFilter === 'POSITIVE'" :size="14" />
            </button>
            <button
              type="button"
              class="flex w-full items-center justify-between gap-2 px-4 py-2 text-left text-sm text-gray-700 hover:bg-gray-50"
              @click="selectScenarioFilter('NEGATIVE')"
            >
              ❌ Negative
              <CheckIcon v-if="scenarioFilter === 'NEGATIVE'" :size="14" />
            </button>
          </div>
        </div>

        <!-- Requirement tambahan #4: cuma OWNER yang boleh lihat test case
             archived -- tombol ini disembunyikan TOTAL dari COLLABORATOR,
             bukan cuma di-disable, supaya COLLABORATOR bahkan tidak tahu
             fitur ini ada di folder ini. -->
        <button
          v-if="isOwner"
          type="button"
          title="Lihat test case yang sudah diarsipkan"
          class="flex items-center gap-2 rounded-xl border border-gray-200 px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
          @click="isArchivedModalOpen = true"
        >
          <EyeIcon :size="16" />
          Archived
        </button>

        <!-- Generate test case dgn AI: hasilnya DRAFT yang direview dulu sebelum disimpan. Nonaktif (dgn alasan) kalau fitur belum
             diaktifkan atau jatah harian habis. -->
        <button
          type="button"
          :disabled="aiBlock !== null"
          :title="aiBlock ?? 'Generate test case dengan AI'"
          class="flex items-center gap-2 rounded-xl border px-3 py-2 text-sm font-medium"
          :class="
            aiBlock === null
              ? 'border-gray-200 text-gray-700 hover:bg-gray-50'
              : 'cursor-not-allowed border-gray-200 text-gray-400'
          "
          @click="isAiModalOpen = true"
        >
          <SparklesIcon :size="16" />
          Generate with AI
        </button>

        <!-- Import test case dari template Excel. Panel ini hanya dirender kalau
             sebuah folder sudah dipilih (lihat TestRepositoryView), jadi tombol
             ini otomatis tidak ada selama belum ada folder terpilih. -->
        <button
          type="button"
          title="Import test case dari file Excel"
          class="flex items-center gap-2 rounded-xl border border-gray-200 px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
          @click="isImportModalOpen = true"
        >
          <UploadIcon :size="16" />
          Import
        </button>

        <button
          type="button"
          class="flex items-center gap-2 rounded-xl bg-gray-900 px-4 py-2 text-sm font-semibold text-white hover:bg-gray-800"
          @click="isCreateModalOpen = true"
        >
          <PlusIcon :size="16" />
          New Test Case
        </button>
      </div>

      <!-- Alasan tombol "Generate with AI" nonaktif (tooltip saja tidak terlihat di layar sentuh). -->
      <p v-if="aiBlockVisible" class="basis-full text-right text-xs text-amber-700" role="status">
        Generate with AI: {{ aiBlock }}
      </p>
    </div>

    <!-- Table -->
    <div class="flex-1 overflow-auto">
      <p v-if="isLoading" class="px-6 py-6 text-sm text-gray-400">Memuat test case...</p>
      <p v-else-if="loadError" class="px-6 py-6 text-sm text-red-500">{{ loadError }}</p>

      <div v-else-if="testCases.length === 0" class="flex flex-col items-center justify-center py-20 text-center">
        <FolderIcon :size="40" class="mb-3 text-gray-200" />
        <p class="text-gray-400">
          {{ keyword || scenarioFilter ? 'Tidak ada test case yang cocok' : 'No test cases yet' }}
        </p>
      </div>

      <table v-else class="w-full text-left text-sm">
        <thead>
          <tr class="border-b border-gray-100 text-xs font-medium uppercase tracking-wide text-gray-400">
            <th class="px-6 py-3 font-medium">ID</th>
            <th class="px-6 py-3 font-medium">Title</th>
            <th class="px-6 py-3 font-medium">Priority</th>
            <th class="px-6 py-3 font-medium">Type</th>
            <th class="px-6 py-3 font-medium">Scenario</th>
            <th class="px-6 py-3 text-right font-medium">Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="(testCase, index) in testCases"
            :key="testCase.id"
            class="border-b border-gray-50 hover:bg-gray-50"
          >
            <td class="px-6 py-4 text-gray-400">{{ displayId(index) }}</td>
            <td class="px-6 py-4 font-medium text-gray-900">{{ testCase.title }}</td>
            <td class="px-6 py-4">
              <span
                class="rounded-full px-2.5 py-1 text-xs font-medium"
                :class="priorityBadgeClass(testCase.priority)"
              >
                {{ priorityLabel(testCase.priority) }}
              </span>
            </td>
            <td class="px-6 py-4">
              <span class="rounded-full border border-gray-200 px-2.5 py-1 text-xs font-medium text-gray-600">
                {{ typeLabel(testCase.type) }}
              </span>
            </td>
            <td class="px-6 py-4">
              <span
                class="rounded-full px-2.5 py-1 text-xs font-medium"
                :class="scenarioBadgeClass(testCase.scenarioType)"
              >
                {{ scenarioLabel(testCase.scenarioType) }}
              </span>
            </td>
            <td class="px-6 py-4">
              <div class="flex items-center justify-end gap-1">
                <!-- Requirement tambahan #1: edit boleh OWNER maupun
                     COLLABORATOR -- sama seperti aturan create. -->
                <button
                  type="button"
                  title="Edit test case"
                  class="rounded-lg p-1.5 text-gray-400 hover:bg-gray-100 hover:text-gray-700"
                  @click="openEditModal(testCase)"
                >
                  <PencilIcon :size="16" />
                </button>
                <!-- Requirement #3: delete cuma boleh OWNER -- tombolnya
                     disembunyikan total dari COLLABORATOR. -->
                <button
                  v-if="isOwner"
                  type="button"
                  title="Delete test case"
                  class="rounded-lg p-1.5 text-gray-400 hover:bg-red-50 hover:text-red-600"
                  @click="openDeleteModal(testCase)"
                >
                  <TrashIcon :size="16" />
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>

  <CreateTestCaseModal
    :open="isCreateModalOpen"
    :project-id="projectId"
    :folder-id="folderId"
    @close="isCreateModalOpen = false"
    @created="handleTestCaseCreated"
  />

  <AiGenerateTestCasesModal
    :open="isAiModalOpen"
    :project-id="projectId"
    :folder-id="folderId"
    :folder-name="folderName"
    :usage="aiUsage"
    @close="closeAiModal"
    @saved="handleAiSaved"
    @usage-changed="loadAiUsage"
  />

  <ImportTestCasesModal
    :open="isImportModalOpen"
    :project-id="projectId"
    :folder-id="folderId"
    :folder-name="folderName"
    @close="isImportModalOpen = false"
    @imported="handleImported"
  />

  <EditTestCaseModal
    :open="isEditModalOpen"
    :project-id="projectId"
    :test-case="editingTestCase"
    @close="closeEditModal"
    @updated="handleTestCaseUpdated"
  />

  <DeleteTestCaseModal
    :open="isDeleteModalOpen"
    :project-id="projectId"
    :test-case="deletingTestCase"
    @close="closeDeleteModal"
    @deleted="handleTestCaseDeleted"
  />

  <ArchivedTestCasesModal
    v-if="isOwner"
    :open="isArchivedModalOpen"
    :project-id="projectId"
    :folder-id="folderId"
    :folder-name="folderName"
    @close="isArchivedModalOpen = false"
  />
</template>
