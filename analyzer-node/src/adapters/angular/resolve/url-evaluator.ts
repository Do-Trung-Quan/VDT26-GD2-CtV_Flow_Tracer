import type { UrlPart } from '../facts.js';
import type { SymbolTable, Symbol, ClassRef } from './symbol-table.js';

const MAX_DEPTH = 8;

export interface UrlResult {
  /** Đường dẫn đã chuẩn hóa; biến đường dẫn ghi `{tên}`. Rỗng nếu không tính được. */
  pattern: string;
  /** Mọi thành phần đều tính được (không còn phần không rõ). */
  resolved: boolean;
  /** Tên miền nếu URL tuyệt đối (`https://host/…`). */
  host?: string;
  /** Lý do cụ thể khi chưa tính được. */
  issues: string[];
}

interface Scope {
  /** File chứa biểu thức (để giải import). */
  file: string;
  /** Class chứa biểu thức (để giải `this.x`). */
  cls?: ClassRef;
}

/**
 * Tính đường dẫn URL của lời gọi HTTP từ hằng số và biểu thức chuỗi (template literal, `+`), kể cả hằng số ở file khác
 * (`import { API_BASE }`, `environment.apiBase`) và field của class (`this.resource`). Phần không tính được (kết quả hàm,
 * biến ngoài) ghi vào `issues` — không đoán.
 */
export class UrlEvaluator {
  constructor(private readonly table: SymbolTable) {}

  evaluate(parts: readonly UrlPart[], file: string, cls?: ClassRef): UrlResult {
    const issues: string[] = [];
    const raw = this.render(parts, { file, ...(cls ? { cls } : {}) }, issues, 0, new Set());
    return normalize(raw, issues);
  }

  private render(parts: readonly UrlPart[], scope: Scope, issues: string[], depth: number, visiting: Set<string>): string {
    let out = '';
    for (const part of parts) {
      switch (part.t) {
        case 'lit':
          out += part.v;
          break;
        case 'param':
          out += `{${part.name}}`;
          break;
        case 'unk':
          out += '{?}';
          issues.push(`Biểu thức không tính được: ${part.text}`);
          break;
        case 'ref':
          out += this.renderRef(part.path, scope, issues, depth, visiting);
          break;
      }
    }
    return out;
  }

  private renderRef(path: string[], scope: Scope, issues: string[], depth: number, visiting: Set<string>): string {
    const label = path.join('.');
    if (depth >= MAX_DEPTH) {
      issues.push(`Hằng số lồng quá sâu: ${label}`);
      return '{?}';
    }
    const found = this.findRef(path, scope);
    if (!found) {
      issues.push(`Không tìm thấy giá trị của ${label}`);
      return `{${path[path.length - 1] ?? '?'}}`;
    }
    const key = `${found.scope.file}::${found.id}`;
    if (visiting.has(key)) {
      issues.push(`Tham chiếu vòng: ${label}`);
      return '{?}';
    }
    visiting.add(key);
    const text = this.render(found.parts, found.scope, issues, depth + 1, visiting);
    visiting.delete(key);
    return text;
  }

  private findRef(path: string[], scope: Scope): { parts: UrlPart[]; scope: Scope; id: string } | undefined {
    const [root, ...rest] = path;
    if (root === undefined) {
      return undefined;
    }
    if (root === 'this') {
      const [name, ...more] = rest;
      if (!scope.cls || name === undefined || more.length > 0) {
        return undefined;
      }
      return this.fieldInit(scope.cls, name);
    }
    // hằng số cùng file hoặc được import
    const full = path.join('.');
    const local = this.table.constantIn(scope.file, full);
    if (local) {
      return { parts: local.parts, scope: { file: scope.file, ...(scope.cls ? { cls: scope.cls } : {}) }, id: full };
    }
    const symbol = this.table.lookup(scope.file, root);
    if (!symbol) {
      return undefined;
    }
    if (symbol.kind === 'const' && rest.length === 0) {
      return { parts: symbol.constant.parts, scope: { file: symbol.file }, id: root };
    }
    // `environment.apiBase`, `Api.BASE`: tìm hằng số / enum đầy đủ tên trong file khai báo `root`
    const declaredIn = symbol.kind === 'const' || symbol.kind === 'class' || symbol.kind === 'namespace' ? symbol.file : undefined;
    if (declaredIn && rest.length > 0) {
      const constant = this.table.constantIn(declaredIn, full);
      if (constant) {
        return { parts: constant.parts, scope: { file: declaredIn }, id: full };
      }
      if (symbol.kind === 'class' && rest.length === 1 && rest[0]) {
        return this.fieldInit({ file: symbol.file, cls: symbol.cls }, rest[0], true);
      }
    }
    return undefined;
  }

  /** Giá trị khởi tạo của field (kể cả field của class cha). */
  private fieldInit(ref: ClassRef, name: string, wantStatic = false): { parts: UrlPart[]; scope: Scope; id: string } | undefined {
    let current: ClassRef | undefined = ref;
    for (let hop = 0; current && hop < 6; hop++) {
      const field = current.cls.fields.find((f) => f.name === name && (!wantStatic || f.isStatic));
      if (field?.init) {
        return { parts: field.init, scope: { file: current.file, cls: current }, id: `${current.cls.name}.${name}` };
      }
      const parentName: string | undefined = current.cls.extends;
      const parentSymbol: Symbol | undefined = parentName ? this.table.lookup(current.file, parentName) : undefined;
      current = parentSymbol?.kind === 'class' ? { file: parentSymbol.file, cls: parentSymbol.cls } : undefined;
    }
    return undefined;
  }
}

function normalize(raw: string, issues: string[]): UrlResult {
  let text = raw.trim();
  let host: string | undefined;
  const absolute = /^(https?:)?\/\/([^/?#{]+)(.*)$/i.exec(text);
  if (absolute) {
    host = absolute[2];
    text = absolute[3] ?? '';
  }
  text = text.replace(/[?#].*$/, '').replace(/([^:])\/{2,}/g, '$1/').replace(/^\/{2,}/, '/');
  const unresolved = text.includes('{?}');
  if (text !== '' && !text.startsWith('/') && !text.startsWith('{')) {
    text = '/' + text;
  }
  const onlyUnknown = text.replace(/\{\?\}/g, '').replace(/\//g, '') === '';
  return {
    pattern: onlyUnknown ? '' : text,
    resolved: issues.length === 0 && !unresolved && text !== '',
    ...(host ? { host } : {}),
    issues: [...new Set(issues)]
  };
}
