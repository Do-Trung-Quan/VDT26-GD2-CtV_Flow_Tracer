import type { FederationFacts } from '../facts.js';
import { dirname, joinPath } from './paths.js';

/** `remoteName` → (`exposedModule` → file, tương đối so với gốc Source). */
export type FederationMap = ReadonlyMap<string, ReadonlyMap<string, string>>;

/** Gom các `federation.config.js`: ứng dụng con chia sẻ file nào dưới tên nào. */
export function readFederation(files: readonly FederationFacts[]): FederationMap {
  const map = new Map<string, Map<string, string>>();
  for (const file of [...files].sort((a, b) => a.path.localeCompare(b.path))) {
    if (!file.name) {
      continue;
    }
    const exposes = new Map<string, string>();
    for (const [key, value] of Object.entries(file.exposes)) {
      exposes.set(key, joinPath(dirname(file.path), value));
    }
    map.set(file.name, exposes);
  }
  return map;
}
