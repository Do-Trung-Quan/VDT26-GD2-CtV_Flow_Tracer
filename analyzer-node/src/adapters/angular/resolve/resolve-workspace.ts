import type { IrFragment } from '../../../protocol/generated/ir-fragment.js';
import type { ResolveContext } from '../../adapter.js';
import type { AngularFileFacts } from '../facts.js';

/**
 * PHA 2: angular.json + tsconfig paths + federation → symbol table → DI (InjectionToken) → call graph → url-evaluator
 * → cây UI_APP/UI_SCREEN/…/UI_ELEMENT → IrFragment. TODO.
 */
export async function resolveWorkspace(facts: AngularFileFacts[], ctx: ResolveContext): Promise<IrFragment> {
  void facts;
  void ctx;
  throw new Error('TODO: chưa triển khai');
}
