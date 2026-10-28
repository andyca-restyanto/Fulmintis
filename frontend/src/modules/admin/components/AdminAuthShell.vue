<!-- frontend/src/modules/admin/components/AdminAuthShell.vue -->
<script setup lang="ts">
import BugIcon from '@/shared/components/icons/BugIcon.vue';
import { BRAND_NAME } from '@/shared/config/brand';

defineProps<{ subtitle: string }>();
</script>

<template>
  <div class="flex min-h-screen items-center justify-center bg-gray-50 px-4 py-10">
    <div class="w-full max-w-md rounded-3xl border border-gray-200 bg-white p-10 shadow-sm">
      <div class="mb-8 text-center">
        <h1 class="flex items-center justify-center gap-2 text-3xl font-bold text-gray-900">
          <BugIcon :size="28" />
          {{ BRAND_NAME }}
        </h1>
        <span class="mt-3 inline-block rounded-full bg-gray-900 px-3 py-1 text-xs font-semibold tracking-wide text-white">
          ADMIN
        </span>
        <p class="mt-3 text-base text-gray-400">{{ subtitle }}</p>
      </div>

      <slot />
    </div>
  </div>
</template>
