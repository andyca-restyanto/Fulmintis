// frontend/src/modules/automation/utils/automationErrors.ts
// Logika pemetaan error AI kini ada di shared (dipakai bersama fitur AI lain). Berkas ini hanya mempertahankan nama
// lama supaya komponen & test modul automation tidak perlu berubah.
import { describeAiError } from '../../../shared/utils/aiErrors';
import type { AiErrorKind, DescribedAiError } from '../../../shared/utils/aiErrors';

export { GENERIC_AI_UNAVAILABLE_MESSAGE, describeGenerationFailure } from '../../../shared/utils/aiErrors';

export type AutomationErrorKind = AiErrorKind;
export type DescribedError = DescribedAiError;
export const describeAutomationError = describeAiError;
