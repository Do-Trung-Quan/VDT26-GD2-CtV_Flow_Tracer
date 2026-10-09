import type { NodeAdapter } from '../adapter.js';
import type { AngularFileFacts } from './facts.js';
import { detectAngular } from './detect.js';
import { extractFile } from './extract/extract-file.js';
import { resolveWorkspace } from './resolve/resolve-workspace.js';

/** Adapter "angular" — MVP. Xem .context/project_structure.md §5. */
export const angularAdapter: NodeAdapter = {
  id: 'angular',
  detect: (files) => detectAngular(files),
  extract: (file) => extractFile(file),
  resolve: (facts, ctx) => resolveWorkspace(facts as AngularFileFacts[], ctx)
};
