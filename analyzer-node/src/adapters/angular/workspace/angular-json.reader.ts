import type { JsonFacts } from '../facts.js';
import { dirname, joinPath } from './paths.js';

/** Một project trong `angular.json`. Đường dẫn tương đối so với gốc Source, dùng dấu '/'. */
export interface AngularProject {
  name: string;
  type: 'application' | 'library';
  /** Thư mục gốc của project; rỗng = chính gốc Source. */
  root: string;
  sourceRoot: string;
  /** Đường dẫn file proxy.conf.json (nếu `serve` khai báo). */
  proxyConfig?: string;
  /** File angular.json chứa khai báo. */
  declaredIn: string;
}

/**
 * Đọc `angular.json`: ứng dụng và thư viện nằm ở đâu. Source không có `angular.json` (chỉ có thư mục mã) thì coi cả
 * Source là một ứng dụng tên `app`.
 */
export function readAngularProjects(files: readonly JsonFacts[]): AngularProject[] {
  const projects: AngularProject[] = [];
  for (const file of files.filter((f) => f.role === 'angular').sort((a, b) => a.path.localeCompare(b.path))) {
    const base = dirname(file.path);
    const config = file.content as { projects?: Record<string, unknown> } | undefined;
    for (const [name, raw] of Object.entries(config?.projects ?? {}).sort(([a], [b]) => a.localeCompare(b))) {
      const p = raw as Record<string, unknown>;
      const root = joinPath(base, typeof p['root'] === 'string' ? p['root'] : '');
      const sourceRoot = typeof p['sourceRoot'] === 'string' ? joinPath(base, p['sourceRoot']) : joinPath(root, 'src');
      const proxy = proxyConfigOf(p);
      projects.push({
        name,
        type: p['projectType'] === 'library' ? 'library' : 'application',
        root,
        sourceRoot,
        declaredIn: file.path,
        ...(proxy ? { proxyConfig: joinPath(base, proxy) } : {})
      });
    }
  }
  if (projects.length === 0) {
    projects.push({ name: 'app', type: 'application', root: '', sourceRoot: '', declaredIn: '' });
  }
  return projects;
}

function proxyConfigOf(project: Record<string, unknown>): string | undefined {
  for (const section of ['architect', 'targets']) {
    const targets = project[section] as Record<string, unknown> | undefined;
    const serve = targets?.['serve'] as { options?: { proxyConfig?: unknown } } | undefined;
    const value = serve?.options?.proxyConfig;
    if (typeof value === 'string') {
      return value;
    }
  }
  return undefined;
}

/** project chứa file: root dài nhất là tiền tố của đường dẫn; ứng dụng thắng thư viện khi bằng nhau. */
export function projectOf(projects: readonly AngularProject[], path: string): AngularProject | undefined {
  let best: AngularProject | undefined;
  for (const project of projects) {
    const prefix = project.root === '' ? '' : project.root + '/';
    if (path.startsWith(prefix) && (best === undefined || project.root.length > best.root.length)) {
      best = project;
    }
  }
  return best;
}
