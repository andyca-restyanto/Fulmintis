<!-- frontend/src/shared/components/icons/CrownIcon.vue -->
<script setup lang="ts">
withDefaults(defineProps<{ size?: number }>(), { size: 14 });
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
    <path
      d="m2 4 3 12h14l3-12-6 7-4-7-4 7-6-7Z"
    />
  </svg>
</template>
