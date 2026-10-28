<!-- frontend/src/modules/admin/components/AdminConfirmDialog.vue -->
<script setup lang="ts">
import { ref, watch, nextTick } from 'vue';

const props = withDefaults(
  defineProps<{
    open: boolean;
    title: string;
    message: string;
    confirmLabel: string;
    cancelLabel?: string;
    /** true saat aksi sedang diproses: tombol terkunci dan dialog tidak bisa ditutup. */
    loading?: boolean;
    /** Tombol konfirmasi berwarna merah (aksi merusak/menutup akses). */
    danger?: boolean;
  }>(),
  { cancelLabel: 'Batal', loading: false, danger: false }
);

const emit = defineEmits<{ (e: 'confirm'): void; (e: 'cancel'): void }>();

const cancelButton = ref<HTMLButtonElement | null>(null);

// Fokus awal di tombol Batal (aksi aman) supaya Enter tidak langsung mengonfirmasi, dan Esc berfungsi.
watch(
  () => props.open,
  async (isOpen: boolean) => {
    if (isOpen) {
      await nextTick();
      cancelButton.value?.focus();
    }
  }
);

function onCancel() {
  if (!props.loading) {
    emit('cancel');
  }
}
</script>

<template>
  <div
    v-if="open"
    class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4"
    role="dialog"
    aria-modal="true"
    aria-labelledby="admin-confirm-title"
    @keydown.esc="onCancel"
    @click.self="onCancel"
  >
    <div class="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl">
      <h2 id="admin-confirm-title" class="text-lg font-bold text-gray-900">{{ title }}</h2>
      <p class="mt-2 text-sm text-gray-500">{{ message }}</p>

      <div class="mt-6 flex justify-end gap-3">
        <button
          ref="cancelButton"
          type="button"
          :disabled="loading"
          class="rounded-xl border border-gray-200 px-4 py-2.5 text-sm font-semibold text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
          @click="onCancel"
        >
          {{ cancelLabel }}
        </button>
        <button
          type="button"
          :disabled="loading"
          class="rounded-xl px-4 py-2.5 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-60"
          :class="danger ? 'bg-red-600 hover:bg-red-700' : 'bg-gray-900 hover:bg-gray-800'"
          @click="emit('confirm')"
        >
          {{ loading ? 'Memproses...' : confirmLabel }}
        </button>
      </div>
    </div>
  </div>
</template>
