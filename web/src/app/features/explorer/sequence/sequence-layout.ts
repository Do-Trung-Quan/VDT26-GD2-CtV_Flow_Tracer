import type { Block, Lane, SequenceTrace, Step } from './sequence-model';

/** Đo độ rộng chữ (pixel). Tách riêng để test chạy được không cần canvas. */
export type Measure = (text: string, size?: number, bold?: boolean) => number;

export interface Frame {
  block: Block;
  /** Số khối bao ngoài khối này (0 = ngoài cùng); khối trong thụt vào. */
  depth: number;
  top: number;
  bottom: number;
}

export interface ElseLine {
  label: string;
  depth: number;
  y: number;
}

export interface Activation {
  laneIndex: number;
  y0: number;
  y1: number;
}

export interface SequenceLayout {
  lanes: Lane[];
  steps: Step[];
  /** Toạ độ tâm của từng đường sống. */
  x: number[];
  /** Bề rộng ô tiêu đề của từng cột. */
  boxW: number[];
  /** Toạ độ dọc của từng mũi tên (cùng thứ tự `steps`). */
  ys: number[];
  width: number;
  height: number;
  frames: Frame[];
  elseLines: ElseLine[];
  activations: Activation[];
  laneIndex(id: string): number;
}

export const FIRST_ROW_Y = 112;
const BLOCK_OPEN_GAP = 30;
const ELSE_GAP = 26;
const BLOCK_CLOSE_GAP = 14;

/** Nhãn hiển thị của mũi tên: chiều trả về có chữ "trả về", lần gọi lặp lại trỏ về lần đầu. */
export function stepLabel(step: Step): string {
  const base = (step.returning ? 'trả về ' : '') + step.label;
  return step.repeatOf === null ? base : `${base} · như bước ${step.repeatOf}`;
}

/**
 * Dàn cột và mũi tên theo kiểu PlantUML: cột rộng vừa chữ, nới khoảng cách để nhãn không tràn; khối alt/opt/loop lồng nhau
 * chiếm thêm chỗ ở đầu, ở vạch else và ở cuối khối.
 */
export function layoutSequence(trace: SequenceTrace, measure: Measure): SequenceLayout {
  const { lanes, steps, blocks } = trace;
  const idx = (id: string) => lanes.findIndex((l) => l.id === id);
  const boxW = lanes.map((l) => Math.max(96, measure(l.label, 13, true) + 28));
  const x: number[] = [];
  lanes.forEach((_, i) => {
    x[i] = i === 0 ? 30 + boxW[0] / 2 : x[i - 1] + boxW[i - 1] / 2 + boxW[i] / 2 + 36;
  });
  // nới khoảng cách để nhãn mũi tên không tràn
  for (const s of steps) {
    const a = idx(s.from);
    const b = idx(s.to);
    if (a < 0 || b < 0) {
      continue;
    }
    const w = measure(`${s.index}  ${stepLabel(s)}`) + 36;
    if (a === b) {
      if (a + 1 < lanes.length && x[a + 1] - x[a] < w + 46) {
        const d = w + 46 - (x[a + 1] - x[a]);
        for (let k = a + 1; k < x.length; k++) {
          x[k] += d;
        }
      }
      continue;
    }
    const lo = Math.min(a, b);
    const hi = Math.max(a, b);
    const span = x[hi] - x[lo];
    if (span < w) {
      for (let k = hi; k < x.length; k++) {
        x[k] += w - span;
      }
    }
  }

  const depthOf = (b: Block) =>
    blocks.filter((o) => o !== b && o.fromStep <= b.fromStep && o.toStep >= b.toStep && (o.fromStep < b.fromStep || o.toStep > b.toStep)).length;
  const order = (a: Block, b: Block) => depthOf(a) - depthOf(b);
  const startsAt = (n: number) => blocks.filter((b) => b.fromStep === n).sort(order);
  const elsesAt = (n: number) =>
    blocks
      .flatMap((b) => b.elseBranches.filter((e) => e.fromStep === n).map((e) => ({ block: b, label: e.label })))
      .sort((p, q) => order(p.block, q.block));
  const endsAt = (n: number) => blocks.filter((b) => b.toStep === n).sort((p, q) => order(q, p)); // trong cùng trước

  const ys: number[] = [];
  const elseLines: ElseLine[] = [];
  const frames = new Map<Block, Frame>();
  let y = FIRST_ROW_Y;
  steps.forEach((s) => {
    const opens = startsAt(s.index);
    const elses = elsesAt(s.index);
    y += BLOCK_OPEN_GAP * opens.length + ELSE_GAP * elses.length;
    ys.push(y);
    elses.forEach((e, j) =>
      elseLines.push({ label: e.label, depth: depthOf(e.block), y: y - BLOCK_OPEN_GAP * opens.length - ELSE_GAP * (elses.length - 1 - j) - BLOCK_OPEN_GAP })
    );
    opens.forEach((b, k) =>
      frames.set(b, { block: b, depth: depthOf(b), top: y - 38 - BLOCK_OPEN_GAP * (opens.length - 1 - k), bottom: 0 })
    );
    const ends = endsAt(s.index);
    ends.forEach((b, r) => {
      const f = frames.get(b);
      if (f) {
        f.bottom = y + 22 + BLOCK_CLOSE_GAP * r;
      }
    });
    y += s.from === s.to ? 50 : 40;
    y += BLOCK_CLOSE_GAP * ends.length;
  });

  const lastX = x.length ? x[x.length - 1] + boxW[boxW.length - 1] / 2 : 60;
  return {
    lanes,
    steps,
    x,
    boxW,
    ys,
    width: lastX + 40,
    height: y + 86,
    frames: [...frames.values()].filter((f) => f.bottom > 0),
    elseLines,
    activations: activations(steps, ys, idx),
    laneIndex: idx
  };
}

/** Thanh kích hoạt: bước đi mở, bước trả về đóng; trả về do lỗi không đóng (nhánh sau vẫn dùng tiếp cột đó). */
function activations(steps: Step[], ys: number[], idx: (id: string) => number): Activation[] {
  const result: Activation[] = [];
  const open = new Map<string, number>();
  steps.forEach((s, k) => {
    if (s.from === s.to) {
      return;
    }
    if (!s.returning) {
      for (const lane of [s.to, s.from]) {
        if (!open.has(lane)) {
          open.set(lane, ys[k]);
        }
      }
    } else if (!s.error && open.has(s.from)) {
      result.push({ laneIndex: idx(s.from), y0: open.get(s.from) ?? ys[k], y1: ys[k] });
      open.delete(s.from);
    }
  });
  const end = (ys.at(-1) ?? 0) + 4;
  open.forEach((start, lane) => result.push({ laneIndex: idx(lane), y0: start, y1: end }));
  return result.filter((a) => a.laneIndex >= 0);
}
