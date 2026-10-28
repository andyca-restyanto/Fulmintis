<!-- frontend/src/modules/automation/components/GenerationResult.vue -->
<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import axios from 'axios';
import { automationService } from '../services/automation.service';
import type { AutomationGeneration } from '../types/automation.types';
import { buildFileTree, firstFilePath, zipFileName } from '../utils/automationFiles';
import { frameworkLabel, languageLabel, patternLabel } from '../utils/automationOptions';
import { describeAutomationError } from '../utils/automationErrors';
import CodeViewer from './CodeViewer.vue';
import FileTreeNode from './FileTreeNode.vue';
import DownloadIcon from '@/shared/components/icons/DownloadIcon.vue';
import AlertCircleIcon from '@/shared/components/icons/AlertCircleIcon.vue';
import SparklesIcon from '@/shared/components/icons/SparklesIcon.vue';

// Hasil generate yang BERHASIL: pohon berkas + penampil kode + unduh zip.
const props = defineProps<{
  projectId: string;
  generation: AutomationGeneration;
}>();

const tree = computed(() => buildFileTree(props.generation.files.map((file) => file.path)));
const selectedPath = ref<string | null>(null);
const selectedFile = computed(() => props.generation.files.find((file) => file.path === selectedPath.value) ?? null);

// Buka berkas pertama otomatis; ganti hasil -> pilih ulang.
watch(
  () => props.generation.id,
  () => {
    selectedPath.value = firstFilePath(tree.value);
  },
  { immediate: true }
);

const isDownloading = ref(false);
const downloadError = ref('');

async function download() {
  if (isDownloading.value) return;
  isDownloading.value = true;
  downloadError.value = '';
  try {
    const blob = await automationService.downloadGeneration(props.projectId, props.generation.id);
    // Unduhan lewat blob (bukan <a href> biasa) karena endpoint-nya butuh header JWT.
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = zipFileName(props.generation);
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
  } catch (err) {
    downloadError.value = axios.isAxiosError(err)
      ? describeAutomationError(err.response?.status, err.response?.data, 'Gagal mengunduh zip, coba lagi.').message
      : 'Tidak dapat terhubung ke server.';
  } finally {
    isDownloading.value = false;
  }
}
</script>

<template>
  <section class="rounded-2xl border border-gray-200">
    <div class="flex flex-wrap items-center justify-between gap-3 border-b border-gray-100 px-6 py-4">
      <div>
        <h3 class="text-base font-bold text-gray-900">Hasil generate</h3>
        <p class="mt-0.5 text-xs text-gray-500">
          {{ frameworkLabel(generation.framework) }} · {{ languageLabel(generation.language) }} ·
          {{ patternLabel(generation.pattern) }} · {{ generation.testCaseCount }} test case ·
          {{ generation.files.length }} berkas
        </p>
      </div>
      <button
        type="button"
        class="flex items-center gap-2 rounded-xl bg-gray-900 px-4 py-2 text-sm font-semibold text-white hover:bg-gray-800 disabled:cursor-not-allowed disabled:bg-gray-300"
        :disabled="isDownloading"
        @click="download"
      >
        <DownloadIcon :size="15" />
        {{ isDownloading ? 'Mengunduh...' : 'Download .zip' }}
      </button>
    </div>

    <div class="flex items-start gap-2.5 border-b border-amber-100 bg-amber-50/60 px-6 py-3 text-sm text-amber-800" role="note">
      <SparklesIcon :size="16" class="mt-0.5 shrink-0" />
      <p>
        <span class="font-semibold">AI-generated, review sebelum dijalankan.</span>
        Hasil berupa draft: tidak dijamin langsung bisa dikompilasi atau dijalankan, dan locator/URL perlu disesuaikan
        dengan aplikasi Anda.
      </p>
    </div>

    <p v-if="downloadError" class="flex items-start gap-2 px-6 pt-4 text-sm text-red-600" role="alert">
      <AlertCircleIcon :size="16" class="mt-0.5 shrink-0" />{{ downloadError }}
    </p>

    <div class="grid grid-cols-1 gap-4 p-6 lg:grid-cols-[260px_minmax(0,1fr)]">
      <nav aria-label="Berkas hasil generate" class="max-h-[460px] overflow-auto rounded-xl border border-gray-200 p-2">
        <ul class="space-y-0.5">
          <FileTreeNode
            v-for="node in tree"
            :key="node.path"
            :node="node"
            :selected-path="selectedPath"
            @select="selectedPath = $event"
          />
        </ul>
      </nav>
      <CodeViewer :file="selectedFile" />
    </div>

    <p v-if="generation.notes" class="border-t border-gray-100 px-6 py-3 text-xs text-gray-500">
      <span class="font-semibold text-gray-700">Catatan:</span> {{ generation.notes }}
    </p>
  </section>
</template>
