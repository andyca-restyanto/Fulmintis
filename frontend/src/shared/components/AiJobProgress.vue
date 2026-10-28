<!-- frontend/src/shared/components/AiJobProgress.vue -->
<script setup lang="ts">
import { computed } from 'vue';
import type { AiJobStatus } from '../utils/aiPolling';
import { formatElapsed } from '../utils/aiPolling';

// Indikator selama job AI diproses di server (status dari polling). Sengaja TIDAK memakai persentase palsu:
// lama proses AI tidak bisa diperkirakan, jadi yang ditampilkan status nyata + waktu berjalan. Teks pesan dan catatan
// kaki ditentukan fitur pemanggil (tiap fitur punya kalimat sendiri).
const props = defineProps<{
  status: AiJobStatus;
  elapsedMs: number;
  message: string;
  footnote?: string;
}>();

const elapsed = computed(() => formatElapsed(props.elapsedMs));
</script>

<template>
  <div class="rounded-2xl border border-gray-200 p-6" role="status" aria-live="polite">
    <div class="flex items-center gap-4">
      <span
        class="h-6 w-6 shrink-0 animate-spin rounded-full border-2 border-gray-200 border-t-gray-900"
        aria-hidden="true"
      />
      <div class="min-w-0 flex-1">
        <p class="text-sm font-semibold text-gray-900">
          {{ status === 'QUEUED' ? 'Dalam antrean' : 'Sedang digenerate' }}
          <span class="ml-2 font-normal tabular-nums text-gray-400">{{ elapsed }}</span>
        </p>
        <p class="mt-0.5 text-sm text-gray-500">{{ message }}</p>
      </div>
    </div>
    <div class="mt-4 h-1.5 w-full overflow-hidden rounded-full bg-gray-100">
      <div class="h-full w-1/3 animate-pulse rounded-full bg-gray-900" />
    </div>
    <p v-if="footnote" class="mt-3 text-xs text-gray-400">{{ footnote }}</p>
  </div>
</template>
