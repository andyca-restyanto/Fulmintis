<!-- frontend/src/modules/automation/components/TestCasePicker.vue -->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { testCaseService, priorityBadgeClass, priorityLabel, typeLabel, type TestCaseResponse } from '@/modules/testcase';
import {
  testFolderService,
  buildFolderTree,
  flattenTreeForDropdown,
  type TestFolderResponse,
} from '@/modules/testrepository';
import {
  addMany,
  groupSelectionState,
  pruneSelection,
  removeMany,
  selectionCounter,
  toggleSelection,
} from '../utils/automationSelection';
import SearchIcon from '@/shared/components/icons/SearchIcon.vue';
import FolderIcon from '@/shared/components/icons/FolderIcon.vue';

// Memilih test case (lintas folder) utk digenerate. Hanya test case ACTIVE (backend tidak pernah mengirim yang
// diarsipkan). Jumlah dibatasi `max` (bergantung tier); urutan pilihan = urutan klik.
const props = defineProps<{
  projectId: string;
  /** Daftar id terpilih (v-model:selected). */
  selected: string[];
  max: number;
  disabled?: boolean;
}>();

const emit = defineEmits<{
  (e: 'update:selected', value: string[]): void;
}>();

const ALL = '__all__';

const folders = ref<TestFolderResponse[]>([]);
const testCases = ref<TestCaseResponse[]>([]);
const isLoading = ref(true);
const errorMessage = ref('');
const activeFolderId = ref<string>(ALL);
const search = ref('');
const limitNotice = ref('');

async function load() {
  isLoading.value = true;
  errorMessage.value = '';
  try {
    [folders.value, testCases.value] = await Promise.all([
      testFolderService.listFolders(props.projectId),
      testCaseService.searchTestCases(props.projectId),
    ]);
    // Test case yang sudah diarsipkan/dihapus sejak pilihan dibuat -> buang dari pilihan.
    const pruned = pruneSelection(props.selected, new Set(testCases.value.map((testCase) => testCase.id)));
    if (pruned.length !== props.selected.length) emit('update:selected', pruned);
  } catch {
    errorMessage.value = 'Gagal memuat test case. Coba muat ulang.';
  } finally {
    isLoading.value = false;
  }
}

onMounted(load);

const folderOptions = computed(() => flattenTreeForDropdown(buildFolderTree(folders.value)));
const countByFolder = computed(() => {
  const counts = new Map<string, number>();
  testCases.value.forEach((testCase) => counts.set(testCase.folderId, (counts.get(testCase.folderId) ?? 0) + 1));
  return counts;
});

const visible = computed(() => {
  const keyword = search.value.trim().toLowerCase();
  return testCases.value.filter(
    (testCase) =>
      (activeFolderId.value === ALL || testCase.folderId === activeFolderId.value) &&
      (keyword === '' || testCase.title.toLowerCase().includes(keyword))
  );
});
const visibleIds = computed(() => visible.value.map((testCase) => testCase.id));
const groupState = computed(() => groupSelectionState(visibleIds.value, props.selected));
const isFull = computed(() => props.selected.length >= props.max);

function showLimitNotice() {
  limitNotice.value = `Batas ${props.max} test case per generate tercapai.`;
}

function toggle(id: string) {
  if (props.disabled) return;
  const result = toggleSelection(props.selected, id, props.max);
  if (result.blocked) {
    showLimitNotice();
    return;
  }
  limitNotice.value = '';
  emit('update:selected', result.selected);
}

function toggleGroup() {
  if (props.disabled) return;
  if (groupState.value === 'all') {
    emit('update:selected', removeMany(props.selected, visibleIds.value));
    limitNotice.value = '';
    return;
  }
  const result = addMany(props.selected, visibleIds.value, props.max);
  emit('update:selected', result.selected);
  if (result.truncated) {
    limitNotice.value = `Hanya sebagian yang terpilih: batas ${props.max} test case per generate.`;
  } else {
    limitNotice.value = '';
  }
}

function clearAll() {
  if (props.disabled) return;
  limitNotice.value = '';
  emit('update:selected', []);
}

function folderNameOf(folderId: string): string {
  return folders.value.find((folder) => folder.id === folderId)?.folderName ?? '';
}
</script>

<template>
  <div class="rounded-2xl border border-gray-200">
    <div class="flex flex-wrap items-center justify-between gap-3 border-b border-gray-100 px-5 py-4">
      <div>
        <h3 class="text-base font-bold text-gray-900">Pilih test case</h3>
        <p class="mt-0.5 text-xs text-gray-500">Hanya test case aktif. Urutan hasil mengikuti urutan pilihan.</p>
      </div>
      <div class="flex items-center gap-3">
        <span
          class="rounded-full px-3 py-1 text-xs font-semibold tabular-nums"
          :class="isFull ? 'bg-amber-50 text-amber-700' : 'bg-gray-100 text-gray-700'"
          aria-live="polite"
        >
          {{ selectionCounter(selected, max) }} dipilih
        </span>
        <button
          v-if="selected.length > 0"
          type="button"
          class="text-xs font-medium text-gray-500 underline-offset-2 hover:text-gray-800 hover:underline disabled:opacity-50"
          :disabled="disabled"
          @click="clearAll"
        >
          Hapus pilihan
        </button>
      </div>
    </div>

    <div v-if="isLoading" class="px-5 py-10 text-center text-sm text-gray-400">Memuat test case...</div>

    <div v-else-if="errorMessage" class="flex flex-col items-center gap-3 px-5 py-10 text-center" role="alert">
      <p class="text-sm text-red-600">{{ errorMessage }}</p>
      <button type="button" class="rounded-xl bg-gray-900 px-4 py-2 text-sm font-semibold text-white hover:bg-gray-800" @click="load">
        Coba lagi
      </button>
    </div>

    <div v-else-if="testCases.length === 0" class="px-5 py-10 text-center text-sm text-gray-500">
      Belum ada test case di project ini.
      <RouterLink
        :to="{ name: 'project-test-repository', params: { projectId } }"
        class="font-semibold text-gray-900 underline underline-offset-2"
      >
        Buat di Test Repository
      </RouterLink>
    </div>

    <div v-else class="grid grid-cols-1 md:grid-cols-[220px_minmax(0,1fr)]">
      <!-- Folder -->
      <ul class="max-h-[380px] space-y-0.5 overflow-auto border-b border-gray-100 p-3 md:border-b-0 md:border-r" aria-label="Folder">
        <li>
          <button
            type="button"
            class="flex w-full items-center justify-between rounded-lg px-3 py-2 text-left text-sm"
            :class="activeFolderId === ALL ? 'bg-gray-100 font-semibold text-gray-900' : 'text-gray-600 hover:bg-gray-50'"
            @click="activeFolderId = ALL"
          >
            <span>Semua test case</span>
            <span class="text-xs text-gray-400">{{ testCases.length }}</span>
          </button>
        </li>
        <li v-for="folder in folderOptions" :key="folder.id">
          <button
            type="button"
            class="flex w-full items-center justify-between gap-2 rounded-lg px-3 py-2 text-left text-sm"
            :class="activeFolderId === folder.id ? 'bg-gray-100 font-semibold text-gray-900' : 'text-gray-600 hover:bg-gray-50'"
            @click="activeFolderId = folder.id"
          >
            <span class="flex min-w-0 items-center gap-2"><FolderIcon :size="14" class="shrink-0 text-gray-400" /><span class="truncate">{{ folder.label }}</span></span>
            <span class="text-xs text-gray-400">{{ countByFolder.get(folder.id) ?? 0 }}</span>
          </button>
        </li>
      </ul>

      <!-- Test case -->
      <div class="min-w-0">
        <div class="flex items-center gap-3 border-b border-gray-100 px-4 py-2.5">
          <input
            type="checkbox"
            class="h-4 w-4 rounded border-gray-300"
            :checked="groupState === 'all'"
            :indeterminate.prop="groupState === 'some'"
            :disabled="disabled || visible.length === 0"
            aria-label="Pilih semua yang tampil"
            @change="toggleGroup"
          />
          <div class="relative flex-1">
            <SearchIcon :size="14" class="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
            <input
              v-model="search"
              type="search"
              placeholder="Cari test case..."
              class="w-full rounded-lg border border-gray-200 py-1.5 pl-8 pr-3 text-sm placeholder-gray-400 focus:border-gray-400 focus:outline-none"
            />
          </div>
        </div>

        <p v-if="limitNotice" class="border-b border-amber-100 bg-amber-50/60 px-4 py-2 text-xs text-amber-700" role="status">
          {{ limitNotice }}
        </p>

        <ul class="max-h-[330px] overflow-auto">
          <li v-if="visible.length === 0" class="px-4 py-8 text-center text-sm text-gray-400">Tidak ada test case yang cocok.</li>
          <li v-for="testCase in visible" :key="testCase.id" class="border-b border-gray-50 last:border-0">
            <label
              class="flex cursor-pointer items-center gap-3 px-4 py-2.5 hover:bg-gray-50"
              :class="{ 'cursor-not-allowed opacity-60': disabled || (isFull && !selected.includes(testCase.id)) }"
              :title="isFull && !selected.includes(testCase.id) ? `Batas ${max} test case per generate tercapai` : undefined"
            >
              <input
                type="checkbox"
                class="h-4 w-4 shrink-0 rounded border-gray-300"
                :checked="selected.includes(testCase.id)"
                :disabled="disabled || (isFull && !selected.includes(testCase.id))"
                @change="toggle(testCase.id)"
              />
              <span class="min-w-0 flex-1">
                <span class="block truncate text-sm font-medium text-gray-900">{{ testCase.title }}</span>
                <span v-if="activeFolderId === ALL" class="block truncate text-xs text-gray-400">{{ folderNameOf(testCase.folderId) }}</span>
              </span>
              <span class="shrink-0 text-xs text-gray-500">{{ typeLabel(testCase.type) }}</span>
              <span class="shrink-0 rounded-full px-2 py-0.5 text-xs font-medium" :class="priorityBadgeClass(testCase.priority)">
                {{ priorityLabel(testCase.priority) }}
              </span>
            </label>
          </li>
        </ul>
      </div>
    </div>
  </div>
</template>
