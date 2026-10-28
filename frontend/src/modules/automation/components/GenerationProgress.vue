<!-- frontend/src/modules/automation/components/GenerationProgress.vue -->
<script setup lang="ts">
import { computed } from 'vue';
import type { AutomationGenerationStatus } from '../types/automation.types';
import { progressText } from '../utils/automationPolling';
import AiJobProgress from '@/shared/components/AiJobProgress.vue';

// Pembungkus tipis: tampilan indikator ada di komponen shared; di sini hanya kalimat khas automation.
const props = defineProps<{
  status: AutomationGenerationStatus;
  elapsedMs: number;
}>();

const message = computed(() => progressText(props.status, props.elapsedMs));
</script>

<template>
  <AiJobProgress
    :status="status"
    :elapsed-ms="elapsedMs"
    :message="message"
    footnote="Proses berjalan di server. Kamu boleh pindah halaman; hasilnya tersimpan di riwayat."
  />
</template>
