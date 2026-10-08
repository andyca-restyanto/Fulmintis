// frontend/src/modules/testrepository/utils/testFolderTree.ts
import type { TestFolderResponse, TestFolderTreeNode } from '../types/testFolder.types';

/**
 * Susun flat list (apa adanya dari GET /api/projects/{id}/test-folders)
 * jadi struktur tree bersarang berdasarkan parentId -- dipakai buat render
 * sidebar "Test Suites".
 */
export function buildFolderTree(flatFolders: TestFolderResponse[]): TestFolderTreeNode[] {
  const nodeById = new Map<string, TestFolderTreeNode>();
  flatFolders.forEach((folder) => {
    nodeById.set(folder.id, { ...folder, children: [] });
  });

  const roots: TestFolderTreeNode[] = [];
  flatFolders.forEach((folder) => {
    const node = nodeById.get(folder.id)!;
    const parentNode = folder.parentId ? nodeById.get(folder.parentId) : undefined;
    if (parentNode) {
      parentNode.children.push(node);
    } else {
      // folder.parentId null, ATAU parent-nya entah kenapa tidak ada di
      // list ini -- tetap dianggap root supaya tidak "hilang" dari tree.
      roots.push(node);
    }
  });
  return roots;
}

export interface FlatFolderOption {
  id: string;
  label: string; // sudah termasuk indentasi visual sesuai depth
}

/**
 * Dipakai utk isi dropdown "Parent" di NewFolderModal -- folder ber-indent
 * sesuai kedalamannya di tree, supaya user paham struktur nested-nya.
 */
export function flattenTreeForDropdown(tree: TestFolderTreeNode[], depth = 0): FlatFolderOption[] {
  const options: FlatFolderOption[] = [];
  for (const node of tree) {
    const indent = depth > 0 ? `${'—'.repeat(depth)} ` : '';
    options.push({ id: node.id, label: `${indent}${node.folderName}` });
    options.push(...flattenTreeForDropdown(node.children, depth + 1));
  }
  return options;
}
