import { describe, expect, it } from 'vitest';
import { nodeLabel } from '../frontend-tree-model';
import { adviceBadge } from '../sequence/edge-popover-model';
import { relationName } from './explanation-model';

describe('nhãn do AI đặt và lời khuyên của AI', () => {
  it('tên AI đặt thay chữ trên giao diện, giữ tiền tố loại và đánh dấu ai', () => {
    const l = nodeLabel({ kind: 'UI_ELEMENT', elementType: 'button', displayName: 'Lưu', aiLabel: 'Lưu danh mục' });
    expect(l).toEqual({ prefix: 'Nút', text: 'Lưu danh mục', technical: false, ai: true });
  });

  it('không có tên AI thì giữ chữ trên giao diện', () => {
    const l = nodeLabel({ kind: 'UI_ELEMENT', elementType: 'button', displayName: 'Lưu' });
    expect(l.text).toBe('Lưu');
    expect(l.ai).toBeUndefined();
  });

  it('quan hệ cũng hiện tên AI đặt', () => {
    expect(relationName({ kind: 'UI_ELEMENT', elementType: 'button', displayName: 'Xóa', aiLabel: 'Xóa chức vụ' })).toBe('Nút Xóa chức vụ');
  });

  it('lời khuyên của AI → nhãn và màu', () => {
    expect(adviceBadge('SHOULD')).toEqual({ label: 'Nên chọn', pill: 'p-green' });
    expect(adviceBadge('SHOULD_NOT')).toEqual({ label: 'Không nên chọn', pill: 'p-red' });
    expect(adviceBadge('UNSURE').label).toBe('Chưa chắc');
    expect(adviceBadge(undefined).label).toBe('Chưa chắc');
  });
});
