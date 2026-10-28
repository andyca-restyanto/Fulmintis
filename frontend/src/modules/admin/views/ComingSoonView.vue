<!-- frontend/src/modules/admin/views/ComingSoonView.vue -->
<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import SparklesIcon from '@/shared/components/icons/SparklesIcon.vue';

const route = useRoute();

// Judul diambil dari meta.title route (lihat modules/admin/routes.ts).
const title = computed(() => (typeof route.meta.title === 'string' ? route.meta.title : 'Halaman ini'));
</script>

<template>
  <div>
    <h1 class="text-2xl font-bold text-gray-900">{{ title }}</h1>
    <div
      class="mt-6 flex min-h-[420px] flex-col items-center justify-center rounded-2xl border-2 border-dashed border-gray-200"
    >
      <SparklesIcon :size="40" class="mb-4 text-gray-300" />
      <p class="text-lg font-semibold text-gray-900">Coming soon</p>
      <p class="mt-1 text-sm text-gray-400">Fitur {{ title }} sedang disiapkan.</p>
    </div>
  </div>
</template>
