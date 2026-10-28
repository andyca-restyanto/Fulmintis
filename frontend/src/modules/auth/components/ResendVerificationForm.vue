<!-- frontend/src/modules/auth/components/ResendVerificationForm.vue -->
<script setup lang="ts">
import { ref, computed, onBeforeUnmount } from 'vue';
import axios from 'axios';
import { authService } from '../services/auth.service';

const props = withDefaults(
  defineProps<{
    /** Email awal (mis. yang barusan dipakai daftar / login). Boleh diedit user. */
    initialEmail?: string;
  }>(),
  { initialEmail: '' }
);

// Cooldown sisi FE (selaras dgn cooldown 60 detik per email di backend) supaya
// user tidak menekan berulang dan kena rate limit (429).
const COOLDOWN_SECONDS = 60;

const email = ref(props.initialEmail);
const isSending = ref(false);
const message = ref('');
const errorMessage = ref('');
const cooldown = ref(0);
let timer: ReturnType<typeof setInterval> | null = null;

const canSend = computed(
  () => email.value.trim().length > 0 && !isSending.value && cooldown.value === 0
);

function startCooldown() {
  cooldown.value = COOLDOWN_SECONDS;
  timer = setInterval(() => {
    cooldown.value -= 1;
    if (cooldown.value <= 0 && timer) {
      clearInterval(timer);
      timer = null;
    }
  }, 1000);
}

async function handleResend() {
  if (!canSend.value) return;

  isSending.value = true;
  message.value = '';
  errorMessage.value = '';
  try {
    const result = await authService.resendVerification({ email: email.value.trim() });
    message.value = result.message;
    startCooldown();
  } catch (err) {
    if (axios.isAxiosError(err) && err.response?.status === 429) {
      errorMessage.value =
        (err.response.data as { message?: string })?.message ??
        'Terlalu banyak percobaan. Coba lagi sebentar.';
    } else if (axios.isAxiosError(err) && err.response?.status === 400) {
      errorMessage.value = 'Format email tidak valid.';
    } else {
      errorMessage.value = 'Gagal mengirim ulang link verifikasi, coba lagi.';
    }
  } finally {
    isSending.value = false;
  }
}

onBeforeUnmount(() => {
  if (timer) clearInterval(timer);
});
</script>

<template>
  <div class="space-y-2">
    <div class="flex flex-col gap-2 sm:flex-row">
      <input
        v-model="email"
        type="email"
        placeholder="you@example.com"
        aria-label="Email untuk kirim ulang verifikasi"
        class="min-w-0 flex-1 rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 placeholder-gray-400 focus:border-gray-500 focus:outline-none"
        @keyup.enter="handleResend"
      />
      <button
        type="button"
        :disabled="!canSend"
        class="shrink-0 rounded-lg bg-gray-900 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-gray-800 disabled:cursor-not-allowed disabled:bg-gray-400"
        @click="handleResend"
      >
        <template v-if="isSending">Mengirim...</template>
        <template v-else-if="cooldown > 0">Kirim ulang ({{ cooldown }}s)</template>
        <template v-else>Kirim ulang link verifikasi</template>
      </button>
    </div>
    <p v-if="message" class="text-sm text-green-700">{{ message }}</p>
    <p v-if="errorMessage" class="text-sm text-red-700">{{ errorMessage }}</p>
  </div>
</template>
