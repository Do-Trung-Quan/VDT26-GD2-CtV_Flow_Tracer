import type { TraceDto } from '../../../core/api/api-types';

export type Confidence = 'CERTAIN' | 'INFERRED' | 'UNRESOLVED' | 'RUNTIME_VERIFIED';
export type LaneRole = 'USER' | 'FRONTEND' | 'CONTROLLER' | 'SERVICE' | 'REPOSITORY' | 'DATABASE' | 'EXTERNAL';

/** Một cột (đường sống) của diagram. `unresolved`: cột chỉ có các bước tới chưa xác định → vẽ gạch chéo và thêm dấu "?". */
export interface Lane {
  id: string;
  label: string;
  role: LaneRole;
  unresolved: boolean;
}

/** Một mũi tên. Số `index` cũng là số bước trong tab Kịch bản. */
export interface Step {
  index: number;
  from: string;
  to: string;
  label: string;
  confidence: Confidence;
  reason: string | null;
  returning: boolean;
  pinned: boolean;
  error: boolean;
  /** Số bước của lần gọi đầu nếu đây là lần gọi lặp lại không vẽ lại phần bên trong. */
  repeatOf: number | null;
}

export interface BlockBranch {
  label: string;
  fromStep: number;
}

/** Khung điều kiện / lặp. Nhánh chính tới trước nhánh else đầu tiên; mỗi else tới trước nhánh kế tiếp; nhánh cuối tới `toStep`. */
export interface Block {
  type: 'ALT' | 'OPT' | 'LOOP';
  label: string;
  fromStep: number;
  toStep: number;
  elseBranches: BlockBranch[];
}

export interface SequenceTrace {
  traceId: string;
  startKey: string;
  startName: string;
  startDisplayName: string | null;
  lanes: Lane[];
  steps: Step[];
  blocks: Block[];
  warnings: string[];
  /** Luồng chỉ gồm User và Frontend, không gọi máy chủ. */
  uiOnly: boolean;
}

/** Đổi kiểu sinh từ OpenAPI (mọi trường tùy chọn) sang kiểu chặt dùng để vẽ. */
export function normalizeTrace(dto: TraceDto): SequenceTrace {
  const steps: Step[] = (dto.steps ?? []).map((s) => ({
    index: s.index ?? 0,
    from: s.from ?? '',
    to: s.to ?? '',
    label: s.label ?? '',
    confidence: s.confidence ?? 'CERTAIN',
    reason: s.reason ?? null,
    returning: s.returning ?? false,
    pinned: s.pinned ?? false,
    error: s.error ?? false,
    repeatOf: s.repeatOf ?? null
  }));
  const lanes: Lane[] = (dto.participants ?? []).map((p) => ({
    id: p.id ?? '',
    label: p.label ?? '',
    role: p.role ?? 'SERVICE',
    unresolved: false
  }));
  // Cột chỉ được đi tới bằng bước chưa xác định (interface nhiều lớp hiện thực): chưa biết lớp nào làm việc
  for (const lane of lanes) {
    const incoming = steps.filter((s) => !s.returning && s.from !== s.to && s.to === lane.id);
    if (incoming.length > 0 && incoming.every((s) => s.confidence === 'UNRESOLVED')) {
      lane.unresolved = true;
      lane.label += ' ?';
    }
  }
  return {
    traceId: dto.traceId ?? '',
    startKey: dto.startKey ?? '',
    startName: dto.startName ?? '',
    startDisplayName: dto.startDisplayName ?? null,
    lanes,
    steps,
    blocks: (dto.blocks ?? []).map((b) => ({
      type: b.type ?? 'OPT',
      label: b.label ?? '',
      fromStep: b.fromStep ?? 0,
      toStep: b.toStep ?? 0,
      elseBranches: (b.elseBranches ?? []).map((e) => ({ label: e.label ?? '', fromStep: e.fromStep ?? 0 }))
    })),
    warnings: dto.warnings ?? [],
    uiOnly: dto.uiOnly ?? false
  };
}

/** Bước chưa xác định đích (chiều đi), theo thứ tự. */
export function unresolvedSteps(trace: SequenceTrace): Step[] {
  return trace.steps.filter((s) => s.confidence === 'UNRESOLVED' && !s.returning);
}
