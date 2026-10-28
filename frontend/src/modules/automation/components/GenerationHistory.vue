<!-- frontend/src/modules/automation/components/GenerationHistory.vue -->
<script setup lang="ts">
import type { AutomationGenerationStatus, AutomationGenerationSummary } from '../types/automation.types';
import { describeGenerationFailure } from '../utils/automationErrors';
import { formatWhen } from '../utils/automationFiles';
import { frameworkLabel, languageLabel } from '../utils/automationOptions';

// Riwayat generate project (semua member, terbaru dulu, maks 20). Kegagalan sisi AI tampil sbg pesan umum.
defineProps<{
  items: AutomationGenerationSummary[];
  isLoading: boolean;
  viewingId: string | null;
}>();

const emit = defineEmits<{ (e: 'view', id: string): void }>();

const STATUS_STYLE: Record<AutomationGenerationStatus, string> = {
  QUEUED: 'bg-gray-100 text-gray-600',
  RUNNING: 'bg-blue-50 text-blue-600',
  SUCCEEDED: 'bg-emerald-50 text-emerald-700',
  FAILED: 'bg-red-50 text-red-600',
};
const STATUS_LABEL: Record<AutomationGenerationStatus, string> = {
  QUEUED: 'Antre',
  RUNNING: 'Berjalan',
  SUCCEEDED: 'Berhasil',
  FAILED: 'Gagal',
};
</script>

<template>
  <section class="rounded-2xl border border-gray-200">
    <div class="border-b border-gray-100 px-6 py-4">
      <h3 class="text-base font-bold text-gray-900">Riwayat generate</h3>
    </div>

    <p v-if="isLoading && items.length === 0" class="px-6 py-8 text-center text-sm text-gray-400">Memuat riwayat...</p>
    <p v-else-if="items.length === 0" class="px-6 py-8 text-center text-sm text-gray-400">Belum ada generate di project ini.</p>

    <ul v-else class="divide-y divide-gray-100">
      <li v-for="item in items" :key="item.id" class="px-6 py-3.5">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <div class="flex min-w-0 flex-wrap items-center gap-x-3 gap-y-1">
            <span class="rounded-full px-2.5 py-0.5 text-xs font-medium" :class="STATUS_STYLE[item.status]">
              {{ STATUS_LABEL[item.status] }}
            </span>
            <span class="text-sm font-medium text-gray-900">
              {{ frameworkLabel(item.framework) }} · {{ languageLabel(item.language) }}
            </span>
            <span class="text-xs text-gray-500">{{ item.testCaseCount }} test case</span>
          </div>
          <div class="flex items-center gap-4">
            <span class="text-xs text-gray-400">{{ item.requestedBy }} · {{ formatWhen(item.createdAt) }}</span>
            <button
              v-if="item.status === 'SUCCEEDED'"
              type="button"
              class="rounded-lg border px-3 py-1 text-xs font-medium"
              :class="viewingId === item.id ? 'border-gray-900 bg-gray-900 text-white' : 'border-gray-200 text-gray-700 hover:bg-gray-50'"
              :aria-pressed="viewingId === item.id"
              @click="emit('view', item.id)"
            >
              {{ viewingId === item.id ? 'Sedang dilihat' : 'Lihat' }}
            </button>
          </div>
        </div>
        <p v-if="item.status === 'FAILED'" class="mt-1.5 text-xs text-red-600">
          {{ describeGenerationFailure(item.errorCode, item.errorMessage) }}
        </p>
      </li>
    </ul>
  </section>
</template>
