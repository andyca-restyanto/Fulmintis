<!-- frontend/src/modules/testrun/components/EvidenceViewerModal.vue -->
<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue';
import { testRunService } from '../services/testRun.service';
import { evidenceKindOf, type EvidenceKind } from '../utils/evidenceTypes';
import XIcon from '@/shared/components/icons/XIcon.vue';
import DownloadIcon from '@/shared/components/icons/DownloadIcon.vue';

// Menampilkan evidence DI DALAM halaman (modal) lewat <img>/<video>, bukan
// window.open(blobUrl). Alasan keamanan: blob URL berasal dari origin aplikasi
// ini, jadi kalau file-nya ternyata HTML/SVG berskrip, membukanya sebagai
// halaman akan menjalankan skrip itu dengan akses ke localStorage (JWT).
// <img>/<video> tidak pernah mengeksekusi skrip, dan tipe yang tidak ada di
// whitelist TIDAK ditampilkan -- hanya ditawarkan sebagai download.
const props = defineProps<{
  evidenceUrl: string;
  title?: string;
}>();

const emit = defineEmits<{ (e: 'close'): void }>();

const isLoading = ref(true);
const errorMessage = ref('');
const objectUrl = ref<string | null>(null);
const kind = ref<EvidenceKind | null>(null);

function revoke() {
  if (objectUrl.value) {
    URL.revokeObjectURL(objectUrl.value);
    objectUrl.value = null;
  }
}

async function load() {
  isLoading.value = true;
  errorMessage.value = '';
  try {
    const blob = await testRunService.fetchEvidenceBlob(props.evidenceUrl);
    kind.value = evidenceKindOf(blob.type);
    objectUrl.value = URL.createObjectURL(blob);
  } catch {
    errorMessage.value = 'Gagal memuat evidence.';
  } finally {
    isLoading.value = false;
  }
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') emit('close');
}

onMounted(() => {
  window.addEventListener('keydown', handleKeydown);
  load();
});

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown);
  revoke();
});
</script>

<template>
  <div
    class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 px-4 py-6"
    @click.self="emit('close')"
  >
    <div
      class="flex max-h-full w-full max-w-4xl flex-col rounded-2xl bg-white shadow-xl"
      role="dialog"
      aria-modal="true"
      aria-label="Evidence"
    >
      <div class="flex items-center justify-between border-b border-gray-100 px-5 py-3">
        <h3 class="truncate text-sm font-semibold text-gray-900">{{ title || 'Evidence' }}</h3>
        <button
          type="button"
          class="rounded-lg p-1.5 text-gray-400 hover:bg-gray-100 hover:text-gray-700"
          aria-label="Tutup"
          @click="emit('close')"
        >
          <XIcon :size="18" />
        </button>
      </div>

      <div class="flex min-h-[200px] flex-1 items-center justify-center overflow-auto bg-gray-50 p-4">
        <p v-if="isLoading" class="text-sm text-gray-400">Memuat...</p>
        <p v-else-if="errorMessage" class="text-sm text-red-600">{{ errorMessage }}</p>

        <img
          v-else-if="objectUrl && kind === 'image'"
          :src="objectUrl"
          alt="Evidence"
          class="max-h-[70vh] max-w-full rounded-lg object-contain"
        />
        <video
          v-else-if="objectUrl && kind === 'video'"
          :src="objectUrl"
          controls
          class="max-h-[70vh] max-w-full rounded-lg"
        />

        <!-- Tipe tidak dikenal (mis. file lama sebelum validasi isi file ada):
             TIDAK dirender, hanya bisa diunduh. -->
        <div v-else-if="objectUrl" class="text-center">
          <p class="text-sm text-gray-600">Format file ini tidak bisa ditampilkan di sini.</p>
          <a
            :href="objectUrl"
            download="evidence"
            class="mt-3 inline-flex items-center gap-2 rounded-xl border border-gray-200 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100"
          >
            <DownloadIcon :size="15" />
            Download
          </a>
        </div>
      </div>
    </div>
  </div>
</template>
