import type { ClassFact, ConstantFact, RouteArrayFact, TokenFact, TsFacts } from '../facts.js';
import { commonDirDepth, dirname, joinPathStrict } from '../workspace/paths.js';
import type { PathAlias } from '../workspace/tsconfig-paths.reader.js';

export interface ClassRef {
  file: string;
  cls: ClassFact;
}

export type Symbol =
  | { kind: 'class'; file: string; cls: ClassFact }
  | { kind: 'const'; file: string; constant: ConstantFact }
  | { kind: 'token'; file: string; token: TokenFact }
  | { kind: 'routes'; file: string; array: RouteArrayFact }
  /** Object literal hằng số (`environment`): chỉ có các thuộc tính con `environment.x`. */
  | { kind: 'namespace'; file: string };

const EXTENSIONS = ['', '.ts', '/index.ts', '/public-api.ts', '/public_api.ts'];
const MAX_BARREL_DEPTH = 8;

export function refKey(ref: ClassRef): string {
  return `${ref.file}#${ref.cls.name}`;
}

/**
 * Danh bạ: tên được import trong một file thực sự được khai báo ở đâu. Không có TypeScript type checker (dự án phân
 * tích không được dựa vào `node_modules`, có thể vắng hoặc khác phiên bản): giải bằng đường dẫn import (tương đối, alias của tsconfig), đi xuyên file barrel
 * (`export * from`). Thư viện ngoài không có mã nguồn trong Source thì không giải được và bị bỏ qua.
 */
export class SymbolTable {
  private readonly aliases: PathAlias[];
  private readonly classesByName = new Map<string, ClassRef[]>();

  constructor(
    readonly files: ReadonlyMap<string, TsFacts>,
    aliases: readonly PathAlias[]
  ) {
    // alias gần gốc Source hơn xếp sau; khi khớp nhiều alias, mẫu dài (cụ thể) hơn thắng
    this.aliases = [...aliases].sort((a, b) => b.pattern.length - a.pattern.length || a.configDir.localeCompare(b.configDir));
    for (const [path, facts] of [...files].sort(([a], [b]) => a.localeCompare(b))) {
      for (const cls of facts.classes) {
        const list = this.classesByName.get(cls.name) ?? [];
        list.push({ file: path, cls });
        this.classesByName.set(cls.name, list);
      }
    }
  }

  /** Mọi class có tên này trong Source (dùng để so khớp `implements X` khi X không giải được bằng import). */
  classesNamed(name: string): readonly ClassRef[] {
    return this.classesByName.get(name) ?? [];
  }

  /** `from` import `spec` → file TypeScript trong Source; null nếu là thư viện ngoài / không có trong Source. */
  resolveModule(from: string, spec: string): string | null {
    const candidates: string[] = [];
    if (spec.startsWith('.')) {
      const joined = joinPathStrict(dirname(from), spec);
      if (joined !== null) {
        candidates.push(joined);
      }
    } else {
      for (const alias of this.aliases) {
        const mapped = applyAlias(alias, spec);
        candidates.push(...mapped);
      }
    }
    for (const base of candidates) {
      const stripped = base.replace(/\.js$/, '');
      for (const ext of EXTENSIONS) {
        const path = stripped + ext;
        if (this.files.has(path)) {
          return path;
        }
      }
    }
    return null;
  }

  /** Khai báo mà file `file` nhìn thấy dưới tên `local` (import hoặc khai báo tại chỗ). */
  lookup(file: string, local: string): Symbol | undefined {
    const facts = this.files.get(file);
    if (!facts) {
      return undefined;
    }
    const own = this.declared(file, local);
    if (own) {
      return own;
    }
    const imp = facts.imports.find((i) => i.local === local);
    if (!imp) {
      return undefined;
    }
    const target = this.resolveModule(file, imp.from);
    return target ? this.exported(target, imp.imported, new Set(), 0) : undefined;
  }

  /** Khai báo được xuất ra từ `file` dưới tên `name`, đi xuyên barrel. */
  exported(file: string, name: string, seen: Set<string>, depth: number): Symbol | undefined {
    const key = `${file}::${name}`;
    if (depth > MAX_BARREL_DEPTH || seen.has(key)) {
      return undefined;
    }
    seen.add(key);
    const facts = this.files.get(file);
    if (!facts) {
      return undefined;
    }
    const own = this.declared(file, name);
    if (own) {
      return own;
    }
    for (const re of facts.reexports) {
      const target = this.resolveModule(file, re.from);
      if (!target) {
        continue;
      }
      if (re.names === null) {
        const found = this.exported(target, name, seen, depth + 1);
        if (found) {
          return found;
        }
      } else {
        const match = re.names.find((n) => n.exported === name);
        if (match) {
          const found = this.exported(target, match.local, seen, depth + 1);
          if (found) {
            return found;
          }
        }
      }
    }
    // `import { X } from './a'; export { X }`
    const imp = facts.imports.find((i) => i.local === name);
    if (imp) {
      const target = this.resolveModule(file, imp.from);
      return target ? this.exported(target, imp.imported, seen, depth + 1) : undefined;
    }
    return undefined;
  }

  /** Tìm hằng số theo tên đầy đủ (`API_BASE`, `environment.apiBase`) khai báo trong đúng file đó. */
  constantIn(file: string, name: string): ConstantFact | undefined {
    return this.files.get(file)?.constants.find((c) => c.name === name);
  }

  /** Độ gần của hai file (số thư mục chung) — dùng để chọn ứng viên gần nhất. */
  static proximity(a: string, b: string): number {
    return commonDirDepth(a, b);
  }

  private declared(file: string, name: string): Symbol | undefined {
    const facts = this.files.get(file);
    if (!facts) {
      return undefined;
    }
    const cls = facts.classes.find((c) => c.name === name);
    if (cls) {
      return { kind: 'class', file, cls };
    }
    const token = facts.tokens.find((t) => t.name === name);
    if (token) {
      return { kind: 'token', file, token };
    }
    const array = facts.routeArrays.find((r) => r.name === name);
    if (array) {
      return { kind: 'routes', file, array };
    }
    const constant = facts.constants.find((c) => c.name === name);
    if (constant) {
      return { kind: 'const', file, constant };
    }
    if (facts.constants.some((c) => c.name.startsWith(name + '.'))) {
      return { kind: 'namespace', file };
    }
    return undefined;
  }
}

function applyAlias(alias: PathAlias, spec: string): string[] {
  const star = alias.pattern.indexOf('*');
  if (star < 0) {
    return alias.pattern === spec ? alias.targets : [];
  }
  const prefix = alias.pattern.slice(0, star);
  const suffix = alias.pattern.slice(star + 1);
  if (!spec.startsWith(prefix) || !spec.endsWith(suffix) || spec.length < prefix.length + suffix.length) {
    return [];
  }
  const middle = spec.slice(prefix.length, spec.length - suffix.length);
  return alias.targets.map((t) => t.replace('*', middle));
}
