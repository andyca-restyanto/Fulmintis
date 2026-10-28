<!-- frontend/src/modules/admin/views/AdminListView.vue -->
<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { adminManagementService } from '../services/adminManagement.service';
import { readAdminError } from '../utils/adminErrors';
import {
  ADMIN_PAGE_SIZE,
  adminDisplayName,
  clampPage,
  formatAdminDate,
  pageIndexFromQuery,
  pageQueryFromIndex,
  resendInviteRequest,
  rowActions,
  statusLabel,
  type AdminRowAction,
  type StatusTone,
} from '../utils/adminList';
import type { AdminListItem, AdminListPage } from '../types/admin-auth.types';
import AdminPager from '../components/AdminPager.vue';
import AdminConfirmDialog from '../components/AdminConfirmDialog.vue';
import UserPlusIcon from '@/shared/components/icons/UserPlusIcon.vue';
import CheckCircleIcon from '@/shared/components/icons/CheckCircleIcon.vue';

const route = useRoute();
const router = useRouter();

// Halaman aktif disimpan di URL (?page=2, mulai dari 1) agar refresh dan tombol back tetap di halaman yang sama.
const pageIndex = computed(() => pageIndexFromQuery(route.query.page));

const data = ref<AdminListPage | null>(null);
const isLoading = ref(true);
const loadError = ref('');
const nowMs = ref(Date.now());

const notice = ref('');
const actionError = ref('');
const busyId = ref<string | null>(null);
const confirmTarget = ref<AdminListItem | null>(null);
const isConfirming = ref(false);

const TONE_CLASS: Record<StatusTone, string> = {
  success: 'bg-green-50 text-green-700',
  warning: 'bg-amber-50 text-amber-700',
  danger: 'bg-red-50 text-red-700',
  muted: 'bg-gray-100 text-gray-500',
};

const ACTION_LABEL: Record<AdminRowAction, string> = {
  deactivate: 'Nonaktifkan',
  activate: 'Aktifkan',
  resend: 'Kirim ulang undangan',
};

const confirmMessage = computed(() => {
  const target = confirmTarget.value;
  if (!target) return '';
  return `${adminDisplayName(target)} (${target.email}) tidak akan bisa login dan sesi yang sedang berjalan langsung berakhir. Anda bisa mengaktifkannya kembali kapan saja.`;
});

// Permintaan yang datang belakangan menang: respons lama yang terlambat tidak menimpa data baru.
let requestSeq = 0;

async function load() {
  const seq = ++requestSeq;
  const target = pageIndex.value;
  isLoading.value = true;
  loadError.value = '';
  try {
    const result = await adminManagementService.list(target, ADMIN_PAGE_SIZE);
    if (seq !== requestSeq) return;

    // Halaman di luar jangkauan (mis. ?page=99, atau data berkurang): pindah ke halaman terakhir yang ada.
    const clamped = clampPage(target, result.totalPages);
    if (result.items.length === 0 && result.totalPages > 0 && clamped !== target) {
      await router.replace({ name: 'admin-admins', query: pageQueryFromIndex(clamped) });
      return; // perubahan query memicu load() lagi
    }

    nowMs.value = Date.now();
    data.value = result;
  } catch (err) {
    if (seq !== requestSeq) return;
    loadError.value = readAdminError(err, 'Gagal memuat daftar admin.').message;
  } finally {
    if (seq === requestSeq) {
      isLoading.value = false;
    }
  }
}

onMounted(load);
watch(pageIndex, () => {
  notice.value = '';
  actionError.value = '';
  load();
});

function goToPage(index: number) {
  router.push({ name: 'admin-admins', query: pageQueryFromIndex(index) });
}

async function runAction(action: AdminRowAction, item: AdminListItem) {
  actionError.value = '';
  notice.value = '';

  if (action === 'deactivate') {
    confirmTarget.value = item; // dikonfirmasi dulu di dialog
    return;
  }

  busyId.value = item.id;
  try {
    if (action === 'activate') {
      await adminManagementService.activate(item.id);
      notice.value = `${adminDisplayName(item)} diaktifkan kembali.`;
    } else {
      // Kirim ulang undangan: endpoint undangan yang sama (admin menunggu undangan = token baru).
      const response = await adminManagementService.invite(resendInviteRequest(item));
      notice.value = response.message;
    }
    await load();
  } catch (err) {
    const info = readAdminError(err, 'Aksi gagal, coba lagi.');
    actionError.value = info.message;
    if (info.status === 404 || info.status === 409) {
      await load(); // datanya sudah berubah di server -> sinkronkan tampilan
    }
  } finally {
    busyId.value = null;
  }
}

async function confirmDeactivate() {
  const target = confirmTarget.value;
  if (!target) return;

  isConfirming.value = true;
  try {
    await adminManagementService.deactivate(target.id);
    notice.value = `${adminDisplayName(target)} dinonaktifkan.`;
    confirmTarget.value = null;
    await load();
  } catch (err) {
    const info = readAdminError(err, 'Gagal menonaktifkan admin.');
    actionError.value = info.message;
    confirmTarget.value = null;
    if (info.status === 404 || info.status === 409) {
      await load();
    }
  } finally {
    isConfirming.value = false;
  }
}

function cancelDeactivate() {
  confirmTarget.value = null;
}
</script>

<template>
  <div>
    <div class="flex flex-wrap items-start justify-between gap-4">
      <div>
        <h1 class="text-2xl font-bold text-gray-900">Admin</h1>
        <p class="mt-1 text-gray-500">Kelola administrator: undang admin baru, nonaktifkan, atau aktifkan kembali.</p>
      </div>
      <button
        type="button"
        class="flex items-center gap-2 rounded-xl bg-gray-900 px-4 py-2.5 text-sm font-semibold text-white hover:bg-gray-800"
        @click="router.push({ name: 'admin-invite' })"
      >
        <UserPlusIcon :size="18" />
        Undang Admin
      </button>
    </div>

    <p v-if="notice" class="mt-6 flex items-start gap-2 text-sm text-green-600">
      <CheckCircleIcon :size="18" class="mt-0.5 shrink-0" />
      {{ notice }}
    </p>
    <p v-if="actionError" class="mt-6 text-sm text-red-600">{{ actionError }}</p>

    <!-- Gagal memuat (dan belum ada data sama sekali) -->
    <div v-if="loadError && !data" class="mt-8 rounded-2xl border border-gray-200 p-6">
      <p class="text-sm text-red-600">{{ loadError }}</p>
      <button
        type="button"
        class="mt-4 rounded-xl border border-gray-200 px-4 py-2 text-sm font-semibold text-gray-700 hover:bg-gray-50"
        @click="load"
      >
        Coba lagi
      </button>
    </div>

    <!-- Pertama kali memuat -->
    <p v-else-if="!data" class="mt-8 text-sm text-gray-500">Memuat daftar admin...</p>

    <template v-else>
      <p v-if="loadError" class="mt-6 text-sm text-red-600">
        {{ loadError }}
        <button type="button" class="ml-1 font-semibold underline" @click="load">Coba lagi</button>
      </p>

      <div
        class="mt-6 overflow-x-auto rounded-2xl border border-gray-200 transition-opacity"
        :class="{ 'opacity-60': isLoading }"
        :aria-busy="isLoading"
      >
        <table class="min-w-full text-left text-sm">
          <thead class="border-b border-gray-100 bg-gray-50 text-xs font-semibold tracking-wide text-gray-500">
            <tr>
              <th scope="col" class="px-4 py-3">NAMA</th>
              <th scope="col" class="px-4 py-3">EMAIL</th>
              <th scope="col" class="px-4 py-3">STATUS</th>
              <th scope="col" class="px-4 py-3">DIBUAT</th>
              <th scope="col" class="px-4 py-3 text-right">AKSI</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-100">
            <tr v-if="data.items.length === 0">
              <td colspan="5" class="px-4 py-8 text-center text-gray-500">Belum ada admin.</td>
            </tr>
            <tr v-for="item in data.items" :key="item.id">
              <td class="px-4 py-3 font-medium text-gray-900">
                {{ adminDisplayName(item) }}
                <span v-if="item.self" class="ml-1 text-xs font-normal text-gray-400">(Anda)</span>
              </td>
              <td class="px-4 py-3 text-gray-600">{{ item.email }}</td>
              <td class="px-4 py-3">
                <span
                  class="inline-block rounded-full px-2.5 py-0.5 text-xs font-semibold"
                  :class="TONE_CLASS[statusLabel(item, nowMs).tone]"
                >
                  {{ statusLabel(item, nowMs).text }}
                </span>
              </td>
              <td class="px-4 py-3 text-gray-500">{{ formatAdminDate(item.createdAt) }}</td>
              <td class="px-4 py-3">
                <div class="flex flex-wrap justify-end gap-2">
                  <button
                    v-for="action in rowActions(item)"
                    :key="action"
                    type="button"
                    :disabled="busyId !== null || isLoading"
                    class="rounded-lg border px-3 py-1.5 text-xs font-semibold disabled:cursor-not-allowed disabled:opacity-50"
                    :class="
                      action === 'deactivate'
                        ? 'border-red-200 text-red-600 hover:bg-red-50'
                        : 'border-gray-200 text-gray-700 hover:bg-gray-50'
                    "
                    @click="runAction(action, item)"
                  >
                    {{ busyId === item.id ? 'Memproses...' : ACTION_LABEL[action] }}
                  </button>
                  <span v-if="rowActions(item).length === 0" class="text-xs text-gray-300">-</span>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <AdminPager
        :page="data.page"
        :size="data.size"
        :total-items="data.totalItems"
        :total-pages="data.totalPages"
        :disabled="isLoading || busyId !== null"
        @change="goToPage"
      />
    </template>

    <AdminConfirmDialog
      :open="confirmTarget !== null"
      title="Nonaktifkan admin?"
      :message="confirmMessage"
      confirm-label="Nonaktifkan"
      danger
      :loading="isConfirming"
      @confirm="confirmDeactivate"
      @cancel="cancelDeactivate"
    />
  </div>
</template>
