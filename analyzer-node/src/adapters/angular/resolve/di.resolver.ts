import type { FieldFact, ProviderFact } from '../facts.js';
import { refKey, SymbolTable, type ClassRef } from './symbol-table.js';

export type Dependency =
  | { kind: 'class'; ref: ClassRef; confidence: 'CERTAIN' | 'INFERRED'; reason?: string }
  | { kind: 'unresolved'; candidates: ClassRef[]; reason: string };

interface ProviderEntry {
  provider: ProviderFact;
  file: string;
}

/**
 * Xác định mỗi field được tiêm (`inject(X)`, `@Inject(TOKEN)`, tham số constructor) thực sự là class nào:
 *
 * - kiểu là class cụ thể → chính nó (`CERTAIN`);
 * - token hoặc interface / abstract class → tra khai báo provider (`{ provide, useExisting | useClass }`): một đích rõ ràng
 *   là `CERTAIN`; khai báo qua `useFactory` hoặc chỉ có đúng một lớp hiện thực là `INFERRED`; nhiều đích thì
 *   `UNRESOLVED` kèm ứng viên — không chọn hộ.
 */
export class DiResolver {
  private readonly providers = new Map<string, ProviderEntry[]>();

  constructor(
    private readonly table: SymbolTable,
    /** Thư mục nhóm (project) của file — để ưu tiên provider cùng ứng dụng. */
    private readonly groupOf: (file: string) => string
  ) {
    for (const [file, facts] of [...table.files].sort(([a], [b]) => a.localeCompare(b))) {
      for (const provider of facts.providers) {
        const list = this.providers.get(provider.provide) ?? [];
        list.push({ provider, file });
        this.providers.set(provider.provide, list);
      }
    }
  }

  /** Field của class (kể cả class cha) theo tên. */
  findField(ref: ClassRef, name: string): { owner: ClassRef; field: FieldFact } | undefined {
    let current: ClassRef | undefined = ref;
    for (let hop = 0; current && hop < 6; hop++) {
      const field = current.cls.fields.find((f) => f.name === name);
      if (field) {
        return { owner: current, field };
      }
      current = this.parentOf(current);
    }
    return undefined;
  }

  parentOf(ref: ClassRef): ClassRef | undefined {
    const parent = ref.cls.extends;
    const symbol = parent ? this.table.lookup(ref.file, parent) : undefined;
    return symbol?.kind === 'class' ? { file: symbol.file, cls: symbol.cls } : undefined;
  }

  resolveField(owner: ClassRef, fieldName: string): Dependency | undefined {
    const found = this.findField(owner, fieldName);
    if (!found) {
      return undefined;
    }
    const { owner: declaredIn, field } = found;
    const file = declaredIn.file;
    if (field.injectArg) {
      const symbol = this.table.lookup(file, field.injectArg);
      if (symbol?.kind === 'class') {
        return this.fromType({ file: symbol.file, cls: symbol.cls }, field.injectArg, declaredIn);
      }
      if (symbol?.kind === 'token') {
        const viaProvider = this.fromProviders(field.injectArg, symbol.file, declaredIn);
        if (viaProvider) {
          return viaProvider;
        }
        // token không có provider trong Source: dùng kiểu generic của token (InjectionToken<T>) nếu là class của dự án
        const generic = symbol.token.typeName ? this.table.lookup(symbol.file, symbol.token.typeName) : undefined;
        return generic?.kind === 'class' ? this.fromType({ file: generic.file, cls: generic.cls }, generic.cls.name, declaredIn) : undefined;
      }
    }
    if (field.typeName) {
      const symbol = this.table.lookup(file, field.typeName);
      if (symbol?.kind === 'class') {
        return this.fromType({ file: symbol.file, cls: symbol.cls }, field.typeName, declaredIn);
      }
    }
    return undefined;
  }

  /** Class ứng với một tên kiểu viết trong file của `requester` (kiểu của biến, kiểu trả về…), kể cả interface → lớp hiện thực. */
  resolveType(requester: ClassRef, typeName: string): Dependency | undefined {
    const symbol = this.table.lookup(requester.file, typeName);
    return symbol?.kind === 'class' ? this.fromType({ file: symbol.file, cls: symbol.cls }, typeName, requester) : undefined;
  }

  private fromType(type: ClassRef, localName: string, requester: ClassRef): Dependency | undefined {
    const concrete = type.cls.kind === 'class' && !type.cls.isAbstract;
    // abstract class dùng làm token: có provider thì ưu tiên provider
    const viaProvider = this.fromProviders(type.cls.name, type.file, requester);
    if (viaProvider) {
      return viaProvider;
    }
    if (concrete) {
      return { kind: 'class', ref: type, confidence: 'CERTAIN' };
    }
    const implementors = this.implementorsOf(type);
    if (implementors.length === 1 && implementors[0]) {
      return {
        kind: 'class',
        ref: implementors[0],
        confidence: 'INFERRED',
        reason: `${localName} chỉ có một lớp hiện thực là ${implementors[0].cls.name}`
      };
    }
    if (implementors.length > 1) {
      return {
        kind: 'unresolved',
        candidates: implementors,
        reason: `${localName} có ${implementors.length} lớp hiện thực: ${implementors.map((i) => i.cls.name).join(', ')}`
      };
    }
    return undefined;
  }

  /** Tra `{ provide: <token>, useExisting | useClass | useFactory }` cho token khai báo ở `tokenFile`. */
  private fromProviders(tokenName: string, tokenFile: string, requester: ClassRef): Dependency | undefined {
    const entries = (this.providers.get(tokenName) ?? []).filter((e) => this.sameToken(e, tokenName, tokenFile));
    if (entries.length === 0) {
      return undefined;
    }
    const targets = new Map<string, { ref: ClassRef; inferred: boolean; entry: ProviderEntry }>();
    for (const entry of entries) {
      for (const target of this.providerTargets(entry)) {
        const key = refKey(target.ref);
        if (!targets.has(key)) {
          targets.set(key, { ...target, entry });
        }
      }
    }
    if (targets.size === 0) {
      return undefined;
    }
    let candidates = [...targets.values()];
    if (candidates.length > 1) {
      const sameGroup = candidates.filter((c) => this.groupOf(c.entry.file) === this.groupOf(requester.file));
      if (sameGroup.length >= 1 && sameGroup.length < candidates.length) {
        candidates = sameGroup;
      }
    }
    if (candidates.length === 1 && candidates[0]) {
      const only = candidates[0];
      return {
        kind: 'class',
        ref: only.ref,
        confidence: only.inferred ? 'INFERRED' : 'CERTAIN',
        reason: `Provider ${tokenName} → ${only.ref.cls.name} (${only.entry.file}:${only.entry.provider.line})`
      };
    }
    return {
      kind: 'unresolved',
      candidates: candidates.map((c) => c.ref),
      reason: `${tokenName} có ${candidates.length} provider khác nhau: ${candidates.map((c) => c.ref.cls.name).join(', ')}`
    };
  }

  /** Provider khai báo cho đúng token này (cùng file khai báo, hoặc tên duy nhất khi không giải được import). */
  private sameToken(entry: ProviderEntry, tokenName: string, tokenFile: string): boolean {
    const symbol = this.table.lookup(entry.file, tokenName);
    return symbol === undefined || ('file' in symbol && symbol.file === tokenFile);
  }

  private providerTargets(entry: ProviderEntry): { ref: ClassRef; inferred: boolean }[] {
    const { provider, file } = entry;
    const direct = provider.useExisting ?? provider.useClass;
    if (direct) {
      const symbol = this.table.lookup(file, direct);
      return symbol?.kind === 'class' ? [{ ref: { file: symbol.file, cls: symbol.cls }, inferred: false }] : [];
    }
    if (provider.useFactory) {
      const names = new Set<string>();
      for (const m of provider.useFactory.matchAll(/\binject\(\s*([A-Za-z_$][\w$]*)\s*\)|\bnew\s+([A-Za-z_$][\w$]*)\s*\(/g)) {
        const name = m[1] ?? m[2];
        if (name) {
          names.add(name);
        }
      }
      return [...names]
        .map((n) => this.table.lookup(file, n))
        .filter((s) => s?.kind === 'class')
        .map((s) => ({ ref: { file: s!.file, cls: (s as { cls: ClassRef['cls'] }).cls }, inferred: true }));
    }
    return [];
  }

  /** Các class cụ thể `implements` / `extends` kiểu này (so khớp qua import, không chỉ qua tên). */
  implementorsOf(type: ClassRef): ClassRef[] {
    const out: ClassRef[] = [];
    for (const [file, facts] of this.table.files) {
      for (const cls of facts.classes) {
        if (cls.kind !== 'class' || cls.isAbstract) {
          continue;
        }
        const names = [...cls.implements, ...(cls.extends ? [cls.extends] : [])];
        if (names.some((n) => this.sameType(file, n, type))) {
          out.push({ file, cls });
        }
      }
    }
    return out.sort((a, b) => refKey(a).localeCompare(refKey(b)));
  }

  private sameType(file: string, local: string, type: ClassRef): boolean {
    if (local !== type.cls.name) {
      return false;
    }
    const symbol = this.table.lookup(file, local);
    return symbol?.kind === 'class' && symbol.file === type.file && symbol.cls.name === type.cls.name;
  }
}
