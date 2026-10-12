import type { JsonFacts } from '../facts.js';
import { dirname, joinPath } from './paths.js';

/** Đường dẫn tắt khi import, vd `@shop/shared` hoặc `@shop/*`. */
export interface PathAlias {
  /** Mẫu, có thể kết thúc bằng `/*`. */
  pattern: string;
  /** Các đích, tương đối so với gốc Source; có thể có `*`. */
  targets: string[];
  /** Thư mục của tsconfig khai báo (alias gần file hơn được ưu tiên). */
  configDir: string;
}

interface TsConfigContent {
  extends?: string;
  compilerOptions?: { baseUrl?: string; paths?: Record<string, string[]> };
}

/**
 * Đọc `compilerOptions.paths` của mọi tsconfig (kể cả qua `extends` tương đối). Chỉ đọc file đã được Engine cấp; không
 * chạy gì.
 */
export function readTsconfigPaths(files: readonly JsonFacts[]): PathAlias[] {
  const byPath = new Map(files.filter((f) => f.role === 'tsconfig').map((f) => [f.path, f]));
  const aliases: PathAlias[] = [];
  for (const file of [...byPath.values()].sort((a, b) => a.path.localeCompare(b.path))) {
    const { baseUrl, paths } = effectiveOptions(file.path, byPath, new Set());
    const configDir = dirname(file.path);
    for (const [pattern, targets] of Object.entries(paths).sort(([a], [b]) => a.localeCompare(b))) {
      aliases.push({ pattern, targets: targets.map((t) => joinPath(baseUrl, t)), configDir });
    }
  }
  return aliases;
}

function effectiveOptions(
  path: string,
  byPath: ReadonlyMap<string, JsonFacts>,
  seen: Set<string>
): { baseUrl: string; paths: Record<string, string[]> } {
  const config = byPath.get(path)?.content as TsConfigContent | undefined;
  const dir = dirname(path);
  let baseUrl = dir;
  let paths: Record<string, string[]> = {};
  if (config?.extends && config.extends.startsWith('.') && !seen.has(path)) {
    seen.add(path);
    const parentPath = joinPath(dir, config.extends.endsWith('.json') ? config.extends : config.extends + '.json');
    const parent = effectiveOptions(parentPath, byPath, seen);
    baseUrl = parent.baseUrl;
    paths = parent.paths;
  }
  const options = config?.compilerOptions;
  if (options?.baseUrl !== undefined) {
    baseUrl = joinPath(dir, options.baseUrl);
  }
  if (options?.paths) {
    paths = { ...paths, ...options.paths };
  }
  return { baseUrl, paths };
}
