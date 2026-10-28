<!-- frontend/src/modules/project/components/ProjectCard.vue -->
<script setup lang="ts">
import { computed } from 'vue';
import type { ProjectListItem } from '../types/project.types';
import FolderIcon from '@/shared/components/icons/FolderIcon.vue';
import CrownIcon from '@/shared/components/icons/CrownIcon.vue';

const props = defineProps<{ project: ProjectListItem }>();

const formattedDate = computed(() => {
  try {
    return new Date(props.project.createdAt).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    });
  } catch {
    return '';
  }
});
</script>

<template>
  <!-- Klik card project -> masuk ke halaman project (ProjectLayoutView),
       requirement "As a user when I click project, I can see menu for
       Dashboard, Test Repository, Test Runs, Report and Setting". -->
  <RouterLink
    :to="{ name: 'project-dashboard', params: { projectId: project.id } }"
    class="block rounded-2xl border border-gray-200 p-5 transition-shadow hover:shadow-md"
  >
    <div class="mb-3 flex items-start justify-between">
      <span class="flex h-10 w-10 items-center justify-center rounded-xl bg-gray-100 text-gray-500">
        <FolderIcon :size="20" />
      </span>
      <span
        v-if="project.myProjectTeam === 'OWNER'"
        class="flex items-center gap-1 rounded-full bg-amber-50 px-2.5 py-1 text-xs font-medium text-amber-600"
      >
        <CrownIcon :size="12" />
        Owner
      </span>
      <span
        v-else
        class="rounded-full bg-blue-50 px-2.5 py-1 text-xs font-medium text-blue-600"
      >
        Collaborator
      </span>
    </div>

    <h3 class="text-base font-semibold text-gray-900">{{ project.projectName }}</h3>
    <p v-if="project.description" class="mt-1 line-clamp-2 text-sm text-gray-500">
      {{ project.description }}
    </p>
    <p class="mt-3 text-xs text-gray-400">Created {{ formattedDate }}</p>
  </RouterLink>
</template>
