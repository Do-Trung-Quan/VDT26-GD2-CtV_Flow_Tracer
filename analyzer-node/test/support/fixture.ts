import { createHash } from 'node:crypto';
import { mkdtemp, readFile, readdir } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { angularAdapter } from '../../src/adapters/angular/index.js';
import type { AnalyzeRequest } from '../../src/protocol/generated/analyze-request.js';
import type { IrFragment } from '../../src/protocol/generated/ir-fragment.js';
import { runPipeline } from '../../src/runtime/pipeline.js';

export const REPO_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..', '..');
export const FIXTURES = path.join(REPO_ROOT, 'fixtures');

const SKIP_DIRS = new Set(['node_modules', 'dist', '.angular', '.git']);

/** Quy tắc chọn file giống `SidecarStackAdapter.analyzes` của Engine. */
export function analyzesAngular(rel: string): boolean {
  const base = rel.split('/').pop() ?? rel;
  if (rel.endsWith('.spec.ts') || rel.endsWith('.d.ts')) {
    return false;
  }
  if (rel.endsWith('.ts') || rel.endsWith('.html')) {
    return true;
  }
  if (rel.endsWith('.json')) {
    return (
      base === 'angular.json' ||
      /^tsconfig.*\.json$/.test(base) ||
      /^proxy.*\.json$/.test(base) ||
      base === 'package.json' ||
      /(^|[/._-])vi([-_][a-z]{2})?\.json$/i.test(rel)
    );
  }
  return /^federation\.config\.(js|cjs|mjs)$/.test(base);
}

async function walk(dir: string, rel: string, out: string[]): Promise<void> {
  const entries = (await readdir(path.join(dir, rel), { withFileTypes: true })).sort((a, b) => a.name.localeCompare(b.name));
  for (const entry of entries) {
    const next = rel ? `${rel}/${entry.name}` : entry.name;
    if (entry.isDirectory()) {
      if (!SKIP_DIRS.has(entry.name)) {
        await walk(dir, next, out);
      }
    } else {
      out.push(next);
    }
  }
}

/** Chạy toàn bộ pipeline sidecar trên một thư mục fixture (không qua CLI). */
export async function analyzeFixture(name: string, options: Record<string, string> = {}): Promise<IrFragment> {
  const root = path.join(FIXTURES, name);
  const all: string[] = [];
  await walk(root, '', all);
  const files = [];
  for (const rel of all.filter(analyzesAngular)) {
    const content = await readFile(path.join(root, rel));
    files.push({ path: rel, hash: createHash('sha256').update(content).digest('hex') });
  }
  const request: AnalyzeRequest = {
    protocolVersion: 1,
    adapter: 'angular',
    sourceId: 'fixture',
    sourceRoot: root,
    files,
    cacheDir: await mkdtemp(path.join(os.tmpdir(), 'ft-cache-')),
    options
  };
  return runPipeline(angularAdapter, request);
}
