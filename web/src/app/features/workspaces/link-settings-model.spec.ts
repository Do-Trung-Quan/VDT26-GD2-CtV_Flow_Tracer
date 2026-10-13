import { decisionLabel, needsReplacement, patternError, ruleTypeLabel, withRule, withoutRule } from './link-settings-model';

describe('luật liên kết', () => {
  const manual = [
    { type: 'PREFIX' as const, pattern: '{apiUrl}', replacement: '/api/v1', origin: 'MANUAL' as const },
    { type: 'REWRITE' as const, pattern: '^/gateway', origin: 'MANUAL' as const, description: 'proxy' }
  ];

  it('thêm, sửa, xóa luật thủ công thành danh sách gửi lên E7 (bỏ trường origin)', () => {
    expect(withRule(manual, null, { type: 'CONTEXT_PATH', pattern: '/shop' })).toEqual([
      { type: 'PREFIX', pattern: '{apiUrl}', replacement: '/api/v1' },
      { type: 'REWRITE', pattern: '^/gateway', description: 'proxy' },
      { type: 'CONTEXT_PATH', pattern: '/shop' }
    ]);
    expect(withRule(manual, 0, { type: 'PREFIX', pattern: '{base}', replacement: '/api' })[0]).toEqual({ type: 'PREFIX', pattern: '{base}', replacement: '/api' });
    expect(withoutRule(manual, 0)).toEqual([{ type: 'REWRITE', pattern: '^/gateway', description: 'proxy' }]);
  });

  it('nhãn loại luật và trường "Thay bằng"', () => {
    expect(ruleTypeLabel('REWRITE')).toBe('Proxy (biểu thức chính quy)');
    expect(needsReplacement('PREFIX')).toBe(true);
    expect(needsReplacement('CONTEXT_PATH')).toBe(false);
    expect(needsReplacement('QUERY_SINK')).toBe(false);
  });

  it('đọc lỗi mẫu của luật thứ i từ phản hồi 400 của Engine', () => {
    expect(patternError({ 'manual[2].pattern': 'Biểu thức chính quy không hợp lệ' }, 2)).toBe('Biểu thức chính quy không hợp lệ');
    expect(patternError({}, 0)).toBeNull();
  });

  it('nhãn quyết định chốt tay', () => {
    expect(decisionLabel({ decision: 'PIN' })).toEqual({ label: 'Ghim', pill: 'p-green' });
    expect(decisionLabel({ decision: 'REJECT' })).toEqual({ label: 'Loại bỏ', pill: 'p-red' });
  });
});
