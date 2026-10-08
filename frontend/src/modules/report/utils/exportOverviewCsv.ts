// frontend/src/modules/report/utils/exportOverviewCsv.ts
import type { ReportOverviewResponse } from '../types/report.types';

// Tombol di mockup berlabel "Export Excel", tapi project ini TIDAK punya
// dependency pembuat file .xlsx asli (mis. SheetJS) -- lihat package.json,
// cuma vue/vue-router/axios -- dan sandbox tidak bisa `npm install` paket
// baru (jaringan dimatikan). CSV dipilih sbg pendekatan paling dekat yang
// bisa jalan tanpa dependency tambahan: Excel bisa membuka file .csv
// langsung. Kalau nanti SheetJS di-install, ganti isi function ini jadi
// generate .xlsx asli tanpa perlu ubah pemanggilnya di ReportsView.vue.
export function exportOverviewToCsv(projectName: string, overview: ReportOverviewResponse): void {
  const rows: Array<[string, string | number]> = [
    ['Metric', 'Value'],
    ['Total Runs', overview.totalTestRuns],
    ['Total Executions', overview.totalExecutions],
    ['Passed', overview.totalPassed],
    ['Failed', overview.totalFailed],
    ['Blocked', overview.totalBlocked],
    ['Pending', overview.totalPending],
    ['Pass Rate (%)', overview.passRatePercentage],
  ];
  const csvContent = rows.map((row) => row.map(escapeCsvCell).join(',')).join('\r\n');

  // Prefix BOM supaya Excel mendeteksi encoding UTF-8 dgn benar (tanpa ini,
  // karakter non-ASCII di nama project bisa tampil mojibake di Excel).
  const blob = new Blob(['\ufeff' + csvContent], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `${slugify(projectName)}-report-overview.csv`;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

function escapeCsvCell(value: string | number): string {
  const text = String(value);
  return /[",\r\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

function slugify(text: string): string {
  const slug = text
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
  return slug || 'project';
}
