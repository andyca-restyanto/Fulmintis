// frontend/src/modules/testrun/utils/evidenceTypes.ts

// Tipe evidence yang DITERIMA backend (EvidenceFileType: png, jpg, gif, webp,
// mp4, mov, webm). Backend memvalidasi dari ISI file; daftar ini hanya untuk
// UX di FE (menolak lebih awal & memilih cara menampilkan) -- bukan
// pengganti validasi backend.
export const EVIDENCE_IMAGE_TYPES = ['image/png', 'image/jpeg', 'image/gif', 'image/webp'] as const;
export const EVIDENCE_VIDEO_TYPES = ['video/mp4', 'video/quicktime', 'video/webm'] as const;

export const EVIDENCE_ACCEPT = [...EVIDENCE_IMAGE_TYPES, ...EVIDENCE_VIDEO_TYPES].join(',');
export const EVIDENCE_FORMAT_LABEL = 'png, jpg, gif, webp, mp4, mov, webm';

export type EvidenceKind = 'image' | 'video';

/** 'image' | 'video' kalau MIME termasuk whitelist, null kalau tidak (jangan ditampilkan inline). */
export function evidenceKindOf(mimeType: string): EvidenceKind | null {
  const type = mimeType.toLowerCase().split(';')[0]?.trim() ?? '';
  if ((EVIDENCE_IMAGE_TYPES as readonly string[]).includes(type)) return 'image';
  if ((EVIDENCE_VIDEO_TYPES as readonly string[]).includes(type)) return 'video';
  return null;
}
