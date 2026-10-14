/** Khoảng thời gian của thống kê: 1 ngày (hôm nay), 7 ngày gần nhất hoặc toàn bộ nhật ký. */
export type UsageRange = 1 | 7 | 'all';

/** Mốc bắt đầu của "Tất cả": sớm hơn mọi dữ liệu có thể có. */
export const ALL_SINCE = '2000-01-01';

/** Số theo kiểu Việt Nam: 412.300. */
export function vnNumber(n: number | undefined | null): string {
  return (n ?? 0).toLocaleString('vi-VN');
}

/** Chiều cao cột biểu đồ (px, tối đa 100) theo cột lớn nhất; cột có số liệu luôn cao tối thiểu 4px. */
export function barHeight(value: number, all: number[]): number {
  const max = Math.max(0, ...all);
  return max > 0 && value > 0 ? Math.max(4, Math.round((value / max) * 100)) : 0;
}

function pad(n: number): string {
  return String(n).padStart(2, '0');
}

/** `yyyy-MM-dd` theo giờ máy. */
export function isoDate(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** Khoảng `days` ngày gần nhất tính cả hôm nay. */
export function dateRange(days: number, today: Date = new Date()): { from: string; to: string } {
  const from = new Date(today);
  from.setDate(from.getDate() - (days - 1));
  return { from: isoDate(from), to: isoDate(today) };
}

/** Khoảng ngày gửi cho API thống kê theo lựa chọn trên giao diện. */
export function rangeDates(range: UsageRange, today: Date = new Date()): { from: string; to: string } {
  return range === 'all' ? { from: ALL_SINCE, to: isoDate(today) } : dateRange(range, today);
}
