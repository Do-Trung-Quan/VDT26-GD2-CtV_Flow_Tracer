import type { CallRef, MethodFact } from '../facts.js';
import type { DiResolver } from './di.resolver.js';
import type { SymbolTable, ClassRef } from './symbol-table.js';

export type CallTarget =
  | { kind: 'method'; ref: ClassRef; method: MethodFact; confidence: 'CERTAIN' | 'INFERRED'; reason?: string }
  | { kind: 'unresolved'; candidates: { ref: ClassRef; method: MethodFact }[]; reason: string };

/** Phần mô tả receiver ngoài `this`: kiểu khai báo của biến gốc, hoặc lời gọi đã tạo ra receiver. */
export interface ReceiverInfo {
  rootType?: string | undefined;
  via?: CallRef | undefined;
}

/** Nối một lời gọi `this.a.b.method()` (hoặc qua biến / kết quả lời gọi khác) tới method thật trong class của dự án. */
export class CallResolver {
  constructor(
    private readonly table: SymbolTable,
    private readonly di: DiResolver
  ) {}

  /**
   * @param owner class chứa lời gọi
   * @param root `this`, tên biến / tham số, hoặc null (nối chuỗi)
   * @param path các field đi từ gốc tới receiver (`[]` = gọi hàm của chính receiver)
   * @param receiver kiểu của biến gốc hoặc lời gọi tạo ra receiver (khi `root` không phải `this`)
   * @returns undefined khi đích không phải mã của dự án (thư viện, `Router`, `MatDialog`…), kiểu không suy ra được, hoặc
   *     không tìm thấy hàm
   */
  resolve(
    owner: ClassRef,
    root: string | null,
    path: readonly string[],
    method: string,
    argCount: number,
    receiver: ReceiverInfo = {}
  ): CallTarget | undefined {
    const start = this.startOf(owner, root, receiver);
    if (!start) {
      return undefined;
    }
    let current = start.ref;
    let confidence = start.confidence;
    let reason = start.reason;
    for (const field of path) {
      const dep = this.di.resolveField(current, field);
      if (!dep) {
        return undefined;
      }
      if (dep.kind === 'unresolved') {
        const candidates = dep.candidates
          .map((ref) => ({ ref, method: this.findMethod(ref, method, argCount) }))
          .filter((c): c is { ref: ClassRef; method: MethodFact } => c.method !== undefined);
        return candidates.length > 0 ? { kind: 'unresolved', candidates, reason: dep.reason } : undefined;
      }
      current = dep.ref;
      if (dep.confidence === 'INFERRED') {
        confidence = 'INFERRED';
      }
      reason = dep.reason ?? reason;
    }
    const found = this.findMethodWithOwner(current, method, argCount);
    return found ? { kind: 'method', ref: found.ref, method: found.method, confidence, ...(reason ? { reason } : {}) } : undefined;
  }

  /** Class của receiver: `this` → chính class; biến có kiểu khai báo; kết quả của một lời gọi (kiểu trả về khai báo). */
  private startOf(
    owner: ClassRef,
    root: string | null,
    receiver: ReceiverInfo
  ): { ref: ClassRef; confidence: 'CERTAIN' | 'INFERRED'; reason?: string } | undefined {
    if (root === 'this') {
      return { ref: owner, confidence: 'CERTAIN' };
    }
    if (receiver.via) {
      return this.resultOf(owner, receiver.via);
    }
    if (receiver.rootType) {
      const dep = this.di.resolveType(owner, receiver.rootType);
      return dep?.kind === 'class' ? { ref: dep.ref, confidence: dep.confidence, ...(dep.reason ? { reason: dep.reason } : {}) } : undefined;
    }
    return undefined;
  }

  /**
   * Class của giá trị mà một lời gọi trả về, suy ra từ kiểu trả về ĐÃ KHAI BÁO của hàm đích (không đoán từ thân hàm).
   * `await` thì dùng kiểu bên trong `Promise<T>`. Không có kiểu khai báo hoặc kiểu không phải class của dự án thì undefined.
   */
  resultOf(owner: ClassRef, ref: CallRef): { ref: ClassRef; confidence: 'CERTAIN' | 'INFERRED'; reason?: string } | undefined {
    const target = this.resolve(owner, ref.root, ref.path, ref.method, ref.argCount, { rootType: ref.rootType, via: ref.via });
    if (target?.kind !== 'method') {
      return undefined;
    }
    const typeName = ref.awaited && target.method.returnTypeName === 'Promise' ? target.method.returnTypeArg : target.method.returnTypeName;
    if (!typeName || ['Promise', 'Observable', 'Array', 'Set', 'Map', 'void'].includes(typeName)) {
      return undefined;
    }
    const dep = this.di.resolveType(target.ref, typeName);
    if (dep?.kind !== 'class') {
      return undefined;
    }
    const confidence = dep.confidence === 'INFERRED' || target.confidence === 'INFERRED' ? 'INFERRED' : 'CERTAIN';
    return { ref: dep.ref, confidence, reason: `Kiểu trả về đã khai báo của ${ref.method}() là ${typeName}` };
  }

  /** Method gọi được theo tên (kể cả kế thừa), không tính constructor. */
  findMethod(ref: ClassRef, name: string, argCount: number): MethodFact | undefined {
    return this.findMethodWithOwner(ref, name, argCount)?.method;
  }

  findMethodWithOwner(ref: ClassRef, name: string, argCount: number): { ref: ClassRef; method: MethodFact } | undefined {
    let current: ClassRef | undefined = ref;
    for (let hop = 0; current && hop < 6; hop++) {
      const matches = current.cls.methods.filter((m) => m.name === name && m.kind !== 'constructor');
      if (matches.length > 0) {
        const exact = matches.find((m) => m.params.length === argCount);
        return { ref: current, method: exact ?? matches[0]! };
      }
      current = this.di.parentOf(current);
    }
    return undefined;
  }
}
