import type { CandidateAdviceDto, EdgeAdviceDto, EdgeCandidateDto, EdgeDetailDto, EdgeNodeDto } from '../../../core/api/api-types';
import type { Step } from './sequence-model';

/** Nhãn + màu của mức tin cậy (giống bản vẽ giao diện). */
export interface ConfidenceBadge {
  label: string;
  pill: string;
}

/** Một dòng ứng viên trong popover. `level` null nghĩa là không có mức khớp (đích hiện tại duy nhất, hoặc API người dùng tự chọn). */
export interface CandidateRow {
  nodeKey: string;
  name: string;
  detail: string;
  level: { label: string; pill: string } | null;
  current: boolean;
  /** true với ứng viên do AI đề xuất thêm (đã được Engine kiểm tra: có thật trong IR và đúng kiểu). */
  aiExtra?: boolean;
  /** Từ trong chuỗi gọi (tên hàm, chuỗi truyền vào) trùng với đường dẫn của ứng viên — tín hiệu khớp tên, không cần AI. */
  hints?: string[];
}

/** Lời khuyên của AI cho một ứng viên (E2) → nhãn + màu. */
export function adviceBadge(advice: string | undefined): { label: string; pill: string } {
  switch (advice) {
    case 'SHOULD':
      return { label: 'Nên chọn', pill: 'p-green' };
    case 'SHOULD_NOT':
      return { label: 'Không nên chọn', pill: 'p-red' };
    default:
      return { label: 'Chưa chắc', pill: 'p-gray' };
  }
}

/**
 * Lời khuyên của AI cho một dòng ứng viên (E2): phân tích riêng của chính ứng viên đó; nếu không có thì lời đề xuất thêm khi nó
 * trùng với dòng này (vd đích tự động hiện tại) — dòng đó đã nằm sẵn trong danh sách nên không được thêm dòng mới, nhưng lời giải thích
 * vẫn phải hiện. null nếu chưa hỏi hoặc AI không nhắc tới.
 */
export function adviceFor(
  advice: EdgeAdviceDto | null,
  nodeKey: string,
  aiExtra: boolean | undefined
): { badge: { label: string; pill: string }; reason: string; evidence?: string } | null {
  if (!advice) {
    return null;
  }
  if (aiExtra) {
    return advice.extra?.reason ? { badge: adviceBadge('SHOULD'), reason: advice.extra.reason } : null;
  }
  const found: CandidateAdviceDto | undefined = advice.analyses?.find((x) => x.nodeKey === nodeKey);
  if (found?.reason) {
    return { badge: adviceBadge(found.advice), reason: found.reason, ...(found.evidence ? { evidence: found.evidence } : {}) };
  }
  return advice.extra?.node?.nodeKey === nodeKey && advice.extra?.reason ? { badge: adviceBadge('SHOULD'), reason: advice.extra.reason } : null;
}

const LEVEL: Record<string, { label: string; pill: string }> = {
  HIGH: { label: 'Cao', pill: 'p-green' },
  MEDIUM: { label: 'Trung bình', pill: 'p-amber' },
  LOW: { label: 'Thấp', pill: 'p-gray' }
};

/**
 * Bấm vào mũi tên nào thì mở popover liên kết: bước đi (không phải chiều trả về) gắn với một liên kết trong IR (`edgeRef`) mà chưa
 * chắc chắn hoặc đã chốt tay — kể cả bước tự xử lý (mũi tên quay về chính cột, vd hai hàm cùng tên trong một lớp). Liên kết chắc
 * chắn chỉ cần mô tả khi di chuột.
 */
export function opensLinkPopover(step: Step): boolean {
  return !step.returning && !!step.edgeRef && (step.confidence !== 'CERTAIN' || step.pinned);
}

export function confidenceBadge(confidence: string | undefined, pinned: boolean): ConfidenceBadge {
  if (pinned) {
    return { label: 'Chốt tay', pill: 'p-green' };
  }
  switch (confidence) {
    case 'INFERRED':
      return { label: 'Suy luận', pill: 'p-amber' };
    case 'UNRESOLVED':
      return { label: 'Chưa xác định', pill: 'p-red' };
    default:
      return { label: 'Chắc chắn', pill: 'p-gray' };
  }
}

export function nodeName(node: EdgeNodeDto | undefined, fallback = ''): string {
  return node?.displayName || node?.name || fallback;
}

/** Tên kỹ thuật dưới tên thân thiện (vd tên thân thiện của API kèm `GET /api/v1/categories`). */
function nodeDetail(node: EdgeNodeDto | undefined): string {
  return node?.displayName && node.name && node.displayName !== node.name ? node.name : '';
}

/**
 * Danh sách ứng viên để chọn: ứng viên của phân tích tĩnh (mức khớp cao trước, đúng thứ tự Engine trả); không có ứng viên mà
 * đã có đích tự động thì đích đó là lựa chọn duy nhất.
 */
export function candidateRows(detail: EdgeDetailDto): CandidateRow[] {
  const candidates: EdgeCandidateDto[] = detail.candidates ?? [];
  if (candidates.length) {
    const rows: CandidateRow[] = candidates.map((c) => ({
      nodeKey: c.nodeKey ?? '',
      name: nodeName(c.node, c.nodeKey),
      detail: [nodeDetail(c.node), c.reason ?? ''].filter(Boolean).join(' · '),
      level: c.level ? LEVEL[c.level] : null,
      current: !!c.current,
      hints: c.hints ?? []
    }));
    // Ứng viên khớp tên với chuỗi gọi nhiều hơn lên trước (sắp xếp ổn định: cùng số tín hiệu giữ thứ tự mức khớp của phân tích tĩnh)
    return rows.map((r, i) => ({ r, i })).sort((a, b) => (b.r.hints?.length ?? 0) - (a.r.hints?.length ?? 0) || a.i - b.i).map((x) => x.r);
  }
  if (detail.to?.nodeKey) {
    return [{ nodeKey: detail.to.nodeKey, name: nodeName(detail.to), detail: [nodeDetail(detail.to), 'Đích tự động hiện tại'].filter(Boolean).join(' · '), level: null, current: true }];
  }
  return [];
}

/** Lựa chọn ban đầu: đích đã ghim → đích tự động → ứng viên đầu. */
export function initialSelection(detail: EdgeDetailDto, rows: CandidateRow[]): string | null {
  const pinned = detail.override?.decision === 'PIN' ? detail.override.toNodeKey : undefined;
  return pinned ?? rows.find((r) => r.current)?.nodeKey ?? rows[0]?.nodeKey ?? null;
}

/** Giữ popover nằm trọn trong vùng canvas: đẩy vào trong nếu tràn, chiều cao không quá vùng canvas. */
export function placePopover(
  at: { x: number; y: number },
  size: { width: number; height: number },
  area: { width: number; height: number },
  margin = 12
): { left: number; top: number; maxHeight: number } {
  const maxHeight = Math.max(120, area.height - margin * 2);
  const height = Math.min(size.height, maxHeight);
  return {
    left: Math.max(margin, Math.min(at.x, area.width - size.width - margin)),
    top: Math.max(margin, Math.min(at.y, area.height - height - margin)),
    maxHeight
  };
}
