<!-- frontend/src/modules/automation/views/AutomationView.vue -->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import axios from 'axios';
import { useRoute } from 'vue-router';
import { useProjectDetail } from '@/modules/project/composables/useProjectDetail';
import { automationService } from '../services/automation.service';
import type { AutomationOptions, AutomationSetup, AutomationUsage } from '../types/automation.types';
import { describeAutomationError } from '../utils/automationErrors';
import AutomationSetupTab from '../components/AutomationSetupTab.vue';
import AutomationGenerateTab from '../components/AutomationGenerateTab.vue';

// Menu Automation: generate kode automation (Playwright/Cypress/Selenium) dari test case dengan AI.
// Tab Setup = struktur project (OWNER); tab Generate = pilih test case, generate, lihat hasil & riwayat (semua member).
const route = useRoute();
const project = useProjectDetail();
const projectId = computed(() => String(route.params.projectId));
const isOwner = computed(() => project.value?.myProjectTeam === 'OWNER');

type Tab = 'generate' | 'setup';
const tab = ref<Tab>('generate');

const options = ref<AutomationOptions | null>(null);
const setup = ref<AutomationSetup | null>(null);
const usage = ref<AutomationUsage | null>(null);
const isLoading = ref(true);
const errorMessage = ref('');

async function load() {
  isLoading.value = true;
  errorMessage.value = '';
  try {
    [options.value, setup.value, usage.value] = await Promise.all([
      automationService.getOptions(projectId.value),
      automationService.getSetup(projectId.value),
      automationService.getUsage(projectId.value),
    ]);
    // Belum ada setup: mulai dari tab Setup (OWNER langsung bisa mengisi).
    tab.value = setup.value.configured ? 'generate' : 'setup';
  } catch (err) {
    errorMessage.value = axios.isAxiosError(err)
      ? describeAutomationError(err.response?.status, err.response?.data, 'Gagal memuat menu Automation.').message
      : 'Tidak dapat terhubung ke server.';
  } finally {
    isLoading.value = false;
  }
}

async function refreshUsage() {
  try {
    usage.value = await automationService.getUsage(projectId.value);
  } catch {
    // Tidak fatal: angka pemakaian akan segar lagi pada pemuatan berikutnya.
  }
}

function handleSaved(saved: AutomationSetup) {
  setup.value = saved;
}

function handleDeleted() {
  setup.value = { configured: false, framework: null, language: null, pattern: null, structureNotes: null, updatedAt: null, updatedBy: null };
}

onMounted(load);
</script>

<template>
  <div class="px-8 py-8">
    <div class="mb-6">
      <h1 class="text-2xl font-bold text-gray-900">Automation</h1>
      <p class="mt-1 text-sm text-gray-500">Generate kode automation dari test case dengan bantuan AI.</p>
    </div>

    <div v-if="isLoading" class="space-y-4" aria-busy="true">
      <div class="h-12 w-64 animate-pulse rounded-xl bg-gray-100" />
      <div class="h-40 animate-pulse rounded-2xl border border-gray-200 bg-gray-50" />
      <div class="h-64 animate-pulse rounded-2xl border border-gray-200 bg-gray-50" />
    </div>

    <div
      v-else-if="errorMessage"
      class="flex flex-col items-center justify-center gap-3 rounded-2xl border border-red-100 bg-red-50/40 px-6 py-14 text-center"
      role="alert"
    >
      <p class="text-sm font-medium text-red-700">{{ errorMessage }}</p>
      <button type="button" class="rounded-xl bg-gray-900 px-4 py-2 text-sm font-semibold text-white hover:bg-gray-800" @click="load">
        Try again
      </button>
    </div>

    <template v-else-if="options && setup && usage">
      <div class="mb-6 flex gap-1 border-b border-gray-200" role="tablist" aria-label="Menu Automation">
        <button
          type="button"
          role="tab"
          :aria-selected="tab === 'generate'"
          class="-mb-px border-b-2 px-4 py-2.5 text-sm font-semibold transition-colors"
          :class="tab === 'generate' ? 'border-gray-900 text-gray-900' : 'border-transparent text-gray-500 hover:text-gray-800'"
          @click="tab = 'generate'"
        >
          Generate
        </button>
        <button
          type="button"
          role="tab"
          :aria-selected="tab === 'setup'"
          class="-mb-px border-b-2 px-4 py-2.5 text-sm font-semibold transition-colors"
          :class="tab === 'setup' ? 'border-gray-900 text-gray-900' : 'border-transparent text-gray-500 hover:text-gray-800'"
          @click="tab = 'setup'"
        >
          Setup
        </button>
      </div>

      <AutomationSetupTab
        v-if="tab === 'setup'"
        :project-id="projectId"
        :options="options"
        :setup="setup"
        :is-owner="isOwner"
        @saved="handleSaved"
        @deleted="handleDeleted"
        @go-generate="tab = 'generate'"
      />
      <!-- v-show bukan v-if: berpindah tab tidak boleh menghentikan polling atau menghapus pilihan test case -->
      <AutomationGenerateTab
        v-show="tab === 'generate'"
        :project-id="projectId"
        :setup="setup"
        :usage="usage"
        :is-owner="isOwner"
        @usage-changed="refreshUsage"
        @go-setup="tab = 'setup'"
      />
    </template>
  </div>
</template>
