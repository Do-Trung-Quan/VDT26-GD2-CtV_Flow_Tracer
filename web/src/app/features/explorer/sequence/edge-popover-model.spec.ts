import type { EdgeAdviceDto, EdgeDetailDto } from '../../../core/api/api-types';
import { adviceFor, candidateRows, confidenceBadge, initialSelection, opensLinkPopover, placePopover } from './edge-popover-model';
import type { Step } from './sequence-model';

const step = (over: Partial<Step>): Step => ({
  index: 2,
  from: 'fe',
  to: 'c1',
  label: 'POST /api/v1/items',
  confidence: 'INFERRED',
  reason: null,
  returning: false,
  pinned: false,
  error: false,
  repeatOf: null,
  edgeRef: 'e-1',
  ...over
});

describe('popover liên kết', () => {
  it('chỉ mở cho bước đi chưa chắc chắn hoặc đã chốt tay và có liên kết trong IR', () => {
    expect(opensLinkPopover(step({}))).toBe(true);
    expect(opensLinkPopover(step({ confidence: 'UNRESOLVED' }))).toBe(true);
    expect(opensLinkPopover(step({ confidence: 'CERTAIN', pinned: true }))).toBe(true);
    expect(opensLinkPopover(step({ confidence: 'CERTAIN' }))).toBe(false);
    expect(opensLinkPopover(step({ returning: true }))).toBe(false);
    expect(opensLinkPopover(step({ from: 'c1', to: 'c1', confidence: 'UNRESOLVED' }))).toBe(true); // bước tự xử lý chưa xác định
    expect(opensLinkPopover(step({ from: 'c1', to: 'c1', confidence: 'CERTAIN' }))).toBe(false);
    expect(opensLinkPopover(step({ edgeRef: null }))).toBe(false);
  });

  it('nhãn mức tin cậy theo bản vẽ; chốt tay thắng', () => {
    expect(confidenceBadge('INFERRED', false)).toEqual({ label: 'Suy luận', pill: 'p-amber' });
    expect(confidenceBadge('UNRESOLVED', false)).toEqual({ label: 'Chưa xác định', pill: 'p-red' });
    expect(confidenceBadge('UNRESOLVED', true)).toEqual({ label: 'Chốt tay', pill: 'p-green' });
  });

  it('ứng viên hiện mức khớp Cao / Trung bình / Thấp, không hiện con số', () => {
    const detail: EdgeDetailDto = {
      kind: 'HTTP_REQUEST',
      candidates: [
        { nodeKey: 'a', node: { name: 'GET /items/export', displayName: 'Xuất Excel' }, level: 'HIGH', reason: 'khớp đúng từng đoạn', current: true },
        { nodeKey: 'b', node: { name: 'GET /items/{id}' }, level: 'LOW', reason: 'khớp đường dẫn có biến' }
      ]
    };
    const rows = candidateRows(detail);
    expect(rows.map((r) => [r.name, r.level?.label, r.current])).toEqual([
      ['Xuất Excel', 'Cao', true],
      ['GET /items/{id}', 'Thấp', false]
    ]);
    expect(rows[0].detail).toBe('GET /items/export · khớp đúng từng đoạn');
    expect(JSON.stringify(rows)).not.toMatch(/0\.\d/);
    expect(initialSelection(detail, rows)).toBe('a');
  });

  it('ứng viên khớp tên với chuỗi gọi nhiều hơn lên trước; cùng số tín hiệu giữ thứ tự mức khớp', () => {
    const detail: EdgeDetailDto = {
      kind: 'HTTP_REQUEST',
      candidates: [
        { nodeKey: 'featured', node: { name: 'GET /featured-books' }, level: 'MEDIUM', hints: ['books'] },
        { nodeKey: 'config', node: { name: 'GET /config' }, level: 'MEDIUM', hints: [] },
        { nodeKey: 'most', node: { name: 'GET /most-borrowed-books' }, level: 'MEDIUM', hints: ['most', 'borrowed', 'books'] },
        { nodeKey: 'other', node: { name: 'GET /other' }, level: 'LOW' }
      ]
    };

    const rows = candidateRows(detail);

    expect(rows.map((r) => r.nodeKey)).toEqual(['most', 'featured', 'config', 'other']);
    expect(rows[0].hints).toEqual(['most', 'borrowed', 'books']);
  });

  it('không có ứng viên thì đích tự động là lựa chọn duy nhất; đích đã ghim được chọn sẵn', () => {
    const detail: EdgeDetailDto = { to: { nodeKey: 'x', name: 'POST /a' }, override: { decision: 'PIN', toNodeKey: 'y' } };
    const rows = candidateRows(detail);
    expect(rows).toHaveLength(1);
    expect(rows[0]).toMatchObject({ nodeKey: 'x', level: null, current: true });
    expect(initialSelection(detail, rows)).toBe('y');
    expect(candidateRows({})).toEqual([]);
  });

  it('popover luôn nằm trọn trong vùng canvas', () => {
    expect(placePopover({ x: 900, y: 500 }, { width: 390, height: 300 }, { width: 1000, height: 600 })).toEqual({ left: 598, top: 288, maxHeight: 576 });
    expect(placePopover({ x: -40, y: -10 }, { width: 390, height: 300 }, { width: 1000, height: 600 })).toEqual({ left: 12, top: 12, maxHeight: 576 });
    // cao hơn vùng canvas: giới hạn chiều cao, ghim sát mép trên
    expect(placePopover({ x: 10, y: 200 }, { width: 390, height: 900 }, { width: 1000, height: 400 })).toEqual({ left: 12, top: 12, maxHeight: 376 });
  });
});

describe('lời khuyên của AI trên popover', () => {
  const advice: EdgeAdviceDto = {
    edgeId: 'e1',
    analyses: [{ nodeKey: 'a', advice: 'SHOULD_NOT', reason: 'Khác phương thức' }],
    extra: { node: { nodeKey: 'cur', name: 'POST /x' }, reason: 'Khớp đường dẫn và kiểu dữ liệu' }
  };

  it('chưa hỏi AI thì không có lời khuyên', () => {
    expect(adviceFor(null, 'a', false)).toBeNull();
  });

  it('ứng viên có phân tích riêng thì hiện phân tích đó', () => {
    expect(adviceFor(advice, 'a', false)).toEqual({ badge: { label: 'Không nên chọn', pill: 'p-red' }, reason: 'Khác phương thức' });
    const withEvidence: EdgeAdviceDto = { analyses: [{ nodeKey: 'b', advice: 'SHOULD', reason: 'Khớp chuỗi truyền vào', evidence: 'most-borrowed-books' }] };
    expect(adviceFor(withEvidence, 'b', false)?.evidence).toBe('most-borrowed-books');
  });

  it('AI đề xuất đúng dòng đang có sẵn (đích tự động hiện tại) thì lời giải thích vẫn hiện trên dòng đó', () => {
    expect(adviceFor(advice, 'cur', undefined)).toEqual({ badge: { label: 'Nên chọn', pill: 'p-green' }, reason: 'Khớp đường dẫn và kiểu dữ liệu' });
  });

  it('dòng do AI thêm và dòng không liên quan', () => {
    expect(adviceFor(advice, 'cur', true)?.reason).toBe('Khớp đường dẫn và kiểu dữ liệu');
    expect(adviceFor(advice, 'khac', false)).toBeNull();
  });
});
