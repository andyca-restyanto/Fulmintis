<!-- frontend/src/modules/admin/components/AdminPasswordField.vue -->
<script setup lang="ts">
import { ref } from 'vue';
import PasswordChecklist from '@/modules/auth/components/PasswordChecklist.vue';
import EyeIcon from '@/shared/components/icons/EyeIcon.vue';
import EyeOffIcon from '@/shared/components/icons/EyeOffIcon.vue';

withDefaults(
  defineProps<{
    id: string;
    label: string;
    modelValue: string;
    error?: string;
    /** Tampilkan daftar aturan password (untuk field "password baru", bukan konfirmasi). */
    showChecklist?: boolean;
    placeholder?: string;
  }>(),
  { error: '', showChecklist: false, placeholder: '••••••••' }
);

const emit = defineEmits<{ (e: 'update:modelValue', value: string): void }>();

const visible = ref(false);

function onInput(event: Event) {
  emit('update:modelValue', (event.target as HTMLInputElement).value);
}
</script>

<template>
  <div>
    <label :for="id" class="mb-2 block text-base font-semibold text-gray-900">{{ label }}</label>
    <div class="relative">
      <input
        :id="id"
        :value="modelValue"
        :type="visible ? 'text' : 'password'"
        :placeholder="placeholder"
        autocomplete="new-password"
        class="w-full rounded-xl border border-gray-200 px-4 py-3.5 pr-12 text-base placeholder-gray-400 focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-100"
        :class="{ 'border-red-500': error }"
        @input="onInput"
      />
      <button
        type="button"
        class="absolute inset-y-0 right-4 flex items-center text-gray-400 hover:text-gray-600"
        tabindex="-1"
        @click="visible = !visible"
      >
        <EyeOffIcon v-if="visible" :size="20" />
        <EyeIcon v-else :size="20" />
      </button>
    </div>
    <PasswordChecklist v-if="showChecklist" :password="modelValue" />
    <p v-if="error" class="mt-1.5 text-sm text-red-600">{{ error }}</p>
  </div>
</template>
