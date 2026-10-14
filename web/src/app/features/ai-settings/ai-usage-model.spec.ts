import { describe, expect, it } from 'vitest';
import { ALL_SINCE, barHeight, dateRange, rangeDates, vnNumber } from './ai-usage-model';

describe('ai-usage-model', () => {
  it('định dạng số kiểu Việt Nam', () => {
    expect(vnNumber(412300)).toBe('412.300');
    expect(vnNumber(undefined)).toBe('0');
  });

  it('chiều cao cột theo cột lớn nhất, cột có số liệu không biến mất', () => {
    expect(barHeight(200, [200, 100])).toBe(100);
    expect(barHeight(100, [200, 100])).toBe(50);
    expect(barHeight(1, [100000, 1])).toBe(4);
    expect(barHeight(0, [10, 0])).toBe(0);
    expect(barHeight(5, [])).toBe(0);
  });

  it('khoảng ngày tính cả hôm nay', () => {
    const r = dateRange(7, new Date(2026, 9, 13));
    expect(r).toEqual({ from: '2026-10-07', to: '2026-10-13' });
    expect(dateRange(30, new Date(2026, 9, 13)).from).toBe('2026-09-14');
  });

  it('1 ngày là hôm nay, Tất cả bắt đầu từ mốc rất sớm', () => {
    const today = new Date(2026, 9, 13);
    expect(rangeDates(1, today)).toEqual({ from: '2026-10-13', to: '2026-10-13' });
    expect(rangeDates(7, today).from).toBe('2026-10-07');
    expect(rangeDates('all', today)).toEqual({ from: ALL_SINCE, to: '2026-10-13' });
  });
});
