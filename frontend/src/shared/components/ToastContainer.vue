<!-- frontend/src/shared/components/ToastContainer.vue -->
<script setup lang="ts">
import { useToast, type ToastType } from '../composables/useToast';
import CheckCircleIcon from './icons/CheckCircleIcon.vue';
import AlertCircleIcon from './icons/AlertCircleIcon.vue';
import XIcon from './icons/XIcon.vue';

// Pasang SEKALI di layout (lihat ProjectLayoutView). Daftar notifikasi diisi
// lewat useToast().pushToast(...).
const { toasts, dismissToast } = useToast();

const STYLE: Record<ToastType, string> = {
  success: 'border-emerald-200 bg-emerald-50 text-emerald-900',
  error: 'border-red-200 bg-red-50 text-red-900',
  info: 'border-gray-200 bg-white text-gray-900',
};
const ICON_CLASS: Record<ToastType, string> = {
  success: 'text-emerald-600',
  error: 'text-red-600',
  info: 'text-gray-500',
};
</script>

<template>
  <!-- aria-live: pembaca layar mengumumkan notifikasi baru tanpa memindahkan fokus -->
  <div
    class="pointer-events-none fixed right-4 top-4 z-[60] flex w-full max-w-sm flex-col gap-3"
    role="status"
    aria-live="polite"
  >
    <div
      v-for="toast in toasts"
      :key="toast.id"
      class="pointer-events-auto flex items-start gap-3 rounded-xl border px-4 py-3 text-sm shadow-lg"
      :class="STYLE[toast.type]"
    >
      <CheckCircleIcon v-if="toast.type === 'success'" :size="18" class="mt-0.5 shrink-0" :class="ICON_CLASS[toast.type]" />
      <AlertCircleIcon v-else :size="18" class="mt-0.5 shrink-0" :class="ICON_CLASS[toast.type]" />
      <p class="min-w-0 flex-1 break-words font-medium">{{ toast.message }}</p>
      <button
        type="button"
        class="shrink-0 text-gray-400 hover:text-gray-700"
        aria-label="Tutup notifikasi"
        @click="dismissToast(toast.id)"
      >
        <XIcon :size="16" />
      </button>
    </div>
  </div>
</template>
