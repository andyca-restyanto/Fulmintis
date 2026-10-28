<!-- frontend/src/modules/admin/views/AdminEmailAllowlistView.vue -->
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { adminEmailAllowlistService } from '../services/adminEmailAllowlist.service';
import { readAdminError } from '../utils/adminErrors';
import {
  RULE_TYPE_OPTIONS,
  isEnabledButEmpty,
  normalizeRuleValue,
  ruleTypeLabel,
  validateRuleValue,
} from '../utils/adminEmailAllowlist';
import type { AllowedEmailRule, AllowedEmailRuleType, AllowlistResponse } from '../types/admin-email-allowlist.types';
import AdminConfirmDialog from '../components/AdminConfirmDialog.vue';
import CheckCircleIcon from '@/shared/components/icons/CheckCircleIcon.vue';
import TrashIcon from '@/shared/components/icons/TrashIcon.vue';

const state = ref<AllowlistResponse | null>(null);
const isLoading = ref(true);
const loadError = ref('');
const isBusy = ref(false);
const actionError = ref('');
const notice = ref('');

const ruleType = ref<AllowedEmailRuleType>('DOMAIN');
const ruleValue = ref('');
const showErrors = ref(false);
const toRemove = ref<AllowedEmailRule | null>(null);

const valueError = computed(() => validateRuleValue(ruleType.value, ruleValue.value));
const placeholder = computed(() => RULE_TYPE_OPTIONS.find((o) => o.id === ruleType.value)?.placeholder ?? '');
const emptyWarning = computed(() =>
  state.value ? isEnabledButEmpty(state.value.enabled, state.value.rules.length) : false
);

async function load() {
  isLoading.value = true;
  loadError.value = '';
  try {
    state.value = await adminEmailAllowlistService.get();
  } catch (err) {
    loadError.value = readAdminError(err, 'Gagal memuat daftar email.').message;
  } finally {
    isLoading.value = false;
  }
}

onMounted(load);

async function run(action: () => Promise<void>, successNotice: string, failMessage: string) {
  actionError.value = '';
  notice.value = '';
  isBusy.value = true;
  try {
    await action();
    notice.value = successNotice;
  } catch (err) {
    actionError.value = readAdminError(err, failMessage).message;
  } finally {
    isBusy.value = false;
  }
}

async function addRule() {
  showErrors.value = true;
  if (valueError.value) return;
  await run(
    async () => {
      await adminEmailAllowlistService.add({ ruleType: ruleType.value, value: normalizeRuleValue(ruleType.value, ruleValue.value) });
      state.value = await adminEmailAllowlistService.get();
      ruleValue.value = '';
      showErrors.value = false;
    },
    'Aturan ditambahkan.',
    'Gagal menambah aturan.'
  );
}

async function toggle() {
  if (!state.value) return;
  const next = !state.value.enabled;
  await run(
    async () => {
      state.value = await adminEmailAllowlistService.setEnabled(next);
    },
    next ? 'Pembatasan email diaktifkan.' : 'Pembatasan email dimatikan. Semua email boleh mendaftar.',
    'Gagal mengubah status.'
  );
}

async function confirmRemove() {
  const rule = toRemove.value;
  if (!rule) return;
  await run(
    async () => {
      await adminEmailAllowlistService.remove(rule.id);
      state.value = await adminEmailAllowlistService.get();
    },
    'Aturan dihapus.',
    'Gagal menghapus aturan.'
  );
  toRemove.value = null;
}

function formatDate(iso: string): string {
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString('id-ID', { dateStyle: 'medium', timeStyle: 'short' });
}
</script>

<template>
  <div>
    <h1 class="text-2xl font-bold text-gray-900">Email Diizinkan</h1>
    <p class="mt-1 text-gray-500">
      Batasi email yang boleh mendaftar. Hanya berlaku saat register: akun yang sudah ada tetap bisa login, dan undangan admin tidak terpengaruh.
    </p>

    <div v-if="loadError && !state" class="mt-8 rounded-2xl border border-gray-200 p-6">
      <p class="text-sm text-red-600">{{ loadError }}</p>
      <button
        type="button"
        class="mt-4 rounded-xl border border-gray-200 px-4 py-2 text-sm font-semibold text-gray-700 hover:bg-gray-50"
        @click="load"
      >
        Coba lagi
      </button>
    </div>

    <p v-else-if="isLoading || !state" class="mt-8 text-sm text-gray-500">Memuat daftar email...</p>

    <template v-else>
      <p v-if="notice" class="mt-6 flex items-start gap-2 text-sm text-green-600">
        <CheckCircleIcon :size="18" class="mt-0.5 shrink-0" />
        {{ notice }}
      </p>
      <p v-if="actionError" class="mt-6 text-sm text-red-600">{{ actionError }}</p>

      <!-- Status -->
      <section class="mt-8 flex flex-wrap items-center justify-between gap-4 rounded-2xl border border-gray-200 p-5">
        <div>
          <h2 class="text-base font-semibold text-gray-900">
            Pembatasan:
            <span :class="state.enabled ? 'text-green-600' : 'text-gray-500'">{{ state.enabled ? 'Aktif' : 'Mati' }}</span>
          </h2>
          <p class="mt-1 text-sm text-gray-500">
            {{ state.enabled ? 'Hanya email yang cocok dengan daftar di bawah yang bisa mendaftar.' : 'Semua email boleh mendaftar.' }}
          </p>
        </div>
        <button
          type="button"
          :disabled="isBusy"
          class="rounded-xl px-5 py-2.5 text-sm font-semibold disabled:cursor-not-allowed disabled:opacity-50"
          :class="state.enabled ? 'border border-gray-200 text-gray-700 hover:bg-gray-50' : 'bg-gray-900 text-white hover:bg-gray-800'"
          @click="toggle"
        >
          {{ state.enabled ? 'Matikan' : 'Aktifkan' }}
        </button>
      </section>

      <p v-if="emptyWarning" class="mt-4 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
        Pembatasan aktif tetapi daftar masih kosong, jadi sementara semua email tetap boleh mendaftar. Tambahkan minimal satu aturan.
      </p>

      <!-- Tambah aturan -->
      <section class="mt-6 rounded-2xl border border-gray-200 p-5">
        <h2 class="text-base font-semibold text-gray-900">Tambah aturan</h2>
        <form class="mt-4 flex flex-wrap items-start gap-3" @submit.prevent="addRule">
          <select
            v-model="ruleType"
            aria-label="Jenis aturan"
            class="rounded-xl border border-gray-200 px-3 py-2.5 text-sm focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-100"
          >
            <option v-for="o in RULE_TYPE_OPTIONS" :key="o.id" :value="o.id">{{ o.label }}</option>
          </select>
          <div class="min-w-[16rem] flex-1">
            <input
              v-model="ruleValue"
              type="text"
              :placeholder="placeholder"
              aria-label="Nilai aturan"
              class="w-full rounded-xl border px-3 py-2.5 text-sm focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-100"
              :class="showErrors && valueError ? 'border-red-500' : 'border-gray-200'"
              :aria-invalid="showErrors && valueError !== ''"
            />
            <p v-if="showErrors && valueError" class="mt-1 text-xs text-red-600">{{ valueError }}</p>
            <p v-else class="mt-1 text-xs text-gray-400">
              Domain hanya cocok persis (subdomain seperti @mail.perusahaan.com harus ditambahkan sendiri). Alias "+" tidak otomatis diizinkan.
            </p>
          </div>
          <button
            type="submit"
            :disabled="isBusy"
            class="rounded-xl bg-gray-900 px-5 py-2.5 text-sm font-semibold text-white hover:bg-gray-800 disabled:cursor-not-allowed disabled:bg-gray-400"
          >
            {{ isBusy ? 'Memproses...' : 'Tambah' }}
          </button>
        </form>
      </section>

      <!-- Daftar -->
      <section class="mt-6 rounded-2xl border border-gray-200">
        <h2 class="border-b border-gray-100 px-5 py-4 text-base font-semibold text-gray-900">
          Daftar aturan ({{ state.rules.length }})
        </h2>
        <p v-if="state.rules.length === 0" class="px-5 py-6 text-sm text-gray-500">Belum ada aturan.</p>
        <div v-else class="overflow-x-auto">
          <table class="min-w-full text-left text-sm">
            <thead class="bg-gray-50 text-xs font-semibold tracking-wide text-gray-500">
              <tr>
                <th scope="col" class="px-5 py-3">JENIS</th>
                <th scope="col" class="px-3 py-3">NILAI</th>
                <th scope="col" class="px-3 py-3">DITAMBAHKAN</th>
                <th scope="col" class="px-3 py-3"><span class="sr-only">Aksi</span></th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-100">
              <tr v-for="rule in state.rules" :key="rule.id">
                <td class="px-5 py-3 text-gray-700">{{ ruleTypeLabel(rule.ruleType) }}</td>
                <td class="px-3 py-3 font-medium text-gray-900">{{ rule.value }}</td>
                <td class="px-3 py-3 text-gray-500">{{ formatDate(rule.createdAt) }} &middot; {{ rule.createdBy }}</td>
                <td class="px-3 py-3 text-right">
                  <button
                    type="button"
                    :disabled="isBusy"
                    :aria-label="'Hapus ' + rule.value"
                    class="rounded-lg p-2 text-gray-500 hover:bg-gray-100 hover:text-red-600 disabled:cursor-not-allowed disabled:opacity-50"
                    @click="toRemove = rule"
                  >
                    <TrashIcon :size="16" />
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>

    <AdminConfirmDialog
      :open="toRemove !== null"
      title="Hapus aturan?"
      :message="toRemove ? `Aturan ${toRemove.value} dihapus. Email yang hanya cocok dengan aturan ini tidak bisa mendaftar lagi (bila pembatasan aktif).` : ''"
      confirm-label="Hapus"
      danger
      :loading="isBusy"
      @confirm="confirmRemove"
      @cancel="toRemove = null"
    />
  </div>
</template>
