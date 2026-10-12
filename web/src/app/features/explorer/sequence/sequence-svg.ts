import type { Measure, SequenceLayout } from './sequence-layout';
import { stepLabel } from './sequence-layout';
import type { Lane, Step } from './sequence-model';

/** Màu nét theo mức tin cậy (khớp bản vẽ giao diện mẫu). */
export const COLORS = {
  CERTAIN: '#1F2937',
  INFERRED: '#B45309',
  UNRESOLVED: '#DC2626',
  PIN: '#15803D',
  HIGHLIGHT: '#B9000E'
} as const;

export interface SvgOptions {
  zoom: number;
  /** Số bước đang được tô sáng. */
  highlight: number | null;
  measure: Measure;
}

/** Thoát ký tự đặc biệt: nhãn lấy từ mã nguồn của người dùng nên không được tin. */
export function esc(text: string): string {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

export function stepColor(step: Step): string {
  if (step.pinned) {
    return COLORS.PIN;
  }
  if (step.confidence === 'INFERRED') {
    return COLORS.INFERRED;
  }
  if (step.confidence === 'UNRESOLVED') {
    return COLORS.UNRESOLVED;
  }
  return COLORS.CERTAIN;
}

const MARKER_COLORS = [COLORS.CERTAIN, COLORS.INFERRED, COLORS.UNRESOLVED, COLORS.PIN, COLORS.HIGHLIGHT];

/**
 * Vẽ sequence diagram thành chuỗi SVG thuần (thuộc tính nằm ngay trên thẻ, không dựa vào CSS ngoài) để dùng cả trên màn
 * hình lẫn khi xuất file SVG / PNG. Mỗi mũi tên có `data-n` = số bước để bắt sự kiện bằng ủy quyền.
 */
export function renderSequenceSvg(layout: SequenceLayout, options: SvgOptions): string {
  const { lanes, steps, x, ys, boxW, width: w, height: h } = layout;
  const { zoom, highlight } = options;
  const parts: string[] = [];
  parts.push(
    `<svg xmlns="http://www.w3.org/2000/svg" width="${Math.round(w * zoom)}" height="${Math.round(h * zoom)}" viewBox="0 0 ${w} ${h}" font-family="Segoe UI, Arial, sans-serif">`
  );
  parts.push(
    `<defs><pattern id="hatch" width="7" height="7" patternUnits="userSpaceOnUse" patternTransform="rotate(45)"><rect width="7" height="7" fill="#FEF2F2"/><line x1="0" y1="0" x2="0" y2="7" stroke="#FCA5A5" stroke-width="2.5"/></pattern>`
  );
  for (const c of MARKER_COLORS) {
    const id = c.slice(1);
    parts.push(
      `<marker id="mk${id}" markerWidth="10" markerHeight="8" refX="9.5" refY="4" orient="auto" markerUnits="userSpaceOnUse"><path d="M0,0 L10,4 L0,8 z" fill="${c}"/></marker>` +
        `<marker id="mo${id}" markerWidth="10" markerHeight="10" refX="9.5" refY="5" orient="auto" markerUnits="userSpaceOnUse"><path d="M0,0 L10,5 L0,10" fill="none" stroke="${c}" stroke-width="1.4"/></marker>`
    );
  }
  parts.push(`</defs><rect width="${w}" height="${h}" fill="#FFFFFF"/>`);

  // đường sống
  lanes.forEach((l, i) => {
    const top = l.role === 'USER' ? 82 : l.role === 'DATABASE' ? 86 : 66;
    parts.push(`<line x1="${x[i]}" y1="${top}" x2="${x[i]}" y2="${h - 58}" stroke="#9CA3AF" stroke-width="1" stroke-dasharray="5 4"/>`);
  });

  // khung alt / opt / loop và vạch else
  for (const f of [...layout.frames].sort((a, b) => a.depth - b.depth)) {
    const xl = 14 + f.depth * 8;
    const xr = w - 14 - f.depth * 8;
    const tag = f.block.type.toLowerCase();
    const tagW = 12 + 7.2 * tag.length;
    parts.push(`<rect x="${xl}" y="${f.top}" width="${xr - xl}" height="${f.bottom - f.top}" fill="none" stroke="#181818" stroke-width="1.2"/>`);
    parts.push(
      `<path d="M${xl} ${f.top} h${tagW} v14 l-7 7 h-${tagW - 7}z" fill="#F3F4F6" stroke="#181818" stroke-width="1.2"/>` +
        `<text x="${xl + 9}" y="${f.top + 15}" font-size="12" font-weight="700" fill="#111827">${tag}</text>`
    );
    const room = xr - xl - tagW - 24;
    parts.push(`<text x="${xl + tagW + 12}" y="${f.top + 15}" font-size="12" font-weight="700" fill="#111827">[${esc(fit(f.block.label, room, options.measure))}]</text>`);
  }
  for (const e of layout.elseLines) {
    const xl = 14 + e.depth * 8;
    const xr = w - 14 - e.depth * 8;
    parts.push(
      `<line x1="${xl}" y1="${e.y}" x2="${xr}" y2="${e.y}" stroke="#181818" stroke-width="1.2" stroke-dasharray="6 4"/>` +
        `<text x="${xl + 8}" y="${e.y + 15}" font-size="12" font-weight="700" fill="#111827">[${esc(fit(e.label, xr - xl - 24, options.measure))}]</text>`
    );
  }

  // thanh kích hoạt
  for (const a of layout.activations) {
    if (lanes[a.laneIndex].role === 'USER') {
      continue;
    }
    parts.push(
      `<rect x="${x[a.laneIndex] - 5}" y="${a.y0}" width="10" height="${Math.max(10, a.y1 - a.y0)}" fill="#FFFFFF" stroke="#181818" stroke-width="1.1"/>`
    );
  }

  // mũi tên
  steps.forEach((s, k) => parts.push(arrow(layout, s, ys[k], options, highlight === s.index)));

  // tiêu đề cột ở trên và lặp lại ở dưới (strictuml)
  lanes.forEach((l, i) => {
    parts.push(laneHead(l, x[i], 12, boxW[i]));
    parts.push(laneHead(l, x[i], h - 64, boxW[i]));
  });
  parts.push('</svg>');
  return parts.join('');
}

function arrow(layout: SequenceLayout, s: Step, y: number, options: SvgOptions, on: boolean): string {
  const { lanes, x } = layout;
  const a = layout.laneIndex(s.from);
  const b = layout.laneIndex(s.to);
  if (a < 0 || b < 0) {
    return '';
  }
  const base = stepColor(s);
  const color = on ? COLORS.HIGHLIGHT : base;
  const marker = `${s.returning ? 'mo' : 'mk'}${color.slice(1)}`;
  const dash = s.returning ? '5 4' : s.confidence === 'UNRESOLVED' ? '2 4' : '';
  const width = on ? 2.4 : 1.4;
  const text = `<tspan font-weight="700">${s.index}</tspan>  ${esc(stepLabel(s))}`;
  const out: string[] = [`<g class="arrow${on ? ' hl' : ''}" data-n="${s.index}" style="cursor:pointer">`];
  if (a === b) {
    const x0 = x[a] + 5;
    out.push(
      `<path d="M${x0} ${y - 8} h34 v22 h-32" fill="none" stroke="${color}" stroke-width="${width}" stroke-dasharray="${dash}" marker-end="url(#${marker})"/>` +
        `<rect x="${x0}" y="${y - 22}" width="${options.measure(`${s.index}  ${stepLabel(s)}`) + 60}" height="34" fill="transparent"/>` +
        `<text x="${x0 + 42}" y="${y - 12}" font-size="12.5" fill="${on ? COLORS.HIGHLIGHT : '#111827'}">${text}</text>`
    );
  } else {
    const dir = b > a ? 1 : -1;
    const x1 = x[a] + (lanes[a].role === 'USER' ? 0 : dir * 5);
    const x2 = x[b] - (lanes[b].role === 'USER' ? 0 : dir * 5);
    out.push(
      `<line x1="${x1}" y1="${y}" x2="${x2}" y2="${y}" stroke="${color}" stroke-width="${width}" stroke-dasharray="${dash}" marker-end="url(#${marker})"/>` +
        `<line x1="${x1}" y1="${y}" x2="${x2}" y2="${y}" stroke="transparent" stroke-width="16"/>` +
        `<text x="${(x1 + x2) / 2}" y="${s.returning ? y + 15 : y - 6}" text-anchor="middle" font-size="12.5" fill="${on ? COLORS.HIGHLIGHT : s.returning ? '#374151' : '#111827'}"${on ? ' font-weight="600"' : ''}>${text}</text>`
    );
    if (s.confidence === 'UNRESOLVED' || s.confidence === 'INFERRED' || s.pinned) {
      const badge = s.pinned ? '✓' : s.confidence === 'UNRESOLVED' ? '?' : '≈';
      const bx = x2 - dir * 16;
      out.push(
        `<circle cx="${bx}" cy="${y}" r="8" fill="${base}"/><text x="${bx}" y="${y + 4}" text-anchor="middle" font-size="11" font-weight="700" fill="#fff">${badge}</text>`
      );
    }
  }
  out.push('</g>');
  return out.join('');
}

function laneHead(l: Lane, cx: number, top: number, w: number): string {
  const name = esc(l.label);
  const label = (dy: number) => `<text x="${cx}" y="${top + dy}" text-anchor="middle" font-size="13" font-weight="700" fill="#111827">${name}</text>`;
  if (l.role === 'USER') {
    return `<g><circle cx="${cx}" cy="${top + 8}" r="7" fill="#fff" stroke="#181818" stroke-width="1.4"/><path d="M${cx} ${top + 15} v14 M${cx - 10} ${top + 20} h20 M${cx} ${top + 29} l-8 10 M${cx} ${top + 29} l8 10" stroke="#181818" stroke-width="1.4" fill="none"/>${label(54)}</g>`;
  }
  if (l.role === 'DATABASE') {
    return `<g><path d="M${cx - 26} ${top + 10} v30 a26 7 0 0 0 52 0 v-30" fill="#F3F4F6" stroke="#181818" stroke-width="1.4"/><ellipse cx="${cx}" cy="${top + 10}" rx="26" ry="7" fill="#F3F4F6" stroke="#181818" stroke-width="1.4"/>${label(62)}</g>`;
  }
  const fill = l.unresolved ? 'url(#hatch)' : '#F3F4F6';
  const dash = l.unresolved ? ' stroke-dasharray="5 3"' : '';
  return `<g><rect x="${cx - w / 2}" y="${top + 6}" width="${w}" height="34" rx="3" fill="${fill}" stroke="${l.unresolved ? COLORS.UNRESOLVED : '#181818'}" stroke-width="1.4"${dash}/>${label(28)}</g>`;
}

/** Cắt chữ cho vừa bề rộng, thêm "…" nếu bị cắt. */
function fit(text: string, maxWidth: number, measure: Measure): string {
  if (maxWidth <= 0 || measure(text, 12, true) <= maxWidth) {
    return text;
  }
  let cut = text;
  while (cut.length > 1 && measure(cut + '…', 12, true) > maxWidth) {
    cut = cut.slice(0, -1);
  }
  return cut + '…';
}
