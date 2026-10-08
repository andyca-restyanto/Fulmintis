<!-- frontend/src/modules/project/components/settings/MembersSettingsTab.vue -->
<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue';
import axios from 'axios';
import { useRouter } from 'vue-router';
import { authService } from '@/modules/auth';
import { useProjectDetail } from '../../composables/useProjectDetail';
import { projectCollaboratorService } from '../../services/projectCollaborator.service';
import { projectService } from '../../services/project.service';
import type {
  ProjectCollaboratorResponse,
  ProjectTeamRole,
  UserSearchResult,
} from '../../types/collaborator.types';
import UserPlusIcon from '@/shared/components/icons/UserPlusIcon.vue';
import UsersIcon from '@/shared/components/icons/UsersIcon.vue';
import CrownIcon from '@/shared/components/icons/CrownIcon.vue';
import ChevronDownIcon from '@/shared/components/icons/ChevronDownIcon.vue';
import TrashIcon from '@/shared/components/icons/TrashIcon.vue';

// Detail project (id, myProjectTeam) sudah di-fetch ProjectLayoutView &
// di-share lewat provide/inject -- lihat composables/useProjectDetail.ts.
const router = useRouter();
const project = useProjectDetail();
const projectId = computed(() => project.value?.id ?? '');
// Requirement #3: HANYA owner project yang boleh nambah/cari team member --
// "Invite Member" card di bawah cuma tampil kalau ini true.
const isOwner = computed(() => project.value?.myProjectTeam === 'OWNER');

const myEmail = ref('');

// ---- Invite form ----
const emailInput = ref('');
const role = ref<ProjectTeamRole>('COLLABORATOR');
const isRoleDropdownOpen = ref(false);
const ROLE_OPTIONS: { value: ProjectTeamRole; label: string }[] = [
  { value: 'COLLABORATOR', label: 'Collaborator' },
  { value: 'OWNER', label: 'Owner' },
];
const roleLabel = computed(
  () => ROLE_OPTIONS.find((option) => option.value === role.value)?.label ?? role.value
);

function selectRole(value: ProjectTeamRole) {
  role.value = value;
  isRoleDropdownOpen.value = false;
}

// ---- Cari user by EMAIL PERSIS ----
// Backend hanya mencocokkan email utuh & user verified (maks 1 hasil), jadi
// pencarian baru dikirim kalau input sudah berbentuk email lengkap -- tidak
// ada lagi type-ahead per huruf/nama (dulu bisa dipakai memanen daftar user).
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const suggestions = ref<UserSearchResult[]>([]);
const showSuggestions = ref(false);
// true = sudah dicari untuk email yang sedang diketik & tidak ada hasil
// (belum terdaftar / belum verified / sudah jadi member).
const searchedWithoutResult = ref(false);
let searchDebounceTimer: ReturnType<typeof setTimeout> | undefined;

watch(emailInput, (value) => {
  if (searchDebounceTimer) clearTimeout(searchDebounceTimer);
  showSuggestions.value = false;
  searchedWithoutResult.value = false;
  suggestions.value = [];

  const email = value.trim();
  if (!isOwner.value || !projectId.value || !EMAIL_PATTERN.test(email)) return;

  searchDebounceTimer = setTimeout(async () => {
    try {
      suggestions.value = await projectCollaboratorService.searchUsersToAdd(projectId.value, email);
      showSuggestions.value = suggestions.value.length > 0;
      searchedWithoutResult.value = suggestions.value.length === 0;
    } catch {
      // termasuk 429 (rate limit) -- cukup tidak menampilkan hasil; tombol
      // Invite tetap bisa dipakai & backend memvalidasi ulang.
      suggestions.value = [];
    }
  }, 500);
});

function pickSuggestion(user: UserSearchResult) {
  emailInput.value = user.email;
  showSuggestions.value = false;
}

// Delay sedikit sebelum nutup dropdown saat blur, supaya klik di salah satu
// item suggestion (yang juga men-trigger blur di input) sempat kedaftar
// duluan oleh @mousedown.prevent di tombol suggestion-nya.
function handleEmailBlur() {
  setTimeout(() => {
    showSuggestions.value = false;
  }, 150);
}

// ---- Project members list ----
const members = ref<ProjectCollaboratorResponse[]>([]);
const isLoadingMembers = ref(true);

async function loadMembers() {
  if (!projectId.value) return;
  isLoadingMembers.value = true;
  try {
    members.value = await projectCollaboratorService.listCollaborators(projectId.value);
  } catch {
    // biarkan list kosong -- tidak fatal, tabel cuma nampilin "belum ada data"
  } finally {
    isLoadingMembers.value = false;
  }
}

onMounted(async () => {
  await loadMembers();
  try {
    const profile = await authService.getProfile();
    myEmail.value = profile.email;
  } catch {
    // "(you)" tag di tabel cuma tidak muncul, tidak fatal
  }
});
watch(projectId, loadMembers);

// Detail project (memberCount, myProjectTeam, availableMenus) di-share dari
// ProjectLayoutView -- muat ulang setelah ada perubahan member supaya
// angka & hak akses menu ikut segar (mis. OWNER menurunkan dirinya sendiri
// -> menu Settings hilang & guard di layout melempar ke dashboard).
async function refreshProjectDetail() {
  if (!projectId.value) return;
  try {
    project.value = await projectService.getProjectDetail(projectId.value);
  } catch {
    // tidak fatal
  }
}

// ---- Ubah role & hapus member (OWNER-only) ----
const ownerCount = computed(() => members.value.filter((m) => m.projectTeam === 'OWNER').length);
const busyMemberId = ref<number | null>(null);
const memberActionError = ref('');
const memberActionSuccess = ref('');
const memberPendingRemoval = ref<ProjectCollaboratorResponse | null>(null);

function isLastOwner(member: ProjectCollaboratorResponse): boolean {
  return member.projectTeam === 'OWNER' && ownerCount.value <= 1;
}

function actionErrorMessage(err: unknown, fallback: string): string {
  if (axios.isAxiosError(err)) {
    const data = err.response?.data as { message?: string } | undefined;
    return data?.message ?? fallback; // mencakup 409 "minimal 1 OWNER" & 429
  }
  return 'Tidak dapat terhubung ke server.';
}

async function handleRoleChange(member: ProjectCollaboratorResponse, event: Event) {
  const select = event.target as HTMLSelectElement;
  const newRole = select.value as ProjectTeamRole;
  if (newRole === member.projectTeam || !projectId.value) return;

  memberActionError.value = '';
  memberActionSuccess.value = '';
  busyMemberId.value = member.id;
  try {
    const updated = await projectCollaboratorService.updateCollaboratorRole(projectId.value, member.id, {
      projectTeam: newRole,
    });
    const index = members.value.findIndex((m) => m.id === member.id);
    if (index !== -1) members.value[index] = updated;
    memberActionSuccess.value = `Role ${updated.name ?? updated.email} diubah menjadi ${
      updated.projectTeam === 'OWNER' ? 'Owner' : 'Collaborator'
    }.`;
    await refreshProjectDetail();
  } catch (err) {
    select.value = member.projectTeam; // kembalikan tampilan dropdown ke role semula
    memberActionError.value = actionErrorMessage(err, 'Gagal mengubah role.');
  } finally {
    busyMemberId.value = null;
  }
}

function askRemoveMember(member: ProjectCollaboratorResponse) {
  memberActionError.value = '';
  memberActionSuccess.value = '';
  memberPendingRemoval.value = member;
}

async function confirmRemoveMember() {
  const member = memberPendingRemoval.value;
  if (!member || !projectId.value) return;

  busyMemberId.value = member.id;
  try {
    await projectCollaboratorService.removeCollaborator(projectId.value, member.id);
    memberPendingRemoval.value = null;

    if (member.email === myEmail.value) {
      // Keluar dari project sendiri -> tidak punya akses lagi ke halaman ini.
      router.replace({ name: 'dashboard' });
      return;
    }

    members.value = members.value.filter((m) => m.id !== member.id);
    memberActionSuccess.value = `${member.name ?? member.email} dihapus dari project.`;
    await refreshProjectDetail();
  } catch (err) {
    memberPendingRemoval.value = null;
    memberActionError.value = actionErrorMessage(err, 'Gagal menghapus member.');
  } finally {
    busyMemberId.value = null;
  }
}

// ---- Invite submit ----
const isInviting = ref(false);
const inviteError = ref('');
const inviteSuccess = ref('');

async function handleInvite() {
  if (!emailInput.value.trim() || !projectId.value) return;

  inviteError.value = '';
  inviteSuccess.value = '';
  isInviting.value = true;
  try {
    const added = await projectCollaboratorService.addCollaborator(projectId.value, {
      email: emailInput.value.trim(),
      projectTeam: role.value,
    });
    members.value.push(added);
    inviteSuccess.value = `${added.name ?? added.email} berhasil ditambahkan ke project.`;
    emailInput.value = '';
    role.value = 'COLLABORATOR';
    await refreshProjectDetail();
  } catch (err) {
    if (axios.isAxiosError(err)) {
      const data = err.response?.data as { message?: string };
      inviteError.value = data?.message ?? 'Gagal menambahkan member.';
    } else {
      inviteError.value = 'Tidak dapat terhubung ke server.';
    }
  } finally {
    isInviting.value = false;
  }
}
</script>

<template>
  <div class="space-y-6">
    <!-- Invite Member -- OWNER-only (requirement #3) -->
    <div v-if="isOwner" class="rounded-2xl border border-gray-200 p-8">
      <h2 class="flex items-center gap-2 text-lg font-bold text-gray-900">
        <UserPlusIcon :size="18" />
        Invite Member
      </h2>
      <p class="mt-1 text-sm text-gray-400">
        Add a registered, verified user to this project using their full email address.
      </p>

      <form class="mt-6 flex flex-col gap-4 sm:flex-row sm:items-end" @submit.prevent="handleInvite">
        <div class="relative flex-1">
          <label for="invite-email" class="mb-2 block text-sm font-semibold text-gray-900">Email</label>
          <input
            id="invite-email"
            v-model="emailInput"
            type="text"
            placeholder="user@example.com"
            autocomplete="off"
            inputmode="email"
            class="w-full rounded-xl border border-gray-200 px-4 py-3 text-sm placeholder-gray-400 focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-100"
            @focus="showSuggestions = suggestions.length > 0"
            @blur="handleEmailBlur"
          />

          <!-- Hasil pencarian email persis (maks 1 user verified yang belum jadi member) -->
          <div
            v-if="showSuggestions"
            class="absolute left-0 right-0 z-10 mt-1.5 max-h-56 overflow-y-auto rounded-xl border border-gray-100 bg-white py-1.5 shadow-lg"
          >
            <button
              v-for="user in suggestions"
              :key="user.id"
              type="button"
              class="flex w-full flex-col items-start px-4 py-2 text-left hover:bg-gray-50"
              @mousedown.prevent="pickSuggestion(user)"
            >
              <span class="text-sm font-medium text-gray-900">{{ user.name || user.email }}</span>
              <span v-if="user.name" class="text-xs text-gray-400">{{ user.email }}</span>
            </button>
          </div>
          <p v-if="searchedWithoutResult" class="mt-1.5 text-xs text-gray-400">
            Tidak ada user terverifikasi dengan email ini yang bisa ditambahkan (belum terdaftar, belum
            verifikasi, atau sudah jadi member).
          </p>
        </div>

        <div class="relative">
          <span class="mb-2 block text-sm font-semibold text-gray-900">Role</span>
          <button
            type="button"
            class="flex w-40 items-center justify-between gap-2 rounded-xl border border-gray-200 px-4 py-3 text-sm text-gray-900 focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-100"
            @click="isRoleDropdownOpen = !isRoleDropdownOpen"
          >
            {{ roleLabel }}
            <ChevronDownIcon :size="14" class="text-gray-400" />
          </button>

          <div
            v-if="isRoleDropdownOpen"
            class="absolute left-0 right-0 z-10 mt-1.5 rounded-xl border border-gray-100 bg-white py-1.5 shadow-lg"
          >
            <button
              v-for="option in ROLE_OPTIONS"
              :key="option.value"
              type="button"
              class="flex w-full items-center gap-2 px-4 py-2 text-left text-sm text-gray-700 hover:bg-gray-50"
              @click="selectRole(option.value)"
            >
              <span class="w-4">
                <span v-if="option.value === role">&#10003;</span>
              </span>
              {{ option.label }}
            </button>
          </div>
        </div>

        <button
          type="submit"
          :disabled="!emailInput.trim() || isInviting"
          class="rounded-xl bg-gray-900 px-6 py-3 text-sm font-semibold text-white transition-colors hover:bg-gray-800 disabled:cursor-not-allowed disabled:bg-gray-300"
        >
          {{ isInviting ? 'Inviting...' : 'Invite' }}
        </button>
      </form>

      <p v-if="inviteError" class="mt-3 text-sm text-red-600">{{ inviteError }}</p>
      <p v-if="inviteSuccess" class="mt-3 text-sm text-emerald-600">{{ inviteSuccess }}</p>
    </div>

    <!-- Project Members -->
    <div class="rounded-2xl border border-gray-200 p-8">
      <h2 class="flex items-center gap-2 text-lg font-bold text-gray-900">
        <UsersIcon :size="18" />
        Project Members
      </h2>
      <p class="mt-1 text-sm text-gray-400">
        {{ members.length }} member{{ members.length === 1 ? '' : 's' }}
      </p>

      <p v-if="memberActionError" class="mt-4 text-sm text-red-600">{{ memberActionError }}</p>
      <p v-if="memberActionSuccess" class="mt-4 text-sm text-emerald-600">{{ memberActionSuccess }}</p>

      <p v-if="isLoadingMembers" class="mt-6 text-sm text-gray-400">Memuat...</p>
      <table v-else class="mt-6 w-full text-left text-sm">
        <thead>
          <tr class="border-b border-gray-100 text-xs font-medium uppercase tracking-wide text-gray-400">
            <th class="pb-3 font-medium">Name</th>
            <th class="pb-3 font-medium">Email</th>
            <th class="pb-3 font-medium">Role</th>
            <th class="pb-3 font-medium">Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="member in members" :key="member.id" class="border-b border-gray-50 last:border-0">
            <td class="py-3">
              <span class="font-medium text-gray-900">{{ member.name || member.email }}</span>
              <span v-if="member.email === myEmail" class="ml-1 text-gray-400">(you)</span>
            </td>
            <td class="py-3 text-gray-500">{{ member.email }}</td>
            <td class="py-3">
              <span
                v-if="member.projectTeam === 'OWNER'"
                class="inline-flex items-center gap-1 rounded-full bg-amber-50 px-2.5 py-1 text-xs font-medium text-amber-600"
              >
                <CrownIcon :size="12" />
                Owner
              </span>
              <span v-else class="rounded-full bg-blue-50 px-2.5 py-1 text-xs font-medium text-blue-600">
                Collaborator
              </span>
            </td>
            <!-- Ubah role & hapus: OWNER-only. OWNER terakhir tidak bisa diturunkan/dihapus. -->
            <td class="py-3">
              <div v-if="isOwner" class="flex items-center gap-2">
                <select
                  :value="member.projectTeam"
                  :disabled="busyMemberId === member.id || isLastOwner(member)"
                  :title="isLastOwner(member) ? 'Project harus punya minimal 1 Owner' : 'Ubah role'"
                  class="rounded-lg border border-gray-200 bg-white px-2 py-1.5 text-xs text-gray-700 focus:border-gray-400 focus:outline-none disabled:cursor-not-allowed disabled:bg-gray-50 disabled:text-gray-400"
                  :aria-label="`Role ${member.email}`"
                  @change="handleRoleChange(member, $event)"
                >
                  <option v-for="option in ROLE_OPTIONS" :key="option.value" :value="option.value">
                    {{ option.label }}
                  </option>
                </select>
                <button
                  type="button"
                  :disabled="busyMemberId === member.id || isLastOwner(member)"
                  :title="isLastOwner(member) ? 'Project harus punya minimal 1 Owner' : 'Hapus dari project'"
                  class="rounded-lg p-1.5 text-gray-400 transition-colors hover:bg-red-50 hover:text-red-600 disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-transparent disabled:hover:text-gray-400"
                  :aria-label="`Hapus ${member.email}`"
                  @click="askRemoveMember(member)"
                >
                  <TrashIcon :size="16" />
                </button>
              </div>
              <span v-else class="text-gray-300">&mdash;</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Konfirmasi hapus member -->
    <div
      v-if="memberPendingRemoval"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4"
      @click.self="memberPendingRemoval = null"
    >
      <div class="w-full max-w-sm rounded-2xl bg-white p-6 shadow-xl" role="dialog" aria-modal="true">
        <h3 class="text-lg font-bold text-gray-900">
          {{ memberPendingRemoval.email === myEmail ? 'Keluar dari project?' : 'Hapus member?' }}
        </h3>
        <p class="mt-2 text-sm text-gray-500">
          <template v-if="memberPendingRemoval.email === myEmail">
            Kamu tidak akan bisa mengakses project ini lagi kecuali diundang ulang oleh Owner.
          </template>
          <template v-else>
            <span class="font-medium text-gray-900">{{ memberPendingRemoval.name || memberPendingRemoval.email }}</span>
            tidak akan bisa mengakses project ini lagi.
          </template>
        </p>
        <div class="mt-6 flex justify-end gap-3">
          <button
            type="button"
            class="rounded-xl px-4 py-2.5 text-sm font-medium text-gray-600 hover:bg-gray-100"
            @click="memberPendingRemoval = null"
          >
            Batal
          </button>
          <button
            type="button"
            :disabled="busyMemberId === memberPendingRemoval.id"
            class="rounded-xl bg-red-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-red-700 disabled:cursor-not-allowed disabled:bg-red-300"
            @click="confirmRemoveMember"
          >
            {{ busyMemberId === memberPendingRemoval.id ? 'Menghapus...' : 'Hapus' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
