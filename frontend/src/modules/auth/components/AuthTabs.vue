<!-- frontend/src/modules/auth/components/AuthTabs.vue -->
<script setup lang="ts">
defineProps<{ modelValue: 'signin' | 'signup' }>();
const emit = defineEmits<{ (e: 'update:modelValue', value: 'signin' | 'signup'): void }>();
</script>

<template>
  <div class="grid grid-cols-2 gap-1 rounded-2xl bg-gray-100 p-1.5">
    <button
      type="button"
      class="rounded-xl py-3 text-base font-medium transition-colors"
      :class="modelValue === 'signin' ? 'bg-white text-gray-900 shadow-sm font-semibold' : 'text-gray-400'"
      @click="emit('update:modelValue', 'signin')"
    >
      Sign In
    </button>
    <button
      type="button"
      class="rounded-xl py-3 text-base font-medium transition-colors"
      :class="modelValue === 'signup' ? 'bg-white text-gray-900 shadow-sm font-semibold' : 'text-gray-400'"
      @click="emit('update:modelValue', 'signup')"
    >
      Sign Up
    </button>
  </div>
</template>
