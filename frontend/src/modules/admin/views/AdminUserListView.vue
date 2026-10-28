<!-- frontend/src/modules/admin/views/AdminUserListView.vue -->
<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { adminUserService } from '../services/adminUser.service';
import { readAdminError } from '../utils/adminErrors';
import {
  ADMIN_PAGE_SIZE,
  adminDisplayName,
  clampPage,
  formatAdminDate,
  pageIndexFromQuery,
  type StatusTone,
} from '../utils/adminList';
import {
  MAX_QUERY_LENGTH,
  SEARCH_DEBOUNCE_MS,
  USER_TIERS,
  filtersFromQuery,
  hasActiveFilters,
  isTierChange,
  isUserTier,
  normalizeQuery,
  queryFromFilters,
  tierChangeMessage,
  tierChangedNotice,
  userStatus,
} from '../utils/adminUser';
import type { UserListFilters, UserListItem, UserListPage, UserTier } from '../types/admin-user.types';
import AdminPager from '../components/AdminPager.vue';
import AdminConfirmDialog from '../components/AdminConfirmDialog.vue';
import SearchIcon from '@/shared/components/icons/SearchIcon.vue';
import XIcon from '@/shared/components/icons/XIcon.vue';
import CheckCircleIcon from '@/shared/components/icons/CheckCircleIcon.vue';

const route = useRoute();
const router = useRouter();

// Filter dan halaman disimpan di URL (?q=budi&tier=FREE&page=2, halaman mulai dari 1) agar refresh
// dan tombol Back tetap menampilkan hasil yang sama.
const pageIndex = computed(() => pageIndexFromQuery(route.query.page));
const filters = computed<UserListFilters>(() => filtersFromQuery(route.query));
const filterActive = computed(() => hasActiveFilters(filters.value));

// Isi kotak cari dipisahkan dari filter yang sudah diterapkan: tabel tidak berkedip tiap ketikan.
const searchInput = ref(filters.value.q);
let debounceTimer: ReturnType<typeof setTimeout> | null = null;

const data = ref<UserListPage | null>(null);
const isLoading = ref(true);
const loadError = ref('');
const notice = ref('');
const actionError = ref('');

const pending = ref<{ item: UserListItem; next: UserTier } | null>(null);
const isSaving = ref(false);

const TONE_CLASS: Record<StatusTone, string> = {
  success: 'bg-green-50 text-green-700',
  warning: 'bg-amber-50 text-amber-700',
  danger: 'bg-red-50 text-red-700',
  muted: 'bg-gray-100 text-gray-500',
};

const confirmMessage = computed(() =>
  pending.value ? tierChangeMessage(pending.value.item, pending.value.next) : ''
);

// Permintaan yang datang belakangan menang: respons lama yang terlambat tidak menimpa hasil baru.
let requestSeq = 0;

async function load() {
  const seq = ++requestSeq;
  const target = pageIndex.value;
  isLoading.value = true;
  loadError.value = '';
  try {
    const result = await adminUserService.list(target, ADMIN_PAGE_SIZE, filters.value);
    if (seq !== requestSeq) return;

    // Halaman di luar jangkauan (mis. ?page=99, atau hasil menyusut): pindah ke halaman terakhir yang ada.
    const clamped = clampPage(target, result.totalPages);
    if (result.items.length === 0 && result.totalPages > 0 && clamped !== target) {
      await router.replace({ name: 'admin-users', query: queryFromFilters(filters.value, clamped) });
      return; // perubahan query memicu load() lagi
    }

    data.value = result;
  } catch (err) {
    if (seq !== requestSeq) return;
    loadError.value = readAdminError(err, 'Gagal memuat daftar user.').message;
  } finally {
    if (seq === requestSeq) {
      isLoading.value = false;
    }
  }
}

onMounted(load);

// Satu-satunya pemicu muat ulang karena navigasi: perubahan halaman atau filter di URL.
watch(
  [pageIndex, () => filters.value.q, () => filters.value.tier],
  () => {
    notice.value = '';
    actionError.value = '';
    load();
  }
);

// Back/forward mengubah filter di URL -> samakan kotak cari (kecuali sudah sama dengan yang diketik).
watch(
  () => filters.value.q,
  (applied: string) => {
    if (normalizeQuery(searchInput.value) !== applied) {
      searchInput.value = applied;
    }
  }
);

onBeforeUnmount(() => {
  if (debounceTimer) clearTimeout(debounceTimer);
});

// ---------------------------------------------------------------- cari & filter

function applyFilters(next: UserListFilters, mode: 'push' | 'replace') {
  const query = queryFromFilters(next, 0); // filter berubah -> kembali ke halaman 1
  const unchanged =
    next.q === filters.value.q && next.tier === filters.value.tier && pageIndex.value === 0;
  if (unchanged) return;
  router[mode]({ name: 'admin-users', query });
}

function applySearchNow() {
  if (debounceTimer) {
    clearTimeout(debounceTimer);
    debounceTimer = null;
  }
  applyFilters({ q: normalizeQuery(searchInput.value), tier: filters.value.tier }, 'replace');
}

function onSearchInput() {
  if (debounceTimer) clearTimeout(debounceTimer);
  debounceTimer = setTimeout(applySearchNow, SEARCH_DEBOUNCE_MS);
}

function clearSearch() {
  searchInput.value = '';
  applySearchNow();
}

function onTierFilterChange(event: Event) {
  const value = (event.target as HTMLSelectElement).value;
  applyFilters({ q: filters.value.q, tier: isUserTier(value) ? value : '' }, 'push');
}

function clearFilters() {
  searchInput.value = '';
  if (debounceTimer) {
    clearTimeout(debounceTimer);
    debounceTimer = null;
  }
  applyFilters({ q: '', tier: '' }, 'push');
}

function goToPage(index: number) {
  router.push({ name: 'admin-users', query: queryFromFilters(filters.value, index) });
}

// ---------------------------------------------------------------- ubah tier

function onRowTierChange(item: UserListItem, event: Event) {
  const select = event.target as HTMLSelectElement;
  const next = select.value;
  select.value = item.userType; // pilihan belum tersimpan: kembalikan sampai dikonfirmasi
  actionError.value = '';
  notice.value = '';
  if (isTierChange(item, next)) {
    pending.value = { item, next };
  }
}

async function confirmTierChange() {
  const change = pending.value;
  if (!change) return;

  isSaving.value = true;
  try {
    await adminUserService.updateTier(change.item.id, change.next);
    notice.value = tierChangedNotice(change.item, change.next, filters.value.tier);
    pending.value = null;
    await load();
  } catch (err) {
    const info = readAdminError(err, 'Gagal mengubah tier.');
    actionError.value = info.message;
    pending.value = null;
    if (info.status === 404) {
      await load(); // user sudah tidak ada / bukan user lagi -> sinkronkan tampilan
    }
  } finally {
    isSaving.value = false;
  }
}

function cancelTierChange() {
  pending.value = null;
}
</script>

<template>
  <div>
    <h1 class="text-2xl font-bold text-gray-900">User</h1>
    <p class="mt-1 text-gray-500">Daftar user terdaftar. Cari berdasarkan nama atau email, dan ubah tier langganan.</p>

    <div class="mt-6 flex flex-wrap items-center gap-3">
      <div class="relative min-w-[240px] flex-1 sm:max-w-sm">
        <SearchIcon :size="16" class="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-gray-400" />
        <input
          v-model="searchInput"
          type="search"
          :maxlength="MAX_QUERY_LENGTH"
          placeholder="Cari nama atau email"
          aria-label="Cari nama atau email"
          class="w-full rounded-xl border border-gray-200 py-2.5 pl-10 pr-10 text-sm placeholder-gray-400 focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-100 [&::-webkit-search-cancel-button]:hidden"
          @input="onSearchInput"
          @keydown.enter.prevent="applySearchNow"
        />
        <button
          v-if="searchInput"
          type="button"
          aria-label="Hapus pencarian"
          class="absolute right-2.5 top-1/2 -translate-y-1/2 rounded-md p-1 text-gray-400 hover:text-gray-700"
          @click="clearSearch"
        >
          <XIcon :size="14" />
        </button>
      </div>

      <select
        :value="filters.tier"
        aria-label="Filter tier"
        class="rounded-xl border border-gray-200 bg-white px-3.5 py-2.5 text-sm text-gray-700 focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-100"
        @change="onTierFilterChange"
      >
        <option value="">Semua tier</option>
        <option v-for="tier in USER_TIERS" :key="tier.value" :value="tier.value">{{ tier.label }}</option>
      </select>

      <button
        v-if="filterActive"
        type="button"
        class="text-sm font-semibold text-gray-600 underline-offset-2 hover:text-gray-900 hover:underline"
        @click="clearFilters"
      >
        Hapus filter
      </button>
    </div>

    <p v-if="notice" class="mt-6 flex items-start gap-2 text-sm text-green-600">
      <CheckCircleIcon :size="18" class="mt-0.5 shrink-0" />
      {{ notice }}
    </p>
    <p v-if="actionError" class="mt-6 text-sm text-red-600">{{ actionError }}</p>

    <!-- Gagal memuat dan belum ada data sama sekali -->
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
    <p v-else-if="!data" class="mt-8 text-sm text-gray-500">Memuat daftar user...</p>

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
              <th scope="col" class="px-4 py-3">TIER</th>
              <th scope="col" class="px-4 py-3">STATUS</th>
              <th scope="col" class="px-4 py-3">DIBUAT</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-100">
            <tr v-if="data.items.length === 0">
              <td colspan="5" class="px-4 py-8 text-center text-gray-500">
                <template v-if="filterActive">
                  Tidak ada user yang cocok.
                  <button type="button" class="ml-1 font-semibold text-gray-900 underline" @click="clearFilters">
                    Hapus filter
                  </button>
                </template>
                <template v-else>Belum ada user.</template>
              </td>
            </tr>
            <tr v-for="item in data.items" :key="item.id">
              <td class="px-4 py-3 font-medium text-gray-900">{{ adminDisplayName(item) }}</td>
              <td class="px-4 py-3 text-gray-600">{{ item.email }}</td>
              <td class="px-4 py-3">
                <select
                  :value="item.userType"
                  :aria-label="`Tier untuk ${item.email}`"
                  :disabled="isSaving || pending !== null"
                  class="rounded-lg border border-gray-200 bg-white px-2.5 py-1.5 text-xs font-semibold text-gray-700 focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-100 disabled:cursor-not-allowed disabled:opacity-50"
                  @change="onRowTierChange(item, $event)"
                >
                  <option v-for="tier in USER_TIERS" :key="tier.value" :value="tier.value">{{ tier.label }}</option>
                </select>
              </td>
              <td class="px-4 py-3">
                <span
                  class="inline-block rounded-full px-2.5 py-0.5 text-xs font-semibold"
                  :class="TONE_CLASS[userStatus(item).tone]"
                >
                  {{ userStatus(item).text }}
                </span>
              </td>
              <td class="px-4 py-3 text-gray-500">{{ formatAdminDate(item.createdAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <AdminPager
        :page="data.page"
        :size="data.size"
        :total-items="data.totalItems"
        :total-pages="data.totalPages"
        :disabled="isLoading || isSaving"
        @change="goToPage"
      />
    </template>

    <AdminConfirmDialog
      :open="pending !== null"
      title="Ubah tier user?"
      :message="confirmMessage"
      confirm-label="Ubah tier"
      :loading="isSaving"
      @confirm="confirmTierChange"
      @cancel="cancelTierChange"
    />
  </div>
</template>
