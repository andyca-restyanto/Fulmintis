<!-- frontend/src/modules/automation/components/CodeViewer.vue -->
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import type { AutomationFile } from '../types/automation.types';
import { byteLength, formatBytes, lineCount } from '../utils/automationFiles';

// Penampil satu berkas hasil generate. Teks mentah (tanpa highlight/library tambahan) di dalam <pre>:
// isi berasal dari AI dan SELALU dirender sbg teks ({{ }}), tidak pernah sebagai HTML.
const props = defineProps<{ file: AutomationFile | null }>();

const copied = ref(false);
const copyFailed = ref(false);
let resetTimer: ReturnType<typeof setTimeout> | undefined;

watch(
  () => props.file?.path,
  () => {
    copied.value = false;
    copyFailed.value = false;
  }
);

const meta = computed(() =>
  props.file ? `${lineCount(props.file.content)} baris · ${formatBytes(byteLength(props.file.content))}` : ''
);

async function copy() {
  if (!props.file) return;
  copyFailed.value = false;
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(props.file.content);
    } else {
      // Cadangan utk konteks tanpa Clipboard API (mis. http non-localhost).
      const area = document.createElement('textarea');
      area.value = props.file.content;
      area.style.position = 'fixed';
      area.style.opacity = '0';
      document.body.appendChild(area);
      area.select();
      const success = document.execCommand('copy');
      area.remove();
      if (!success) throw new Error('copy gagal');
    }
    copied.value = true;
  } catch {
    copyFailed.value = true;
  }
  if (resetTimer) clearTimeout(resetTimer);
  resetTimer = setTimeout(() => {
    copied.value = false;
    copyFailed.value = false;
  }, 2000);
}

onBeforeUnmount(() => {
  if (resetTimer) clearTimeout(resetTimer);
});
</script>

<template>
  <div class="flex min-h-[240px] min-w-0 flex-col overflow-hidden rounded-xl border border-gray-200">
    <template v-if="file">
      <div class="flex items-center justify-between gap-3 border-b border-gray-100 bg-gray-50 px-4 py-2.5">
        <div class="min-w-0">
          <p class="truncate text-sm font-semibold text-gray-900" :title="file.path">{{ file.path }}</p>
          <p class="text-xs text-gray-400">{{ meta }}</p>
        </div>
        <button
          type="button"
          class="shrink-0 rounded-lg border border-gray-200 bg-white px-3 py-1.5 text-xs font-medium text-gray-700 hover:bg-gray-50"
          @click="copy"
        >
          {{ copied ? 'Disalin' : copyFailed ? 'Gagal menyalin' : 'Copy' }}
        </button>
      </div>
      <pre
        class="max-h-[460px] flex-1 overflow-auto bg-white p-4 text-xs leading-relaxed text-gray-800"
        tabindex="0"
        aria-label="Isi berkas"
      ><code>{{ file.content }}</code></pre>
    </template>
    <div v-else class="flex flex-1 items-center justify-center text-sm text-gray-400">Pilih berkas di sebelah kiri</div>
  </div>
</template>
