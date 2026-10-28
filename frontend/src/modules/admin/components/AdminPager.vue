<!-- frontend/src/modules/admin/components/AdminPager.vue -->
<script setup lang="ts">
import { computed } from 'vue';
import { pageSummary } from '../utils/adminList';

// `page` = indeks halaman mulai dari 0 (sama dengan API).
const props = defineProps<{
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
  /** true saat data sedang dimuat: kedua tombol terkunci agar tidak ada klik ganda. */
  disabled?: boolean;
}>();

const emit = defineEmits<{ (e: 'change', page: number): void }>();

const summary = computed(() => pageSummary(props.page, props.size, props.totalItems));
const currentLabel = computed(() => (props.totalPages === 0 ? 0 : props.page + 1));
const canPrev = computed(() => !props.disabled && props.page > 0);
const canNext = computed(() => !props.disabled && props.page < props.totalPages - 1);
</script>

<template>
  <div class="mt-4 flex flex-wrap items-center justify-between gap-3 text-sm text-gray-500">
    <p>{{ summary }}</p>
    <div class="flex items-center gap-3">
      <button
        type="button"
        :disabled="!canPrev"
        class="rounded-lg border border-gray-200 px-3 py-1.5 font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-40"
        @click="emit('change', page - 1)"
      >
        Sebelumnya
      </button>
      <span>Halaman {{ currentLabel }} dari {{ totalPages }}</span>
      <button
        type="button"
        :disabled="!canNext"
        class="rounded-lg border border-gray-200 px-3 py-1.5 font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-40"
        @click="emit('change', page + 1)"
      >
        Berikutnya
      </button>
    </div>
  </div>
</template>
