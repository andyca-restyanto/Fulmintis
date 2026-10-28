<!-- frontend/src/modules/project/components/ProjectHealthChart.vue -->
<script setup lang="ts">
import { computed } from 'vue';
import type { HealthBreakdown } from '../types/projectDashboard.types';
import { buildHealthSegments } from '../utils/dashboardCharts';

// Donut kondisi project, dihitung SAMA dengan Report: setiap hasil eksekusi di
// semua test run, per status (Passed / Failed / Blocked / Not Run; Not Run =
// field `pending` = NEW + PENDING). Angka di tengah = jumlah HASIL EKSEKUSI
// (= totalExecutions di Report), BUKAN Total Test Cases di kartu statistik:
// test case yang sama di 2 run dihitung 2x, dan test case yang belum masuk
// test run tidak dihitung.
const props = defineProps<{
  health: HealthBreakdown;
}>();

const RADIUS = 44;
const STROKE = 16;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;

const segments = computed(() => buildHealthSegments(props.health, CIRCUMFERENCE));
const total = computed(() => segments.value.reduce((sum, s) => sum + s.count, 0));
</script>

<template>
  <div>
    <p class="mb-5 text-xs text-gray-400">Results across all test runs</p>

    <div v-if="segments.length === 0" class="flex h-52 items-center justify-center text-sm text-gray-400">
      No test run results yet
    </div>

    <div v-else class="flex flex-col items-center gap-6 sm:flex-row sm:justify-center">
      <div class="relative h-44 w-44 shrink-0">
        <svg viewBox="0 0 120 120" class="h-full w-full" role="img" :aria-label="`Project health, ${total} results`">
          <circle cx="60" cy="60" :r="RADIUS" fill="none" stroke="#F3F4F6" :stroke-width="STROKE" />
          <circle
            v-for="segment in segments"
            :key="segment.key"
            cx="60"
            cy="60"
            :r="RADIUS"
            fill="none"
            :stroke="segment.color"
            :stroke-width="STROKE"
            :stroke-dasharray="segment.dashArray"
            :transform="`rotate(${segment.rotation} 60 60)`"
          />
        </svg>
        <div class="absolute inset-0 flex flex-col items-center justify-center">
          <span class="text-2xl font-bold text-gray-900">{{ total }}</span>
          <span class="text-xs text-gray-400">results</span>
        </div>
      </div>

      <ul class="w-full max-w-[220px] space-y-2.5">
        <li v-for="segment in segments" :key="segment.key" class="flex items-center justify-between text-sm">
          <span class="flex items-center gap-2 text-gray-600">
            <span class="h-2.5 w-2.5 rounded-full ring-1 ring-inset ring-black/10" :style="{ backgroundColor: segment.color }" />
            {{ segment.label }}
          </span>
          <span class="tabular-nums text-gray-500">
            <span class="font-semibold text-gray-900">{{ segment.count }}</span>
            <span class="ml-1.5 text-xs text-gray-400">{{ segment.percent }}%</span>
          </span>
        </li>
      </ul>
    </div>
  </div>
</template>
