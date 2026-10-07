import type { NodeAdapter } from './adapter.js';
import { angularAdapter } from './angular/index.js';

/** Danh sách adapter của sidecar. php-legacy sẽ thêm ở phần mở rộng. */
const ADAPTERS: readonly NodeAdapter[] = [angularAdapter];

export function findAdapter(id: string): NodeAdapter | undefined {
  return ADAPTERS.find((a) => a.id === id);
}
