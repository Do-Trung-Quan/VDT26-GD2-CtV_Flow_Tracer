import type { EdgeDetailDto } from '../../../core/api/api-types';
import { candidateRows, confidenceBadge, initialSelection, opensLinkPopover, placePopover } from './edge-popover-model';
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
    expect(opensLinkPopover(step({ to: 'fe' }))).toBe(false);
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
