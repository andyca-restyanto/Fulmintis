<!-- frontend/src/modules/testrun/components/TestCaseSelector.vue -->
<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue';
import { testCaseService } from '@/modules/testcase';
import { testFolderService } from '@/modules/testrepository';
import type { TestCaseResponse } from '@/modules/testcase';
import type { TestFolderResponse } from '@/modules/testrepository';
import SearchIcon from '@/shared/components/icons/SearchIcon.vue';
import ChevronDownIcon from '@/shared/components/icons/ChevronDownIcon.vue';
import CheckIcon from '@/shared/components/icons/CheckIcon.vue';

// Requirement tambahan Test Run #3: "search test case with name / id saat
// akan mapping test case" -- keyword di bawah dikirim apa adanya ke
// testCaseService.searchTestCases(), yang backend-nya (Luhut) sudah
// mendukung cari by title ATAU by UUID id sekaligus (lihat
// TestCaseSpecifications.titleOrIdContains). Dipakai bergantian oleh
// CreateTestRunModal (step "Select Cases") & ManageTestRunCasesModal
// (nambah test case ke run yang sudah ada, requirement #7).
const props = defineProps<{
  projectId: string;
  // Daftar LENGKAP id yang tercentang (bukan cuma yang sedang tampil di
  // hasil search/filter). Di ManageTestRunCasesModal nilai awalnya = test
  // case yang SUDAH ada di run, jadi mereka tampil tercentang: uncheck =
  // lepas dari run, check test case lain = tambah ke run.
  modelValue: string[];
  // Kelas tinggi area daftar (default h-60). ManageTestRunCasesModal memakai
  // area yang lebih tinggi sesuai mockup.
  listHeightClass?: string;
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: string[]): void;
}>();

const testCases = ref<TestCaseResponse[]>([]);
const folders = ref<TestFolderResponse[]>([]);
const isLoading = ref(true);
const loadError = ref('');

const keyword = ref('');
let debounceTimer: ReturnType<typeof setTimeout> | undefined;

const selectedFolderId = ref<string | null>(null); // null = "All folders"
const isFolderDropdownOpen = ref(false);
const selectedFolderLabel = computed(() => {
  if (!selectedFolderId.value) return 'All folders';
  return folders.value.find((f) => f.id === selectedFolderId.value)?.folderName ?? 'All folders';
});

// Test case cuma punya folderId (bukan nama foldernya langsung) -- dipetakan
// balik ke nama lewat daftar folder yang di-fetch terpisah, dipakai render
// subtitle di tiap baris (lihat mockup: judul test case + nama folder di
// bawahnya).
const folderNameById = computed(() => {
  const map = new Map<string, string>();
  folders.value.forEach((f) => map.set(f.id, f.folderName));
  return map;
});

// Semua hasil search ditampilkan apa adanya, TERMASUK yang sudah ada di run.
const visibleTestCases = computed(() => testCases.value);

async function loadFolders() {
  try {
    folders.value = await testFolderService.listFolders(props.projectId);
  } catch {
    folders.value = [];
  }
}

async function loadTestCases() {
  isLoading.value = true;
  loadError.value = '';
  try {
    testCases.value = await testCaseService.searchTestCases(props.projectId, {
      folderId: selectedFolderId.value ?? undefined,
      keyword: keyword.value.trim() || undefined,
    });
  } catch {
    loadError.value = 'Gagal memuat daftar test case.';
  } finally {
    isLoading.value = false;
  }
}

onMounted(async () => {
  await loadFolders();
  await loadTestCases();
});

onBeforeUnmount(() => {
  if (debounceTimer) clearTimeout(debounceTimer);
});

watch(keyword, () => {
  if (debounceTimer) clearTimeout(debounceTimer);
  debounceTimer = setTimeout(loadTestCases, 350);
});

watch(selectedFolderId, loadTestCases);

function selectFolder(id: string | null) {
  selectedFolderId.value = id;
  isFolderDropdownOpen.value = false;
}

const selectedSet = computed(() => new Set(props.modelValue));
const allVisibleSelected = computed(
  () => visibleTestCases.value.length > 0 && visibleTestCases.value.every((tc) => selectedSet.value.has(tc.id))
);

function toggleTestCase(id: string) {
  const next = new Set(props.modelValue);
  if (next.has(id)) {
    next.delete(id);
  } else {
    next.add(id);
  }
  emit('update:modelValue', Array.from(next));
}

function toggleSelectAll() {
  const next = new Set(props.modelValue);
  if (allVisibleSelected.value) {
    visibleTestCases.value.forEach((tc) => next.delete(tc.id));
  } else {
    visibleTestCases.value.forEach((tc) => next.add(tc.id));
  }
  emit('update:modelValue', Array.from(next));
}

// Nomor tampilan "TC-001" dst -- ID display FE-only (backend cuma kasih
// UUID), sama seperti konvensi di TestCasePanel.vue.
function displayId(index: number): string {
  return `TC-${String(index + 1).padStart(3, '0')}`;
}
</script>

<template>
  <div>
    <div class="flex items-center gap-3">
      <div class="relative flex-1">
        <SearchIcon :size="16" class="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
        <input
          v-model="keyword"
          type="text"
          placeholder="Search test cases..."
          class="w-full rounded-xl border border-gray-200 py-2.5 pl-9 pr-3 text-sm placeholder-gray-400 focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-100"
        />
      </div>

      <div class="relative shrink-0">
        <button
          type="button"
          class="flex items-center gap-2 rounded-xl border border-gray-200 px-3.5 py-2.5 text-sm font-medium text-gray-700 hover:bg-gray-50"
          @click="isFolderDropdownOpen = !isFolderDropdownOpen"
        >
          {{ selectedFolderLabel }}
          <ChevronDownIcon :size="14" class="text-gray-400" />
        </button>
        <div
          v-if="isFolderDropdownOpen"
          class="absolute right-0 z-10 mt-2 w-48 rounded-xl border border-gray-100 bg-white py-1.5 shadow-lg"
        >
          <button
            type="button"
            class="flex w-full items-center justify-between gap-2 px-4 py-2 text-left text-sm text-gray-700 hover:bg-gray-50"
            @click="selectFolder(null)"
          >
            All folders
            <CheckIcon v-if="!selectedFolderId" :size="14" />
          </button>
          <button
            v-for="folder in folders"
            :key="folder.id"
            type="button"
            class="flex w-full items-center justify-between gap-2 px-4 py-2 text-left text-sm text-gray-700 hover:bg-gray-50"
            @click="selectFolder(folder.id)"
          >
            {{ folder.folderName }}
            <CheckIcon v-if="selectedFolderId === folder.id" :size="14" />
          </button>
        </div>
      </div>
    </div>

    <div class="mt-4 flex items-center justify-between">
      <button
        type="button"
        class="flex items-center gap-2 px-1 text-sm font-medium text-gray-700 disabled:cursor-not-allowed disabled:text-gray-300"
        :disabled="visibleTestCases.length === 0"
        @click="toggleSelectAll"
      >
        <span class="flex h-[18px] w-[18px] items-center justify-center rounded border border-gray-700 text-gray-700">
          <CheckIcon v-if="allVisibleSelected" :size="12" />
        </span>
        {{ allVisibleSelected ? 'Deselect All' : 'Select All' }}
      </button>
      <span class="rounded-full bg-gray-100 px-3 py-1 text-xs font-semibold text-gray-700">
        {{ modelValue.length }} selected
      </span>
    </div>

    <div class="mt-3 overflow-y-auto rounded-xl border border-gray-100" :class="listHeightClass ?? 'h-60'">
      <p v-if="isLoading" class="px-4 py-6 text-sm text-gray-400">Memuat test case...</p>
      <p v-else-if="loadError" class="px-4 py-6 text-sm text-red-500">{{ loadError }}</p>
      <p v-else-if="visibleTestCases.length === 0" class="px-4 py-6 text-sm text-gray-400">
        {{ keyword ? 'Tidak ada test case yang cocok.' : 'Belum ada test case di folder ini.' }}
      </p>
      <label
        v-for="(testCase, index) in visibleTestCases"
        :key="testCase.id"
        class="flex cursor-pointer items-center gap-3 border-b border-gray-50 px-4 py-3.5 last:border-0 hover:bg-gray-50"
        :class="selectedSet.has(testCase.id) ? 'bg-gray-50' : ''"
      >
        <!-- input native disembunyikan (tetap bisa diakses keyboard/screen reader), tampilan pakai span di bawahnya -->
        <input
          type="checkbox"
          class="peer sr-only"
          :checked="selectedSet.has(testCase.id)"
          @change="toggleTestCase(testCase.id)"
        />
        <span
          class="flex h-[18px] w-[18px] shrink-0 items-center justify-center rounded-full border border-gray-300 bg-white text-white peer-checked:border-gray-900 peer-checked:bg-gray-900 peer-focus-visible:ring-2 peer-focus-visible:ring-gray-300"
        >
          <CheckIcon v-if="selectedSet.has(testCase.id)" :size="11" />
        </span>
        <span class="min-w-0">
          <span class="font-mono text-xs text-gray-400">{{ displayId(index) }}</span>
          <span class="ml-2 text-sm font-semibold text-gray-900">{{ testCase.title }}</span>
          <span class="mt-1 block font-mono text-xs text-gray-500">{{ folderNameById.get(testCase.folderId) ?? '-' }}</span>
        </span>
      </label>
    </div>
  </div>
</template>
