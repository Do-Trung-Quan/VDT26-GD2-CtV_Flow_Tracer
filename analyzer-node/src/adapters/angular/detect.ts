import type { DetectResult } from '../../protocol/generated/detect-result.js';

/** File Engine cần gửi kèm khi nhận diện Angular (khai báo tương ứng trong AdapterRegistry của Engine). */
export const ANGULAR_DETECT_FILES = ['angular.json', 'package.json'] as const;

export const SCORE_WITH_CORE = 0.95;
export const SCORE_WORKSPACE_ONLY = 0.6;

/**
 * Nhận diện Angular: `angular.json` có `projects` và `package.json` có `@angular/core`.
 * Chỉ có `angular.json` (chưa cài / không đọc được package.json) thì điểm thấp hơn, không có phiên bản.
 */
export function detectAngular(files: Readonly<Record<string, string>>): DetectResult {
  const workspace = parseJson(files['angular.json']);
  if (!isObject(workspace) || !isObject(workspace['projects'])) {
    return none();
  }
  const range = angularCoreRange(parseJson(files['package.json']));
  if (range === undefined) {
    return { score: SCORE_WORKSPACE_ONLY, stackName: 'Angular', version: null, reason: 'angular.json' };
  }
  return {
    score: SCORE_WITH_CORE,
    stackName: 'Angular',
    version: cleanVersion(range),
    reason: `angular.json + @angular/core ${range}`
  };
}

function angularCoreRange(pkg: unknown): string | undefined {
  if (!isObject(pkg)) {
    return undefined;
  }
  for (const section of ['dependencies', 'devDependencies', 'peerDependencies']) {
    const deps = pkg[section];
    if (isObject(deps) && typeof deps['@angular/core'] === 'string') {
      return deps['@angular/core'];
    }
  }
  return undefined;
}

/** "^21.0.3" → "21.0.3"; "~15.2" → "15.2"; dải không đọc được → null. */
export function cleanVersion(range: string): string | null {
  const m = /(\d+(?:\.\d+){0,2})/.exec(range);
  return m?.[1] ?? null;
}

function parseJson(text: string | undefined): unknown {
  if (text === undefined) {
    return undefined;
  }
  try {
    return JSON.parse(text);
  } catch {
    return undefined;
  }
}

function isObject(v: unknown): v is Record<string, unknown> {
  return typeof v === 'object' && v !== null && !Array.isArray(v);
}

function none(): DetectResult {
  return { score: 0, stackName: null, version: null, reason: null };
}
