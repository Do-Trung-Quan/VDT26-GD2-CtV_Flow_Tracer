import type { TraceDto } from '../../../core/api/api-types';
import { layoutSequence } from './sequence-layout';
import type { Measure } from './sequence-layout';
import { normalizeTrace, unresolvedSteps } from './sequence-model';
import { COLORS, esc, renderSequenceSvg } from './sequence-svg';

const measure: Measure = (text) => text.length * 7;

/** Luồng thêm mới danh mục (rút gọn): kiểm tra tồn tại → nhánh lỗi (alt) / nhánh còn lại; một bước chưa xác định. */
function dto(overrides: Partial<TraceDto> = {}): TraceDto {
  return {
    traceId: 't1',
    startKey: 'java:endpoint:POST /api/v1/categories',
    startName: 'POST /api/v1/categories',
    participants: [
      { id: 'user', label: 'Client', role: 'USER' },
      { id: 'c1', label: 'Controller Danh mục', role: 'CONTROLLER' },
      { id: 's1', label: 'Service Danh mục', role: 'SERVICE' },
      { id: 'r1', label: 'Repository Danh mục', role: 'REPOSITORY' },
      { id: 'db', label: 'Database', role: 'DATABASE' }
    ],
    steps: [
      { index: 1, from: 'user', to: 'c1', label: 'POST /api/v1/categories', confidence: 'CERTAIN' },
      { index: 2, from: 'c1', to: 's1', label: 'create(request)', confidence: 'CERTAIN' },
      { index: 3, from: 's1', to: 'r1', label: 'existsByCode(code)', confidence: 'CERTAIN' },
      { index: 4, from: 'r1', to: 'db', label: 'SELECT category', confidence: 'CERTAIN' },
      { index: 5, from: 'db', to: 'r1', label: 'kết quả', confidence: 'CERTAIN', returning: true },
      { index: 6, from: 'r1', to: 's1', label: 'true / false', confidence: 'CERTAIN', returning: true },
      { index: 7, from: 's1', to: 'c1', label: 'CATEGORY_CODE_EXISTS', confidence: 'CERTAIN', returning: true, error: true },
      { index: 8, from: 'c1', to: 'user', label: 'BusinessException', confidence: 'CERTAIN', returning: true, error: true },
      { index: 9, from: 's1', to: 's1', label: 'toResponse()', confidence: 'CERTAIN' },
      { index: 10, from: 's1', to: 'c1', label: 'CategoryResponse', confidence: 'CERTAIN', returning: true },
      { index: 11, from: 'c1', to: 'user', label: '200 ApiResponse<CategoryResponse>', confidence: 'CERTAIN', returning: true }
    ],
    blocks: [{ type: 'ALT', label: 'categoryRepository.existsByCode(code)', fromStep: 7, toStep: 11, elseBranches: [{ label: 'ngược lại', fromStep: 9 }] }],
    warnings: [],
    ...overrides
  };
}

describe('sequence layout', () => {
  const trace = normalizeTrace(dto());
  const layout = layoutSequence(trace, measure);

  it('cột xếp từ trái sang phải theo thứ tự participant, mỗi cột đủ rộng cho tiêu đề', () => {
    expect(layout.x.length).toBe(5);
    expect(layout.x).toEqual([...layout.x].sort((a, b) => a - b));
    layout.boxW.forEach((w) => expect(w).toBeGreaterThanOrEqual(96));
    expect(layout.width).toBeGreaterThan(layout.x[4]);
  });

  it('mũi tên đi từ trên xuống; bước tự xử lý chiếm nhiều chỗ hơn', () => {
    expect(layout.ys).toEqual([...layout.ys].sort((a, b) => a - b));
    expect(layout.ys[7] - layout.ys[6]).toBe(40); // bước đi/trả về thường: 40
    expect(layout.ys[9] - layout.ys[8]).toBe(50); // sau bước tự xử lý (bước 9): 50
    expect(layout.height).toBeGreaterThan(layout.ys[10]);
  });

  it('khối alt bao các bước 7–11 và có vạch else trước bước 9', () => {
    const frame = layout.frames[0];
    expect(frame.block.type).toBe('ALT');
    expect(frame.top).toBeLessThan(layout.ys[6]);
    expect(frame.bottom).toBeGreaterThan(layout.ys[10]);
    expect(layout.elseLines).toHaveLength(1);
    expect(layout.elseLines[0].y).toBeGreaterThan(layout.ys[7]);
    expect(layout.elseLines[0].y).toBeLessThan(layout.ys[8]);
  });

  it('trả về do lỗi không đóng thanh kích hoạt; trả về thường thì đóng', () => {
    const service = layout.laneIndex('s1');
    const bars = layout.activations.filter((a) => a.laneIndex === service);
    // service được kích hoạt từ bước 2 tới bước trả về cuối (bước 10), không bị cắt ở bước 7
    expect(bars).toHaveLength(1);
    expect(bars[0].y0).toBe(layout.ys[1]);
    expect(bars[0].y1).toBe(layout.ys[9]);
  });

  it('khối lồng nhau: khối trong thụt vào và nằm gọn trong khối ngoài', () => {
    const nested = normalizeTrace(
      dto({
        blocks: [
          { type: 'ALT', label: 'a', fromStep: 7, toStep: 11, elseBranches: [{ label: 'ngược lại', fromStep: 9 }] },
          { type: 'LOOP', label: 'items', fromStep: 9, toStep: 10, elseBranches: [] }
        ]
      })
    );
    const l = layoutSequence(nested, measure);
    const outer = l.frames.find((f) => f.block.type === 'ALT')!;
    const inner = l.frames.find((f) => f.block.type === 'LOOP')!;
    expect(inner.depth).toBe(1);
    expect(inner.top).toBeGreaterThan(outer.top);
    expect(inner.bottom).toBeLessThanOrEqual(outer.bottom);
    expect(inner.top).toBeGreaterThan(l.elseLines[0].y); // khối trong mở sau vạch else
  });
});

describe('sequence svg', () => {
  const trace = normalizeTrace(dto());
  const layout = layoutSequence(trace, measure);
  const svg = renderSequenceSvg(layout, { zoom: 1, highlight: null, measure });

  it('mỗi bước có đúng một nhóm mũi tên mang số bước; chiều trả về có chữ "trả về"', () => {
    expect((svg.match(/class="arrow"/g) ?? []).length).toBe(11);
    expect(svg).toContain('data-n="7"');
    expect(svg).toContain('trả về true / false');
    expect(svg).toContain('[categoryRepository.existsByCode(code)]');
    expect(svg).toContain('[ngược lại]');
  });

  it('thoát ký tự đặc biệt trong nhãn lấy từ mã nguồn', () => {
    const evil = normalizeTrace(
      dto({ steps: [{ index: 1, from: 'user', to: 'c1', label: '<script>alert("x")</script>', confidence: 'CERTAIN' }], blocks: [] })
    );
    const out = renderSequenceSvg(layoutSequence(evil, measure), { zoom: 1, highlight: null, measure });
    expect(out).not.toContain('<script>');
    expect(out).toContain('&lt;script&gt;');
    expect(esc(`a&b"c'`)).toBe('a&amp;b&quot;c&#39;');
  });

  it('tô sáng bước được chọn bằng màu thương hiệu', () => {
    const on = renderSequenceSvg(layout, { zoom: 1, highlight: 3, measure });
    expect(on).toContain('class="arrow hl" data-n="3"');
    expect(on).toContain(`stroke="${COLORS.HIGHLIGHT}"`);
    expect(svg).not.toContain('class="arrow hl"');
  });

  it('kích thước theo mức zoom nhưng viewBox giữ nguyên', () => {
    const half = renderSequenceSvg(layout, { zoom: 0.5, highlight: null, measure });
    expect(half).toContain(`width="${Math.round(layout.width * 0.5)}"`);
    expect(half).toContain(`viewBox="0 0 ${layout.width} ${layout.height}"`);
  });

  it('bước chưa xác định: nét chấm đỏ, huy hiệu "?", cột đích gạch chéo và có dấu "?"', () => {
    const t = normalizeTrace(
      dto({
        participants: [
          { id: 'user', label: 'Client', role: 'USER' },
          { id: 'c1', label: 'Controller A', role: 'CONTROLLER' },
          { id: 's2', label: 'Service B', role: 'SERVICE' }
        ],
        steps: [
          { index: 1, from: 'user', to: 'c1', label: 'GET /a', confidence: 'CERTAIN' },
          { index: 2, from: 'c1', to: 's2', label: 'count(id)', confidence: 'UNRESOLVED', reason: '2 lớp hiện thực' },
          { index: 3, from: 's2', to: 'c1', label: '(chưa xác định)', confidence: 'UNRESOLVED', returning: true }
        ],
        blocks: []
      })
    );
    expect(t.lanes[2].unresolved).toBe(true);
    expect(t.lanes[2].label).toBe('Service B ?');
    expect(unresolvedSteps(t).map((s) => s.index)).toEqual([2]);
    const out = renderSequenceSvg(layoutSequence(t, measure), { zoom: 1, highlight: null, measure });
    expect(out).toContain('stroke-dasharray="2 4"');
    expect(out).toContain('url(#hatch)');
    expect(out).toContain('>?</text>');
  });

  it('lần gọi lặp lại ghi rõ trỏ về lần đầu', () => {
    const t = normalizeTrace(
      dto({ steps: [{ index: 1, from: 'user', to: 'c1', label: 'x()', confidence: 'CERTAIN', repeatOf: 1 }], blocks: [] })
    );
    expect(renderSequenceSvg(layoutSequence(t, measure), { zoom: 1, highlight: null, measure })).toContain('x() · như bước 1');
  });
});
