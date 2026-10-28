// frontend/src/modules/automation/utils/automationFiles.ts
// Util murni utk menampilkan hasil generate: pohon berkas, nama zip, ukuran.

export interface FileTreeNode {
  name: string;
  /** Path lengkap node ini ("tests" utk folder, "tests/login.spec.ts" utk berkas). */
  path: string;
  type: 'dir' | 'file';
  children: FileTreeNode[];
}

function compareNodes(a: FileTreeNode, b: FileTreeNode): number {
  if (a.type !== b.type) {
    return a.type === 'dir' ? -1 : 1; // folder dulu, baru berkas
  }
  return a.name.localeCompare(b.name);
}

function sortRecursively(nodes: FileTreeNode[]): void {
  nodes.sort(compareNodes);
  nodes.forEach((node) => sortRecursively(node.children));
}

/** Susun daftar path ("a/b/c.ts") menjadi pohon. Path ganda dilewati; urutan: folder dulu, lalu abjad. */
export function buildFileTree(paths: string[]): FileTreeNode[] {
  const roots: FileTreeNode[] = [];
  const seenFiles = new Set<string>();

  for (const path of paths) {
    if (seenFiles.has(path)) continue;
    seenFiles.add(path);

    const segments = path.split('/').filter((segment) => segment.length > 0);
    let level = roots;
    let currentPath = '';
    segments.forEach((segment, index) => {
      currentPath = currentPath === '' ? segment : `${currentPath}/${segment}`;
      const isFile = index === segments.length - 1;
      let node = level.find((candidate) => candidate.name === segment && candidate.type === (isFile ? 'file' : 'dir'));
      if (!node) {
        node = { name: segment, path: currentPath, type: isFile ? 'file' : 'dir', children: [] };
        level.push(node);
      }
      level = node.children;
    });
  }

  sortRecursively(roots);
  return roots;
}

/** Berkas pertama menurut urutan tampil pohon (utk dipilih otomatis saat hasil dibuka). */
export function firstFilePath(tree: FileTreeNode[]): string | null {
  for (const node of tree) {
    if (node.type === 'file') return node.path;
    const nested = firstFilePath(node.children);
    if (nested !== null) return nested;
  }
  return null;
}

export function fileName(path: string): string {
  const index = path.lastIndexOf('/');
  return index === -1 ? path : path.slice(index + 1);
}

/**
 * Nama zip untuk unduhan -- sama dengan nama dari backend ("automation-playwright-typescript-20261005.zip").
 * Header Content-Disposition tidak selalu terbaca lintas-origin, jadi nama dibentuk dari data generate.
 * Tanggal diambil dari teks createdAt (bukan new Date) supaya tidak bergeser zona waktu.
 */
export function zipFileName(generation: { framework: string; language: string; createdAt: string }): string {
  const day = generation.createdAt.slice(0, 10).replace(/-/g, '');
  return `automation-${generation.framework}-${generation.language}-${day}.zip`.toLowerCase();
}

export function lineCount(content: string): number {
  return content === '' ? 0 : content.split('\n').length;
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/** Ukuran isi dalam byte UTF-8 (bukan jumlah karakter). */
export function byteLength(content: string): number {
  return new TextEncoder().encode(content).length;
}

/** "05 Okt 2026, 10:00" -- LocalDateTime dari server dibaca sebagai waktu lokal (tanpa pergeseran zona waktu). */
export function formatWhen(isoLocalDateTime: string | null): string {
  if (!isoLocalDateTime) return '-';
  const date = new Date(isoLocalDateTime);
  if (Number.isNaN(date.getTime())) return isoLocalDateTime;
  return date.toLocaleString('id-ID', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
}
