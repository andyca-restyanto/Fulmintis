// frontend/src/modules/project/utils/dashboardCharts.ts
// Fungsi murni (tanpa Vue) untuk grafik dashboard -- dipisah supaya mudah diuji.

import type { HealthBreakdown, PriorityBreakdown, TimelinePoint } from '../types/projectDashboard.types';

// ---- Warna: disamakan dgn modul Report/Test Run/Test Case ----
export const STATUS_COLORS = {
  passed: '#10B981', // emerald-500
  failed: '#EF4444', // red-500
  blocked: '#FBBF24', // amber-400
  notRun: '#E5E7EB', // gray-200 -- warna segmen "Not Run" (field API `pending`)
} as const;

export const PRIORITY_COLORS = {
  highest: '#B91C1C', // red-700   (= badge HIGHEST di modul testcase)
  high: '#F97316', // orange-500
  medium: '#3B82F6', // blue-500
  low: '#15803D', // green-700
} as const;

// ---- Health (donut) ----

export interface DonutSegment {
  key: keyof HealthBreakdown;
  label: string;
  count: number;
  percent: number; // 0..100, dibulatkan 1 desimal
  color: string;
  /** stroke-dasharray = "panjang busur sisa-keliling" utk <circle> dgn keliling `circumference`. */
  dashArray: string;
  /**
   * Sudut awal segmen (derajat) utk transform="rotate(<rotation> cx cy)". Segmen
   * diputar ke posisinya, BUKAN digeser dgn stroke-dashoffset -- lebih konsisten
   * antar renderer. Mulai dari jam 12 (-90) searah jarum jam.
   */
  rotation: number;
}

// Field API `pending` (NEW + PENDING, = totalPending di Report) ditampilkan sebagai
// "Not Run". Nama field sengaja tidak diubah supaya identik dengan Report.
const HEALTH_ORDER: { key: keyof HealthBreakdown; label: string; color: string }[] = [
  { key: 'passed', label: 'Passed', color: STATUS_COLORS.passed },
  { key: 'failed', label: 'Failed', color: STATUS_COLORS.failed },
  { key: 'blocked', label: 'Blocked', color: STATUS_COLORS.blocked },
  { key: 'pending', label: 'Not Run', color: STATUS_COLORS.notRun },
];

/** Angka dari API; nilai hilang/NaN (mis. backend versi lama) dianggap 0, bukan merusak donut. */
function countOf(health: HealthBreakdown, key: keyof HealthBreakdown): number {
  const value = health[key];
  return Number.isFinite(value) ? value : 0;
}

/** Segmen donut (hanya yang count > 0). Total 0 -> array kosong (tampilkan empty state). */
export function buildHealthSegments(health: HealthBreakdown, circumference: number): DonutSegment[] {
  const total = HEALTH_ORDER.reduce((sum, { key }) => sum + countOf(health, key), 0);
  if (total <= 0) return [];

  const segments: DonutSegment[] = [];
  let consumed = 0;
  for (const { key, label, color } of HEALTH_ORDER) {
    const count = countOf(health, key);
    if (count <= 0) continue;
    const length = (count / total) * circumference;
    segments.push({
      key,
      label,
      count,
      percent: Math.round((count / total) * 1000) / 10,
      color,
      dashArray: `${length} ${circumference - length}`,
      rotation: round2(-90 + (consumed / circumference) * 360),
    });
    consumed += length;
  }
  return segments;
}

// ---- Priority (batang horizontal) ----

export interface PriorityBar {
  key: keyof PriorityBreakdown;
  label: string;
  count: number;
  color: string;
  /** Lebar batang 0..100 relatif terhadap prioritas terbesar. */
  widthPercent: number;
}

export function buildPriorityBars(priority: PriorityBreakdown): PriorityBar[] {
  const rows: { key: keyof PriorityBreakdown; label: string }[] = [
    { key: 'highest', label: 'Highest' },
    { key: 'high', label: 'High' },
    { key: 'medium', label: 'Medium' },
    { key: 'low', label: 'Low' },
  ];
  const max = Math.max(...rows.map(({ key }) => priority[key]), 0);
  return rows.map(({ key, label }) => ({
    key,
    label,
    count: priority[key],
    color: PRIORITY_COLORS[key],
    widthPercent: max > 0 ? (priority[key] / max) * 100 : 0,
  }));
}

// ---- Timeline ----

/**
 * Skala sumbu Y bilangan bulat dgn `intervals` interval sama. max >= maxValue.
 * Step = ceil(maxValue / intervals). Step <= 10 dipakai apa adanya; di atasnya
 * dibulatkan ke atas ke kelipatan setengah orde besaran supaya label rapi
 * (step 11 -> 15, 33 -> 35, 47 -> 50, 130 -> 150). Contoh: maxValue 130 ->
 * step 33 -> 35 -> sumbu 0..140.
 */
export function niceAxis(maxValue: number, intervals = 4): { max: number; ticks: number[] } {
  let step = Math.max(1, Math.ceil(Math.max(maxValue, 0) / intervals));
  if (step > 10) {
    const half = Math.pow(10, Math.floor(Math.log10(step))) / 2;
    step = Math.ceil(step / half) * half;
  }
  const max = step * intervals;
  const ticks: number[] = [];
  for (let i = intervals; i >= 0; i--) ticks.push(step * i);
  return { max, ticks };
}

export interface TimelineSeries {
  key: 'passed' | 'failed' | 'blocked';
  label: string;
  color: string;
  total: number;
  /** Nilai "x,y" utk atribut points <polyline> pada kanvas width x height. */
  points: string;
}

/** Nilai terbesar dari SEMUA seri (bukan ditumpuk) -- dasar skala sumbu Y. */
export function timelineMaxValue(points: TimelinePoint[]): number {
  return points.reduce((max, p) => Math.max(max, p.passed, p.failed, p.blocked), 0);
}

export function buildTimelineSeries(
  points: TimelinePoint[],
  axisMax: number,
  width: number,
  height: number
): TimelineSeries[] {
  const defs: { key: TimelineSeries['key']; label: string; color: string }[] = [
    { key: 'passed', label: 'Passed', color: STATUS_COLORS.passed },
    { key: 'failed', label: 'Failed', color: STATUS_COLORS.failed },
    { key: 'blocked', label: 'Blocked', color: STATUS_COLORS.blocked },
  ];
  const lastIndex = Math.max(points.length - 1, 1);
  return defs.map(({ key, label, color }) => ({
    key,
    label,
    color,
    total: points.reduce((sum, p) => sum + p[key], 0),
    points: points
      .map((p, i) => {
        const x = (i / lastIndex) * width;
        const y = height - (p[key] / axisMax) * height;
        return `${round2(x)},${round2(y)}`;
      })
      .join(' '),
  }));
}

/** Indeks titik yg ditampilkan sbg label sumbu X: `count` titik tersebar merata (termasuk ujung). */
export function labelIndexes(length: number, count = 6): number[] {
  if (length <= 0) return [];
  if (length <= count) return Array.from({ length }, (_, i) => i);
  return Array.from({ length: count }, (_, k) => Math.round((k * (length - 1)) / (count - 1)));
}

/** "2026-09-03" -> "03 Sep". Diurai manual (bukan new Date(string)) supaya tidak bergeser zona waktu. */
export function formatTimelineDate(isoDate: string): string {
  const [year, month, day] = isoDate.split('-').map(Number);
  if (!year || !month || !day) return isoDate;
  return new Date(year, month - 1, day).toLocaleDateString('en-GB', { day: '2-digit', month: 'short' });
}

function round2(value: number): number {
  return Math.round(value * 100) / 100;
}
