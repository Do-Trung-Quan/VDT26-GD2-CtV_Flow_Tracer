import type { FederationFacts, JsonFacts } from '../facts.js';

/** Thư mục thường chứa file ngôn ngữ. */
const I18N_DIR = /(^|\/)(i18n|locale|locales|lang|langs|language|languages|translations?|l10n)\//i;
const VI_FILE = /(^|[/._-])vi([-_][a-z]{2})?\.json$/i;

/** File JSON ngôn ngữ tiếng Việt (`assets/i18n/vi.json`, `locales/vi-VN.json`). */
export function isViI18nJson(path: string): boolean {
  return VI_FILE.test(path) && (I18N_DIR.test(path) || /(^|\/)vi([-_][a-z]{2})?\.json$/i.test(path));
}

/** JSON lồng → khóa làm phẳng `a.b.c` → chuỗi. */
export function flattenJsonStrings(value: unknown, prefix = '', out: Record<string, string> = {}, depth = 0): Record<string, string> {
  if (depth > 6) {
    return out;
  }
  if (typeof value === 'string') {
    if (prefix) {
      out[prefix] = value;
    }
    return out;
  }
  if (typeof value === 'object' && value !== null && !Array.isArray(value)) {
    for (const [k, v] of Object.entries(value)) {
      flattenJsonStrings(v, prefix ? `${prefix}.${k}` : k, out, depth + 1);
    }
  }
  return out;
}

export function i18nJsonFacts(path: string, hash: string, text: string): JsonFacts {
  try {
    const entries = flattenJsonStrings(JSON.parse(stripBom(text)));
    return { kind: 'json', role: 'i18n', path, hash, entries, parseErrors: [] };
  } catch (e) {
    return { kind: 'json', role: 'i18n', path, hash, parseErrors: [{ message: `JSON không hợp lệ: ${(e as Error).message}` }] };
  }
}

export function stripBom(text: string): string {
  return text.charCodeAt(0) === 0xfeff ? text.slice(1) : text;
}

/**
 * `federation.config.js` — KHÔNG được chạy file này (CLAUDE.md §3.4); chỉ đọc văn bản: `name: 'x'` và khối
 * `exposes: { './Routes': './src/routes.ts' }`.
 */
export function federationFacts(path: string, hash: string, text: string): FederationFacts {
  const name = /\bname\s*:\s*['"`]([^'"`]+)['"`]/.exec(text)?.[1];
  const exposes: Record<string, string> = {};
  const block = /\bexposes\s*:\s*\{([^}]*)\}/s.exec(text)?.[1];
  if (block) {
    for (const m of block.matchAll(/['"`]([^'"`]+)['"`]\s*:\s*['"`]([^'"`]+)['"`]/g)) {
      const key = m[1];
      const value = m[2];
      if (key && value) {
        exposes[key] = value;
      }
    }
  }
  const remotesBlock = /\bremotes\s*:\s*\{([^}]*)\}/s.exec(text)?.[1];
  const remotes = remotesBlock ? [...remotesBlock.matchAll(/['"`]?([\w-]+)['"`]?\s*:/g)].map((m) => m[1] ?? '').filter(Boolean) : [];
  return { kind: 'federation', path, hash, ...(name ? { name } : {}), exposes, remotes: remotes.sort(), parseErrors: [] };
}
