<!-- frontend/src/modules/testrepository/components/TestFolderTreeItem.vue -->
<script setup lang="ts">
import { ref, computed } from 'vue';
import type { TestFolderTreeNode } from '../types/testFolder.types';
import ChevronDownIcon from '@/shared/components/icons/ChevronDownIcon.vue';
import ChevronRightIcon from '@/shared/components/icons/ChevronRightIcon.vue';
import FolderIcon from '@/shared/components/icons/FolderIcon.vue';

// Komponen ini me-refer diri sendiri (recursive) di <template> lewat nama
// filenya sendiri -- didukung otomatis oleh compiler SFC Vue 3 utk
// <script setup>, tidak perlu registrasi manual.
const props = defineProps<{
  node: TestFolderTreeNode;
  depth: number;
  selectedId: string | null;
  // Map folderId -> jumlah test case, di-supply dari TestRepositoryView
  // (hasil hitung dari daftar test case project ini). Opsional -- kalau
  // tidak di-pass, badge angka cukup tidak ditampilkan.
  counts?: Record<string, number>;
}>();

const emit = defineEmits<{ (e: 'select', folderId: string): void }>();

const isExpanded = ref(true); // default expanded, sesuai mockup

const testCaseCount = computed(() => props.counts?.[props.node.id] ?? 0);

function toggleExpand() {
  if (props.node.children.length > 0) {
    isExpanded.value = !isExpanded.value;
  }
}
</script>

<template>
  <div>
    <div
      class="flex cursor-pointer items-center gap-1.5 rounded-lg py-1.5 pr-2 text-sm"
      :class="
        selectedId === node.id
          ? 'bg-gray-100 font-medium text-gray-900'
          : 'text-gray-700 hover:bg-gray-50'
      "
      :style="{ paddingLeft: `${depth * 18 + 8}px` }"
      @click="emit('select', node.id)"
    >
      <button
        v-if="node.children.length > 0"
        type="button"
        class="shrink-0 text-gray-400 hover:text-gray-600"
        @click.stop="toggleExpand"
      >
        <ChevronDownIcon v-if="isExpanded" :size="14" />
        <ChevronRightIcon v-else :size="14" />
      </button>
      <span v-else class="inline-block w-[14px] shrink-0" />

      <FolderIcon :size="16" class="shrink-0 text-gray-400" />
      <span class="flex-1 truncate">{{ node.folderName }}</span>
      <span v-if="testCaseCount > 0" class="shrink-0 text-xs text-gray-400">{{ testCaseCount }}</span>
    </div>

    <div v-if="isExpanded && node.children.length > 0">
      <TestFolderTreeItem
        v-for="child in node.children"
        :key="child.id"
        :node="child"
        :depth="depth + 1"
        :selected-id="selectedId"
        :counts="counts"
        @select="(id) => emit('select', id)"
      />
    </div>
  </div>
</template>
