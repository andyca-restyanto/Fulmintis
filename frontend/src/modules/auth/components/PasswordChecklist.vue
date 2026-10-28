<!-- frontend/src/modules/auth/components/PasswordChecklist.vue -->
<script setup lang="ts">
import { toRef } from 'vue';
import { usePasswordValidation } from '../composables/usePasswordValidation';
import CheckIcon from '@/shared/components/icons/CheckIcon.vue';
import XIcon from '@/shared/components/icons/XIcon.vue';

const props = defineProps<{ password: string }>();
const passwordRef = toRef(props, 'password');

const { ruleResults } = usePasswordValidation(passwordRef);
</script>

<template>
  <ul class="mt-3 space-y-1.5">
    <li
      v-for="rule in ruleResults"
      :key="rule.key"
      class="flex items-center gap-2 text-sm transition-colors"
      :class="rule.passed ? 'text-green-600' : 'text-gray-400'"
    >
      <CheckIcon v-if="rule.passed" :size="16" />
      <XIcon v-else :size="16" />
      <span>{{ rule.label }}</span>
    </li>
  </ul>
</template>
