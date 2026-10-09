import type { DetectedRootDto, DetectionDto, SourceRole } from '../../core/api/api-types';

export type NoteKind = 'warn' | 'info' | 'errb';

/** Kết quả xử lý một lần nhận diện thư mục (4 trường hợp của Nhóm 1 trong feature_list). */
export interface DetectionOutcome {
  /** Gốc dự án mới, chưa có trong danh sách. */
  fresh: DetectedRootDto[];
  /** Thông báo ngắn — chỉ có khi cần người dùng chú ý; nhận diện bình thường thì không có. */
  note?: { kind: NoteKind; lines: string[] };
  /** Thư mục gốc nên dùng thay thế (thư mục nằm bên trong một dự án). */
  suggested?: string;
}

export const ROLE_LABEL: Record<SourceRole, string> = {
  BACKEND: 'Backend',
  FRONTEND: 'Frontend',
  LEGACY: 'Hệ thống cũ'
};

export const ROLE_PILL: Record<SourceRole, string> = {
  BACKEND: 'p-blue',
  FRONTEND: 'p-green',
  LEGACY: 'p-amber'
};

export function stackText(r: { stackName?: string; version?: string }): string {
  return [r.stackName, r.version].filter(Boolean).join(' ');
}

const displayPath = (p: string) => p || 'SOURCE_ROOT';
const sameRoot = (a: DetectedRootDto, b: DetectedRootDto) => a.path === b.path && a.role === b.role;

/**
 * Xử lý kết quả A10. Nhận diện bình thường thì chỉ thêm gốc vào danh sách, không hiện thông báo; thông báo chỉ hiện khi
 * có mã cũ (PHP…), có cảnh báo, hoặc thư mục không dùng được.
 *
 * @param path thư mục vừa chọn
 * @param added các gốc đã có trong danh sách (để không thêm trùng)
 */
export function describeDetection(path: string, d: DetectionDto, added: DetectedRootDto[]): DetectionOutcome {
  const extra = [
    ...(d.legacyHints ?? []).map((h) => `Phát hiện mã ${h.language} (có thể là hệ thống cũ) trong ${h.path}.`),
    ...(d.warnings ?? [])
  ];

  if (d.status === 'INSIDE_PROJECT' && d.suggestedRoot?.path !== undefined) {
    const root = d.suggestedRoot.path;
    return {
      fresh: [],
      suggested: root,
      note: { kind: 'warn', lines: [`${displayPath(path)} nằm bên trong dự án ${displayPath(root)}.`, ...extra] }
    };
  }

  const roots = d.status === 'VALID' ? (d.roots ?? []) : [];
  if (roots.length === 0) {
    return {
      fresh: [],
      note: { kind: 'warn', lines: [`Không tìm thấy dự án Spring Boot hoặc Angular trong ${displayPath(path)}.`, ...extra] }
    };
  }

  const fresh = roots.filter((r) => !added.some((a) => sameRoot(a, r)));
  if (fresh.length === 0) {
    return { fresh, note: { kind: 'info', lines: ['Thư mục này đã được thêm.', ...extra] } };
  }
  return { fresh, note: extra.length ? { kind: 'warn', lines: extra } : undefined };
}
