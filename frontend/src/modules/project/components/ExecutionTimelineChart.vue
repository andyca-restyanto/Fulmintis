<!-- frontend/src/modules/project/components/ExecutionTimelineChart.vue -->
<script setup lang="ts">
import { computed, ref } from 'vue';
import ActivityIcon from '@/shared/components/icons/ActivityIcon.vue';
import type { TimelinePoint } from '../types/projectDashboard.types';
import {
  STATUS_COLORS,
  buildTimelineSeries,
  formatTimelineDate,
  labelIndexes,
  niceAxis,
  timelineMaxValue,
} from '../utils/dashboardCharts';

// Eksekusi per hari (Passed / Failed / Blocked) selama 30 hari terakhir dari
// GET /api/projects/{id}/dashboard. Backend SELALU mengirim 30 titik berurutan
// dengan hari kosong bernilai 0, jadi chart tidak perlu mengisi celah.
const props = defineProps<{
  points: TimelinePoint[];
}>();

const CHART_WIDTH = 1200;
const CHART_HEIGHT = 220;

// Sumbu Y mengikuti nilai terbesar dari ketiga seri (bukan ditumpuk).
const axis = computed(() => niceAxis(timelineMaxValue(props.points)));
const series = computed(() =>
  buildTimelineSeries(props.points, axis.value.max, CHART_WIDTH, CHART_HEIGHT)
);
const hasData = computed(() => series.value.some((s) => s.total > 0));

function yToPixel(value: number): number {
  return CHART_HEIGHT - (value / axis.value.max) * CHART_HEIGHT;
}

// Label sumbu X: 6 tanggal tersebar merata, diposisikan sesuai titik datanya.
const xLabels = computed(() => {
  const lastIndex = Math.max(props.points.length - 1, 1);
  return labelIndexes(props.points.length).map((index, order, all) => ({
    key: index,
    text: formatTimelineDate(props.points[index]!.date),
    leftPercent: (index / lastIndex) * 100,
    // ujung kiri rata kiri, ujung kanan rata kanan, sisanya di tengah titik
    align: order === 0 ? 'translateX(0)' : order === all.length - 1 ? 'translateX(-100%)' : 'translateX(-50%)',
  }));
});

const ariaLabel = computed(() => {
  const [passed, failed, blocked] = series.value.map((s) => s.total);
  return `Execution timeline 30 hari terakhir: ${passed ?? 0} passed, ${failed ?? 0} failed, ${blocked ?? 0} blocked`;
});

// ---- Tooltip saat hover ----
const plotRef = ref<HTMLElement | null>(null);
const hoverIndex = ref<number | null>(null);

function handleMove(event: MouseEvent) {
  const el = plotRef.value;
  if (!el || props.points.length === 0) return;
  const rect = el.getBoundingClientRect();
  if (rect.width === 0) return;
  const ratio = Math.min(1, Math.max(0, (event.clientX - rect.left) / rect.width));
  hoverIndex.value = Math.round(ratio * (props.points.length - 1));
}

function handleLeave() {
  hoverIndex.value = null;
}

const hoverPoint = computed(() =>
  hoverIndex.value === null ? null : (props.points[hoverIndex.value] ?? null)
);
const hoverLeftPercent = computed(() => {
  if (hoverIndex.value === null || props.points.length < 2) return 0;
  return (hoverIndex.value / (props.points.length - 1)) * 100;
});
</script>

<template>
  <div class="rounded-2xl border border-gray-200 p-6">
    <div class="mb-6 flex flex-wrap items-center justify-between gap-3">
      <h2 class="flex items-center gap-2 text-base font-bold text-gray-900">
        <ActivityIcon :size="18" />
        Execution Timeline (Last 30 Days)
      </h2>
      <ul class="flex flex-wrap items-center gap-4 text-xs text-gray-500">
        <li v-for="item in series" :key="item.key" class="flex items-center gap-1.5">
          <span class="h-2 w-2 rounded-full" :style="{ backgroundColor: item.color }" />
          {{ item.label }}
          <span class="font-semibold text-gray-700">{{ item.total }}</span>
        </li>
      </ul>
    </div>

    <div class="flex gap-3">
      <div
        class="flex shrink-0 flex-col justify-between py-1 text-xs text-gray-400"
        :style="{ height: `${CHART_HEIGHT}px` }"
      >
        <span v-for="tick in axis.ticks" :key="tick">{{ tick }}</span>
      </div>

      <div class="min-w-0 flex-1">
        <div
          ref="plotRef"
          class="relative"
          :style="{ height: `${CHART_HEIGHT}px` }"
          @mousemove="handleMove"
          @mouseleave="handleLeave"
        >
          <svg
            :viewBox="`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`"
            class="h-full w-full overflow-visible"
            preserveAspectRatio="none"
            role="img"
            :aria-label="ariaLabel"
          >
            <line
              v-for="tick in axis.ticks"
              :key="`grid-${tick}`"
              x1="0"
              :x2="CHART_WIDTH"
              :y1="yToPixel(tick)"
              :y2="yToPixel(tick)"
              stroke="#EEEEEE"
              stroke-dasharray="4 4"
              vector-effect="non-scaling-stroke"
            />
            <!-- Tanpa data: satu garis dasar netral (bukan 3 garis berwarna bertumpuk) -->
            <line
              v-if="!hasData"
              x1="0"
              :x2="CHART_WIDTH"
              :y1="yToPixel(0)"
              :y2="yToPixel(0)"
              stroke="#D1D5DB"
              stroke-width="2.5"
              vector-effect="non-scaling-stroke"
            />
            <template v-else>
              <polyline
                v-for="item in series"
                :key="item.key"
                :points="item.points"
                fill="none"
                :stroke="item.color"
                stroke-width="2.5"
                stroke-linecap="round"
                stroke-linejoin="round"
                vector-effect="non-scaling-stroke"
              />
            </template>
          </svg>

          <p
            v-if="!hasData"
            class="pointer-events-none absolute inset-0 flex items-center justify-center text-sm text-gray-400"
          >
            No executions in the last 30 days
          </p>

          <!-- Garis & tooltip hover -->
          <template v-if="hoverPoint">
            <div
              class="pointer-events-none absolute inset-y-0 w-px bg-gray-300"
              :style="{ left: `${hoverLeftPercent}%` }"
            />
            <div
              class="pointer-events-none absolute top-0 z-10 w-36 rounded-xl border border-gray-200 bg-white p-3 text-xs shadow-lg"
              :style="{
                left: `${hoverLeftPercent}%`,
                transform: hoverLeftPercent > 70 ? 'translateX(calc(-100% - 8px))' : 'translateX(8px)',
              }"
            >
              <p class="mb-1.5 font-semibold text-gray-900">{{ formatTimelineDate(hoverPoint.date) }}</p>
              <p class="flex items-center justify-between text-gray-500">
                <span class="flex items-center gap-1.5">
                  <span class="h-2 w-2 rounded-full" :style="{ backgroundColor: STATUS_COLORS.passed }" />Passed
                </span>
                <span class="font-semibold text-gray-800">{{ hoverPoint.passed }}</span>
              </p>
              <p class="flex items-center justify-between text-gray-500">
                <span class="flex items-center gap-1.5">
                  <span class="h-2 w-2 rounded-full" :style="{ backgroundColor: STATUS_COLORS.failed }" />Failed
                </span>
                <span class="font-semibold text-gray-800">{{ hoverPoint.failed }}</span>
              </p>
              <p class="flex items-center justify-between text-gray-500">
                <span class="flex items-center gap-1.5">
                  <span class="h-2 w-2 rounded-full" :style="{ backgroundColor: STATUS_COLORS.blocked }" />Blocked
                </span>
                <span class="font-semibold text-gray-800">{{ hoverPoint.blocked }}</span>
              </p>
            </div>
          </template>
        </div>

        <div class="relative mt-2 h-4 text-xs text-gray-400">
          <span
            v-for="label in xLabels"
            :key="label.key"
            class="absolute whitespace-nowrap"
            :style="{ left: `${label.leftPercent}%`, transform: label.align }"
          >
            {{ label.text }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>
