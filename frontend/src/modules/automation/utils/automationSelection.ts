// frontend/src/modules/automation/utils/automationSelection.ts
// Pemilihan test case dgn batas jumlah per generate (batas = usage.maxTestCasesPerGeneration, bergantung tier).
// Pilihan disimpan sebagai daftar BERURUT (urutan klik) supaya urutan test di hasil mengikuti urutan pilihan.
// Backend tetap penjaga akhir (400 TOO_MANY_TEST_CASES).

function slots(max: number): number {
  return Math.max(0, max);
}

export function remainingSlots(selected: string[], max: number): number {
  return Math.max(0, slots(max) - selected.length);
}

export function selectionCounter(selected: string[], max: number): string {
  return `${selected.length}/${slots(max)}`;
}

/** Pilih/batalkan satu id. blocked=true kalau mau menambah tapi batas sudah penuh (daftar tidak berubah). */
export function toggleSelection(selected: string[], id: string, max: number): { selected: string[]; blocked: boolean } {
  if (selected.includes(id)) {
    return { selected: selected.filter((existing) => existing !== id), blocked: false };
  }
  if (selected.length >= slots(max)) {
    return { selected, blocked: true };
  }
  return { selected: [...selected, id], blocked: false };
}

/** Tambahkan banyak id sampai batas penuh. truncated=true kalau sebagian tidak muat. */
export function addMany(
  selected: string[],
  ids: string[],
  max: number
): { selected: string[]; added: number; truncated: boolean } {
  const result = [...selected];
  let added = 0;
  let truncated = false;
  for (const id of ids) {
    if (result.includes(id)) continue;
    if (result.length >= slots(max)) {
      truncated = true;
      break;
    }
    result.push(id);
    added++;
  }
  return { selected: result, added, truncated };
}

export function removeMany(selected: string[], ids: string[]): string[] {
  const toRemove = new Set(ids);
  return selected.filter((id) => !toRemove.has(id));
}

/** Status kotak centang "pilih semua" utk satu daftar yang sedang tampil. */
export function groupSelectionState(visibleIds: string[], selected: string[]): 'none' | 'some' | 'all' {
  if (visibleIds.length === 0) return 'none';
  const count = visibleIds.filter((id) => selected.includes(id)).length;
  if (count === 0) return 'none';
  return count === visibleIds.length ? 'all' : 'some';
}

/** Buang id yang sudah tidak ada (mis. test case diarsipkan di tab lain) dari pilihan. */
export function pruneSelection(selected: string[], existingIds: Set<string>): string[] {
  return selected.filter((id) => existingIds.has(id));
}
