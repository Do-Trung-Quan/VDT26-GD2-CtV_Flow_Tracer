import { formatDuration, isRunning, progressFraction, statusLabel } from './scan-format';

describe('scan-format', () => {
  it('phần đã xong tính theo giai đoạn và số mục thật đã xử lý', () => {
    expect(progressFraction(1, 6, 0, 10)).toBe(0);
    expect(progressFraction(3, 6, 5, 10)).toBeCloseTo(2.5 / 6);
    expect(progressFraction(6, 6, 0, 0)).toBeCloseTo(5 / 6);
    expect(progressFraction(6, 6, 10, 10)).toBe(1);
    expect(progressFraction(2, 0, 1, 1)).toBe(0);
  });

  it('định dạng thời gian đo được', () => {
    expect(formatDuration(850)).toBe('850 ms');
    expect(formatDuration(1234)).toBe('1,2 giây');
    expect(formatDuration(65_000)).toBe('1 phút 5 giây');
    expect(formatDuration(undefined)).toBe('—');
  });

  it('trạng thái', () => {
    expect(statusLabel(undefined)).toBe('Chưa quét');
    expect(statusLabel('PARTIAL')).toBe('Thành công một phần');
    expect(isRunning('PENDING')).toBe(true);
    expect(isRunning('CANCELLED')).toBe(false);
  });
});
