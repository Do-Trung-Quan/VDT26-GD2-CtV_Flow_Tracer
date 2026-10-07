import type { IrFragment } from '../protocol/generated/ir-fragment.js';
import type { FileAccess } from '../runtime/file-access.js';

/** Một file được cấp cho pha extract. */
export interface InputFile {
  path: string;
  hash: string;
  content: string;
}

/** Ngữ cảnh pha resolve: đọc thêm file cấu hình (angular.json, proxy.conf.json…) qua FileAccess. */
export interface ResolveContext {
  access: FileAccess;
  options: Record<string, string>;
}

/** Hợp đồng adapter phía sidecar — tương ứng StackAdapter (Java) nhưng chạy trọn 2 pha trong sidecar. */
export interface NodeAdapter {
  readonly id: string;
  /** PHA 1 — từng file độc lập; kết quả phải serialize JSON được để cache. */
  extract(file: InputFile): Promise<unknown>;
  /** PHA 2 — ghép dữ kiện cả Source → IrFragment. */
  resolve(facts: unknown[], ctx: ResolveContext): Promise<IrFragment>;
}
