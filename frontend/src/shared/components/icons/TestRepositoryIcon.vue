<!-- frontend/src/shared/components/icons/TestRepositoryIcon.vue -->
<script setup lang="ts">
withDefaults(defineProps<{ size?: number }>(), { size: 20 });
</script>

<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="2"
    stroke-linecap="round"
    stroke-linejoin="round"
  >
    <path d="M21 6h-8" />
    <path d="M21 12h-8" />
    <path d="M21 18h-8" />
    <path d="M3 6v4a2 2 0 0 0 2 2h3" />
    <path d="M3 10v6a2 2 0 0 0 2 2h3" />
  </svg>
</template>
