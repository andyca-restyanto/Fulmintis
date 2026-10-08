// frontend/src/shared/composables/useToast.ts
import { ref } from 'vue';

// Notifikasi singkat (toast) yang bisa dipanggil dari komponen mana pun:
//   const { pushToast } = useToast();
//   pushToast({ type: 'success', message: 'Import berhasil: 12 test case ...' });
// State-nya module-level (satu daftar untuk seluruh aplikasi); ditampilkan oleh
// <ToastContainer /> -- komponen itu harus dipasang SEKALI di layout yang
// membungkus halaman pemanggil (saat ini ProjectLayoutView; App.vue tidak
// dipakai karena file itu di luar lingkup perubahan).

export type ToastType = 'success' | 'error' | 'info';

export interface Toast {
  id: number;
  type: ToastType;
  message: string;
}

const DEFAULT_DURATION_MS = 6000;
const MAX_VISIBLE = 4;

const toasts = ref<Toast[]>([]);
const timers = new Map<number, ReturnType<typeof setTimeout>>();
let nextId = 1;

function dismissToast(id: number) {
  const timer = timers.get(id);
  if (timer) {
    clearTimeout(timer);
    timers.delete(id);
  }
  toasts.value = toasts.value.filter((toast: Toast) => toast.id !== id);
}

function pushToast(input: { type?: ToastType; message: string; durationMs?: number }): number {
  const id = nextId++;
  toasts.value = [...toasts.value, { id, type: input.type ?? 'info', message: input.message }].slice(-MAX_VISIBLE);

  const duration = input.durationMs ?? DEFAULT_DURATION_MS;
  if (duration > 0) {
    timers.set(id, setTimeout(() => dismissToast(id), duration));
  }
  return id;
}

export function useToast() {
  return { toasts, pushToast, dismissToast };
}
