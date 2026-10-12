import type { NodeAdapter } from '../adapters/adapter.js';
import type { AnalyzeRequest } from '../protocol/generated/analyze-request.js';
import type { IrFragment } from '../protocol/generated/ir-fragment.js';
import { reportProgress } from '../protocol/progress.js';
import { FactsCache } from './facts-cache.js';
import { FileAccess } from './file-access.js';

/** Số file extract đồng thời. */
const CONCURRENCY = 8;

/** extract song song (có cache theo mã băm) → resolve → IrFragment đã sắp xếp ổn định. */
export async function runPipeline(adapter: NodeAdapter, request: AnalyzeRequest): Promise<IrFragment> {
  const files = [...request.files].sort((a, b) => a.path.localeCompare(b.path));
  const access = new FileAccess(request.sourceRoot, files.map((f) => f.path));
  const cache = new FactsCache(request.cacheDir, adapter.factsVersion);

  const facts: unknown[] = new Array(files.length);
  let processed = 0;
  for (let i = 0; i < files.length; i += CONCURRENCY) {
    const batch = files.slice(i, i + CONCURRENCY);
    await Promise.all(
      batch.map(async (file, j) => {
        let result = await cache.get(file.hash);
        if (result === undefined) {
          result = await adapter.extract({ path: file.path, hash: file.hash, content: await access.read(file.path) });
          await cache.put(file.hash, result);
        }
        // cùng nội dung ở hai đường dẫn dùng chung một mục cache: gán lại đường dẫn của file đang xét
        facts[i + j] = withPath(result, file.path);
      })
    );
    processed += batch.length;
    reportProgress({ type: 'progress', stage: 'EXTRACT', processed, total: files.length });
  }

  reportProgress({ type: 'progress', stage: 'RESOLVE' });
  const fragment = await adapter.resolve(facts, { access, options: request.options ?? {} });
  return sortFragment(fragment);
}

function sortFragment(fragment: IrFragment): IrFragment {
  return {
    ...fragment,
    nodes: [...fragment.nodes].sort((a, b) => a.key.localeCompare(b.key)),
    edges: [...fragment.edges].sort(
      (a, b) =>
        a.fromKey.localeCompare(b.fromKey) ||
        a.kind.localeCompare(b.kind) ||
        (a.callOrder ?? Number.MAX_SAFE_INTEGER) - (b.callOrder ?? Number.MAX_SAFE_INTEGER) ||
        (a.toKey ?? '').localeCompare(b.toKey ?? '')
    )
  };
}

function withPath(facts: unknown, path: string): unknown {
  return typeof facts === 'object' && facts !== null && 'path' in facts ? { ...facts, path } : facts;
}
