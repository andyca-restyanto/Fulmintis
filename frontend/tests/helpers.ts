// filepath: /frontend/tests/helpers.ts
// Pembantu test (dijalankan Node langsung, tanpa browser/Vitest): localStorage tiruan + pembuat JWT palsu.

export function installLocalStorage(): Map<string, string> {
  const store = new Map<string, string>();
  (globalThis as unknown as { localStorage: Storage }).localStorage = {
    getItem: (key: string) => store.get(key) ?? null,
    setItem: (key: string, value: string) => void store.set(key, value),
    removeItem: (key: string) => void store.delete(key),
    clear: () => store.clear(),
    key: (index: number) => [...store.keys()][index] ?? null,
    get length() {
      return store.size;
    },
  } as Storage;
  return store;
}

function base64Url(value: unknown): string {
  return Buffer.from(JSON.stringify(value)).toString('base64url');
}

/** JWT tidak bertanda tangan (FE memang tidak memverifikasi tanda tangan). */
export function fakeJwt(payload: Record<string, unknown>): string {
  return `${base64Url({ alg: 'HS256', typ: 'JWT' })}.${base64Url(payload)}.signature`;
}

export function inSeconds(seconds: number): number {
  return Math.floor(Date.now() / 1000) + seconds;
}
