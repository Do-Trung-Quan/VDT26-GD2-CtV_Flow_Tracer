import type { EdgeCandidateDto, EdgeDetailDto, EdgeNodeDto } from '../../../core/api/api-types';
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
}

const LEVEL: Record<string, { label: string; pill: string }> = {
  HIGH: { label: 'Cao', pill: 'p-green' },
  MEDIUM: { label: 'Trung bình', pill: 'p-amber' },
  LOW: { label: 'Thấp', pill: 'p-gray' }
};

/**
 * Bấm vào mũi tên nào thì mở popover liên kết: bước đi (không phải chiều trả về / tự xử lý) gắn với một liên kết trong IR
 * (`edgeRef`) mà chưa chắc chắn hoặc đã chốt tay. Liên kết chắc chắn chỉ cần mô tả khi di chuột.
 */
export function opensLinkPopover(step: Step): boolean {
  return !step.returning && step.from !== step.to && !!step.edgeRef && (step.confidence !== 'CERTAIN' || step.pinned);
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
    return candidates.map((c) => ({
      nodeKey: c.nodeKey ?? '',
      name: nodeName(c.node, c.nodeKey),
      detail: [nodeDetail(c.node), c.reason ?? ''].filter(Boolean).join(' · '),
      level: c.level ? LEVEL[c.level] : null,
      current: !!c.current
    }));
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
