<!-- frontend/src/modules/automation/components/FileTreeNode.vue -->
<script setup lang="ts">
import { ref } from 'vue';
import FolderIcon from '@/shared/components/icons/FolderIcon.vue';
import ChevronDownIcon from '@/shared/components/icons/ChevronDownIcon.vue';
import ChevronRightIcon from '@/shared/components/icons/ChevronRightIcon.vue';
import type { FileTreeNode as TreeNode } from '../utils/automationFiles';

// Satu simpul pohon berkas; memanggil dirinya sendiri (rekursif) untuk isi folder. Folder terbuka secara bawaan.
const props = defineProps<{
  node: TreeNode;
  selectedPath: string | null;
  depth?: number;
}>();

const emit = defineEmits<{ (e: 'select', path: string): void }>();

const isOpen = ref(true);
const indent = (props.depth ?? 0) * 14;
</script>

<template>
  <li>
    <button
      v-if="node.type === 'dir'"
      type="button"
      class="flex w-full items-center gap-1.5 rounded-lg py-1.5 pr-2 text-left text-sm text-gray-700 hover:bg-gray-100"
      :style="{ paddingLeft: `${indent + 6}px` }"
      :aria-expanded="isOpen"
      @click="isOpen = !isOpen"
    >
      <ChevronDownIcon v-if="isOpen" :size="14" class="shrink-0 text-gray-400" />
      <ChevronRightIcon v-else :size="14" class="shrink-0 text-gray-400" />
      <FolderIcon :size="14" class="shrink-0 text-gray-400" />
      <span class="truncate">{{ node.name }}</span>
    </button>

    <button
      v-else
      type="button"
      class="flex w-full items-center gap-1.5 rounded-lg py-1.5 pr-2 text-left text-sm"
      :class="selectedPath === node.path ? 'bg-gray-900 font-medium text-white' : 'text-gray-700 hover:bg-gray-100'"
      :style="{ paddingLeft: `${indent + 26}px` }"
      :aria-current="selectedPath === node.path ? 'true' : undefined"
      :title="node.path"
      @click="emit('select', node.path)"
    >
      <span class="truncate">{{ node.name }}</span>
    </button>

    <ul v-if="node.type === 'dir' && isOpen" class="space-y-0.5">
      <FileTreeNode
        v-for="child in node.children"
        :key="child.path"
        :node="child"
        :selected-path="selectedPath"
        :depth="(depth ?? 0) + 1"
        @select="emit('select', $event)"
      />
    </ul>
  </li>
</template>
