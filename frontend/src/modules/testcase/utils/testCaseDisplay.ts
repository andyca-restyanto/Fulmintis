// frontend/src/modules/testcase/utils/testCaseDisplay.ts
import type { TestCasePriority, TestCaseType, TestCaseScenarioType } from '../types/testCase.types';

export const PRIORITY_OPTIONS: { value: TestCasePriority; label: string }[] = [
  { value: 'HIGHEST', label: 'Highest' },
  { value: 'HIGH', label: 'High' },
  { value: 'MEDIUM', label: 'Medium' },
  { value: 'LOW', label: 'Low' },
];

export const TYPE_OPTIONS: { value: TestCaseType; label: string }[] = [
  { value: 'MANUAL', label: 'Manual' },
  { value: 'AUTOMATION', label: 'Automation' },
];

const PRIORITY_BADGE_CLASS: Record<TestCasePriority, string> = {
  HIGHEST: 'bg-red-700 text-white',
  HIGH: 'bg-orange-500 text-white',
  MEDIUM: 'bg-blue-500 text-white',
  LOW: 'bg-green-700 text-white',
};

export function priorityLabel(priority: TestCasePriority): string {
  return PRIORITY_OPTIONS.find((option) => option.value === priority)?.label ?? priority;
}

export function priorityBadgeClass(priority: TestCasePriority): string {
  return PRIORITY_BADGE_CLASS[priority] ?? 'bg-gray-100 text-gray-600';
}

export function typeLabel(type: TestCaseType): string {
  return type.toLowerCase();
}

export function scenarioLabel(scenarioType: TestCaseScenarioType | null): string {
  if (scenarioType === 'POSITIVE') return '✅ Positive';
  if (scenarioType === 'NEGATIVE') return '❌ Negative';
  return '-';
}

export function scenarioBadgeClass(scenarioType: TestCaseScenarioType | null): string {
  if (scenarioType === 'POSITIVE') return 'bg-emerald-50 text-emerald-700';
  if (scenarioType === 'NEGATIVE') return 'bg-red-50 text-red-600';
  return 'text-gray-400';
}

// Requirement tambahan #2 & #4 -- dipakai TestCasePanel (badge kecil kalau
// suatu saat perlu) & ArchivedTestCasesModal (header + badge tiap baris).
export function formatDateTime(value: string | null): string {
  if (!value) return '-';
  return new Date(value).toLocaleString('id-ID', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}
