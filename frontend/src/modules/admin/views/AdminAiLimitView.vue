<!-- frontend/src/modules/admin/views/AdminAiLimitView.vue -->
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { adminAiLimitService } from '../services/adminAiLimit.service';
import { readAdminError } from '../utils/adminErrors';
import {
  AI_LIMIT_TIERS,
  GLOBAL_SCOPE,
  TIER_FIELD_GROUPS,
  buildUpdateRequest,
  defaultValue,
  displayValue,
  fieldsOf,
  formFromLimits,
  isFormDirty,
  isOverridden,
  limitKey,
  limitLabel,
  overriddenKeys,
  unlimitedChanges,
  validateForm,
  type AiLimitsForm,
} from '../utils/adminAiLimits';
import type { AiLimits } from '../types/admin-ai-limits.types';
import AdminConfirmDialog from '../components/AdminConfirmDialog.vue';
import CheckCircleIcon from '@/shared/components/icons/CheckCircleIcon.vue';

const limits = ref<AiLimits | null>(null);
const form = ref<AiLimitsForm>({});
const isLoading = ref(true);
const loadError = ref('');
const isBusy = ref(false);
const actionError = ref('');
const notice = ref('');
const showErrors = ref(false);
const confirm = ref<'unlimited' | 'reset-all' | null>(null);

const errors = computed(() => validateForm(form.value));
const isDirty = computed(() => (limits.value ? isFormDirty(form.value, limits.value) : false));
const overridden = computed(() => (limits.value ? overriddenKeys(limits.value) : []));
const pendingUnlimited = computed(() => (limits.value ? unlimitedChanges(form.value, limits.value) : []));

const confirmMessage = computed(() => {
  if (confirm.value === 'unlimited') {
    return (
      'Nilai 0 berarti TANPA BATAS, sehingga biaya AI tidak terkendali untuk: ' +
      pendingUnlimited.value.map(limitLabel).join('; ') +
      '. Lanjutkan?'
    );
  }
  return `Semua batas yang diubah (${overridden.value.length}) dikembalikan ke nilai bawaan konfigurasi server.`;
});

function applyServerState(next: AiLimits) {
  limits.value = next;
  form.value = formFromLimits(next);
  showErrors.value = false;
}

async function load() {
  isLoading.value = true;
  loadError.value = '';
  try {
    applyServerState(await adminAiLimitService.get());
  } catch (err) {
    loadError.value = readAdminError(err, 'Gagal memuat batas AI.').message;
  } finally {
    isLoading.value = false;
  }
}

onMounted(load);

function discard() {
  if (limits.value) applyServerState(limits.value);
  actionError.value = '';
  notice.value = '';
}

async function save(confirmUnlimited: boolean) {
  actionError.value = '';
  notice.value = '';
  showErrors.value = true;
  if (Object.keys(errors.value).length > 0) return;
  if (pendingUnlimited.value.length > 0 && !confirmUnlimited) {
    confirm.value = 'unlimited';
    return;
  }

  isBusy.value = true;
  try {
    applyServerState(await adminAiLimitService.update(buildUpdateRequest(form.value, confirmUnlimited)));
    notice.value = 'Batas AI disimpan dan langsung berlaku.';
  } catch (err) {
    actionError.value = readAdminError(err, 'Gagal menyimpan batas AI.').message;
  } finally {
    isBusy.value = false;
    confirm.value = null;
  }
}

async function reset(keys?: string[]) {
  actionError.value = '';
  notice.value = '';
  isBusy.value = true;
  try {
    applyServerState(await adminAiLimitService.reset(keys));
    notice.value = keys ? 'Dikembalikan ke nilai bawaan.' : 'Semua batas dikembalikan ke nilai bawaan.';
  } catch (err) {
    actionError.value = readAdminError(err, 'Gagal mengembalikan ke nilai bawaan.').message;
  } finally {
    isBusy.value = false;
    confirm.value = null;
  }
}

function onConfirm() {
  if (confirm.value === 'unlimited') save(true);
  else reset();
}

function errorFor(key: string): string {
  return showErrors.value ? (errors.value[key] ?? '') : '';
}

function inputClass(key: string): string {
  return errorFor(key) ? 'border-red-500' : 'border-gray-200';
}
</script>

<template>
  <div>
    <h1 class="text-2xl font-bold text-gray-900">Batas AI</h1>
    <p class="mt-1 text-gray-500">
      Atur batas generate test case dan code automation per tier tanpa deploy. Perubahan langsung berlaku (paling lambat sekitar 10 detik di server lain).
      Nama model, provider, dan API key tidak diatur di sini.
    </p>

    <div v-if="loadError && !limits" class="mt-8 rounded-2xl border border-gray-200 p-6">
      <p class="text-sm text-red-600">{{ loadError }}</p>
      <button
        type="button"
        class="mt-4 rounded-xl border border-gray-200 px-4 py-2 text-sm font-semibold text-gray-700 hover:bg-gray-50"
        @click="load"
      >
        Coba lagi
      </button>
    </div>

    <p v-else-if="isLoading || !limits" class="mt-8 text-sm text-gray-500">Memuat batas AI...</p>

    <template v-else>
      <p v-if="notice" class="mt-6 flex items-start gap-2 text-sm text-green-600">
        <CheckCircleIcon :size="18" class="mt-0.5 shrink-0" />
        {{ notice }}
      </p>
      <p v-if="actionError" class="mt-6 text-sm text-red-600">{{ actionError }}</p>

      <!-- Batas seluruh sistem -->
      <section class="mt-8 rounded-2xl border border-gray-200 p-5">
        <h2 class="text-base font-semibold text-gray-900">Seluruh sistem</h2>
        <p class="mt-1 text-sm text-gray-500">Pengaman biaya lintas fitur dan lintas tier.</p>
        <div class="mt-4 grid grid-cols-1 gap-5 sm:grid-cols-2">
          <div v-for="field in fieldsOf('global')" :key="field.id">
            <label :for="'lim-' + limitKey(GLOBAL_SCOPE, field.id)" class="block text-sm font-semibold text-gray-900">
              {{ field.label }}
            </label>
            <input
              :id="'lim-' + limitKey(GLOBAL_SCOPE, field.id)"
              v-model="form[limitKey(GLOBAL_SCOPE, field.id)]"
              type="text"
              inputmode="numeric"
              class="mt-1.5 w-full rounded-xl border px-3 py-2.5 text-sm focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-100"
              :class="inputClass(limitKey(GLOBAL_SCOPE, field.id))"
              :aria-invalid="errorFor(limitKey(GLOBAL_SCOPE, field.id)) !== ''"
            />
            <p class="mt-1 text-xs text-gray-400">
              {{ field.hint }} &middot; bawaan: {{ displayValue(field.id, defaultValue(limits, limitKey(GLOBAL_SCOPE, field.id))) }}
              <template v-if="isOverridden(limits, limitKey(GLOBAL_SCOPE, field.id))">
                &middot; <span class="font-semibold text-amber-600">diubah di database</span>
                <button
                  type="button"
                  class="ml-1 underline disabled:cursor-not-allowed disabled:opacity-50"
                  :disabled="isBusy || isDirty"
                  :title="isDirty ? 'Simpan atau batalkan perubahan dulu' : ''"
                  @click="reset([limitKey(GLOBAL_SCOPE, field.id)])"
                >
                  reset
                </button>
              </template>
            </p>
            <p v-if="errorFor(limitKey(GLOBAL_SCOPE, field.id))" class="mt-1 text-xs text-red-600">
              {{ errorFor(limitKey(GLOBAL_SCOPE, field.id)) }}
            </p>
          </div>
        </div>
      </section>

      <!-- Batas per tier: baris = parameter, kolom = tier -->
      <section v-for="g in TIER_FIELD_GROUPS" :key="g.group" class="mt-6 rounded-2xl border border-gray-200">
        <h2 class="border-b border-gray-100 px-5 py-4 text-base font-semibold text-gray-900">{{ g.title }}</h2>
        <div class="overflow-x-auto">
          <table class="min-w-full text-left text-sm">
            <thead class="bg-gray-50 text-xs font-semibold tracking-wide text-gray-500">
              <tr>
                <th scope="col" class="px-5 py-3">PARAMETER</th>
                <th v-for="tier in AI_LIMIT_TIERS" :key="tier.id" scope="col" class="px-3 py-3">{{ tier.label.toUpperCase() }}</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-100">
              <tr v-for="field in fieldsOf(g.group)" :key="field.id">
                <td class="px-5 py-3 align-top">
                  <p class="font-medium text-gray-900">{{ field.label }}</p>
                  <p class="text-xs text-gray-400">{{ field.hint }} &middot; {{ field.min }}&ndash;{{ field.max.toLocaleString('id-ID') }}</p>
                </td>
                <td v-for="tier in AI_LIMIT_TIERS" :key="tier.id" class="px-3 py-3 align-top">
                  <input
                    v-model="form[limitKey(tier.id, field.id)]"
                    type="text"
                    inputmode="numeric"
                    :aria-label="limitLabel(limitKey(tier.id, field.id))"
                    class="w-28 rounded-xl border px-3 py-2 text-sm focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-100"
                    :class="inputClass(limitKey(tier.id, field.id))"
                    :aria-invalid="errorFor(limitKey(tier.id, field.id)) !== ''"
                  />
                  <p class="mt-1 text-xs text-gray-400">
                    bawaan: {{ displayValue(field.id, defaultValue(limits, limitKey(tier.id, field.id))) }}
                  </p>
                  <p v-if="isOverridden(limits, limitKey(tier.id, field.id))" class="text-xs">
                    <span class="font-semibold text-amber-600">database</span>
                    <button
                      type="button"
                      class="ml-1 text-gray-500 underline disabled:cursor-not-allowed disabled:opacity-50"
                      :disabled="isBusy || isDirty"
                      :title="isDirty ? 'Simpan atau batalkan perubahan dulu' : ''"
                      @click="reset([limitKey(tier.id, field.id)])"
                    >
                      reset
                    </button>
                  </p>
                  <p v-if="errorFor(limitKey(tier.id, field.id))" class="mt-1 max-w-[9rem] text-xs text-red-600">
                    {{ errorFor(limitKey(tier.id, field.id)) }}
                  </p>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <div class="mt-6 flex flex-wrap items-center gap-3">
        <button
          type="button"
          :disabled="!isDirty || isBusy"
          class="rounded-xl bg-gray-900 px-5 py-2.5 text-sm font-semibold text-white hover:bg-gray-800 disabled:cursor-not-allowed disabled:bg-gray-400"
          @click="save(false)"
        >
          {{ isBusy ? 'Menyimpan...' : 'Simpan' }}
        </button>
        <button
          type="button"
          :disabled="!isDirty || isBusy"
          class="rounded-xl border border-gray-200 px-5 py-2.5 text-sm font-semibold text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
          @click="discard"
        >
          Batalkan perubahan
        </button>
        <button
          type="button"
          :disabled="overridden.length === 0 || isBusy || isDirty"
          class="ml-auto rounded-xl border border-gray-200 px-5 py-2.5 text-sm font-semibold text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
          :title="isDirty ? 'Simpan atau batalkan perubahan dulu' : ''"
          @click="confirm = 'reset-all'"
        >
          Kembalikan semua ke bawaan
        </button>
      </div>
    </template>

    <AdminConfirmDialog
      :open="confirm !== null"
      :title="confirm === 'unlimited' ? 'Aktifkan tanpa batas?' : 'Kembalikan semua ke bawaan?'"
      :message="confirmMessage"
      :confirm-label="confirm === 'unlimited' ? 'Ya, tanpa batas' : 'Kembalikan'"
      :danger="confirm === 'unlimited'"
      :loading="isBusy"
      @confirm="onConfirm"
      @cancel="confirm = null"
    />
  </div>
</template>
