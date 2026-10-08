<!-- frontend/src/modules/project/components/IntegrationsCard.vue -->
<script setup lang="ts">
import PlugIcon from '@/shared/components/icons/PlugIcon.vue';

// Fitur integrasi (JIRA, Slack) BELUM ada di backend, jadi kartu ini hanya
// menandai "Coming soon" -- tanpa tombol aksi yang tidak berfungsi dan tanpa
// menyiratkan status "Not connected". Begitu backend punya endpoint
// integrasi per project, ganti kartu ini dengan status sungguhan.
</script>

<template>
  <div class="rounded-2xl border border-gray-200 p-6">
    <h2 class="flex items-center gap-2 text-base font-bold text-gray-900">
      <PlugIcon :size="18" />
      Integrations
      <span class="rounded-full bg-gray-100 px-2.5 py-0.5 text-xs font-medium text-gray-500">Coming soon</span>
    </h2>

    <div class="mt-5 flex flex-wrap items-center justify-between gap-3">
      <div class="flex flex-wrap gap-3" aria-disabled="true">
        <span
          class="flex items-center gap-2 rounded-full border border-dashed border-gray-200 px-3.5 py-1.5 text-sm font-medium text-gray-400"
        >
          JIRA
        </span>
        <span
          class="flex items-center gap-2 rounded-full border border-dashed border-gray-200 px-3.5 py-1.5 text-sm font-medium text-gray-400"
        >
          Slack
        </span>
      </div>
      <p class="text-sm text-gray-400">Integrations are not available yet.</p>
    </div>
  </div>
</template>
