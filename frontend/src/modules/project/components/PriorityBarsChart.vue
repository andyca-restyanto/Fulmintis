<!-- frontend/src/modules/project/components/PriorityBarsChart.vue -->
<script setup lang="ts">
import { computed } from 'vue';
import type { PriorityBreakdown } from '../types/projectDashboard.types';
import { buildPriorityBars } from '../utils/dashboardCharts';

// Jumlah test case AKTIF per prioritas. Warna disamakan dgn badge prioritas di
// modul Test Repository (Highest merah tua, High oranye, Medium biru, Low hijau).
const props = defineProps<{
  priority: PriorityBreakdown;
}>();

const bars = computed(() => buildPriorityBars(props.priority));
const hasData = computed(() => bars.value.some((bar) => bar.count > 0));

// Batang kecil tetap terlihat (min 3%) supaya prioritas yang jumlahnya ada tidak tampak kosong.
function barWidth(count: number, widthPercent: number): string {
  return count > 0 ? `${Math.max(widthPercent, 3)}%` : '0%';
}
</script>

<template>
  <div>
    <div v-if="!hasData" class="flex h-52 items-center justify-center text-sm text-gray-400">
      No test cases yet
    </div>

    <ul v-else class="space-y-5 py-2">
      <li v-for="bar in bars" :key="bar.key" class="flex items-center gap-3">
        <span class="w-16 shrink-0 text-sm text-gray-600">{{ bar.label }}</span>
        <div class="h-3 flex-1 overflow-hidden rounded-full bg-gray-100">
          <div
            class="h-full rounded-full transition-all"
            :style="{ width: barWidth(bar.count, bar.widthPercent), backgroundColor: bar.color }"
          />
        </div>
        <span class="w-8 shrink-0 text-right text-sm font-semibold tabular-nums text-gray-900">{{ bar.count }}</span>
      </li>
    </ul>
  </div>
</template>
