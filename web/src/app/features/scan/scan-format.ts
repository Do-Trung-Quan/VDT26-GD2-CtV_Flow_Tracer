import type { ScanStage, ScanStatus, ScanTrigger } from '../../core/api/api-types';

/** 6 giai đoạn của lần quét, theo thứ tự — khớp enum ScanStage của Engine. */
export const SCAN_STAGES: readonly { id: ScanStage; label: string }[] = [
  { id: 'DISCOVER', label: 'Liệt kê file' },
  { id: 'DIFF', label: 'So sánh thay đổi' },
  { id: 'EXTRACT', label: 'Đọc & trích xuất' },
  { id: 'RESOLVE', label: 'Ghép kết quả' },
  { id: 'PERSIST', label: 'Lưu dữ liệu' },
  { id: 'FINALIZE', label: 'Hoàn tất' }
];

const STATUS_LABEL: Record<ScanStatus, string> = {
  PENDING: 'Đang chờ',
  RUNNING: 'Đang quét',
  SUCCEEDED: 'Thành công',
  PARTIAL: 'Thành công một phần',
  FAILED: 'Thất bại',
  CANCELLED: 'Đã hủy'
};

const STATUS_PILL: Record<ScanStatus, string> = {
  PENDING: 'p-gray',
  RUNNING: 'p-blue',
  SUCCEEDED: 'p-green',
  PARTIAL: 'p-amber',
  FAILED: 'p-red',
  CANCELLED: 'p-gray'
};

export function statusLabel(status: ScanStatus | undefined): string {
  return status ? STATUS_LABEL[status] : 'Chưa quét';
}

export function statusPill(status: ScanStatus | undefined): string {
  return status ? STATUS_PILL[status] : 'p-gray';
}

export function triggerLabel(trigger: ScanTrigger | undefined): string {
  return trigger === 'IMPORT' ? 'Quét toàn bộ' : 'Quét lại';
}

export function isRunning(status: ScanStatus | undefined): boolean {
  return status === 'PENDING' || status === 'RUNNING';
}

/** 1234 → "1,2 giây"; 65000 → "1 phút 5 giây". */
export function formatDuration(ms: number | undefined | null): string {
  if (ms === undefined || ms === null) {
    return '—';
  }
  if (ms < 1000) {
    return `${ms} ms`;
  }
  const seconds = ms / 1000;
  if (seconds < 60) {
    return `${seconds.toFixed(1).replace('.', ',')} giây`;
  }
  const minutes = Math.floor(seconds / 60);
  return `${minutes} phút ${Math.round(seconds - minutes * 60)} giây`;
}

/**
 * Phần đã xong của lần quét (0..1), tính từ giai đoạn hiện tại và số mục đã xử lý trong giai đoạn đó — không ước lượng
 * theo thời gian.
 */
export function progressFraction(stageIndex: number, stageCount: number, processed: number, total: number): number {
  if (stageCount <= 0) {
    return 0;
  }
  const inStage = total > 0 ? Math.min(1, processed / total) : 0;
  return Math.min(1, (stageIndex - 1 + inStage) / stageCount);
}
