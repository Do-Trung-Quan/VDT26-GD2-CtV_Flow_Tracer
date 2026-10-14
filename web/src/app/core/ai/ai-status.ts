import { Injectable, computed, inject, signal } from '@angular/core';
import { AiApi } from '../api/ai.api';
import { toApiError } from '../api/api-error';
import type { AiSettingsDto, AiTestDto } from '../api/api-types';

/** Dạng đèn AI ở header: đang kiểm tra, sẵn sàng, đạt giới hạn (429), quá tải (503), bị lỗi (lỗi khác / chưa cấu hình), tắt. */
export type AiLamp = 'checking' | 'on' | 'cooling' | 'overloaded' | 'error' | 'off';

/** Nhãn và màu đèn theo trạng thái cấu hình AI (I1). */
export function lampOf(s: Pick<AiSettingsDto, 'status'> | null): { lamp: AiLamp; label: string } {
  switch (s?.status) {
    case 'CHECKING':
      return { lamp: 'checking', label: 'AI đang kiểm tra' };
    case 'READY':
      return { lamp: 'on', label: 'AI sẵn sàng' };
    case 'COOLING':
      return { lamp: 'cooling', label: 'AI đạt giới hạn' };
    case 'OVERLOADED':
      return { lamp: 'overloaded', label: 'AI quá tải' };
    case 'ERROR':
      return { lamp: 'error', label: 'AI bị lỗi' };
    case 'NOT_CONFIGURED':
      return { lamp: 'error', label: 'AI chưa cấu hình' };
    default:
      return { lamp: 'off', label: 'AI tắt' };
  }
}

/** Giờ hết nghỉ dạng "21:05" từ mốc ISO-8601; rỗng nếu không đọc được. */
export function clockOf(iso: string | undefined | null): string {
  const d = iso ? new Date(iso) : null;
  return d && !Number.isNaN(d.getTime()) ? d.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' }) : '';
}

/**
 * Trạng thái AI dùng chung (đèn ở header, banner, nút Sinh): nạp từ I1 khi mở ứng dụng và nạp lại mỗi khi một lần gọi AI báo lỗi,
 * để đèn và banner phản ánh đúng việc provider đang nghỉ. Không bao giờ chứa khóa API.
 */
@Injectable({ providedIn: 'root' })
export class AiStatus {
  private readonly api = inject(AiApi);

  readonly settings = signal<AiSettingsDto | null>(null);
  readonly lamp = computed(() => lampOf(this.settings()));
  /** Cho phép gọi AI: sẵn sàng, hoặc lần gọi gần nhất lỗi nhưng vẫn cho thử lại (quá tải / lỗi khác). Hết hạn mức 429, tắt, chưa cấu hình thì không. */
  readonly usable = computed(() => ['CHECKING', 'READY', 'OVERLOADED', 'ERROR'].includes(this.settings()?.status ?? ''));

  /** "Kiểm tra kết nối" đang chạy (I3 mất vài giây, tối đa 15 giây) — nằm ở service để đổi trang rồi quay lại vẫn thấy tiến độ / kết quả. */
  readonly testing = signal(false);
  /** Số giây đã chờ của lần kiểm tra đang chạy. */
  readonly testElapsed = signal(0);
  /** Kết quả lần kiểm tra gần nhất kèm thời điểm; null nếu chưa kiểm tra (hoặc đang kiểm tra). */
  readonly lastTest = signal<{ result: AiTestDto; at: Date } | null>(null);
  private testTimer: ReturnType<typeof setInterval> | undefined;
  private pollTimer: ReturnType<typeof setTimeout> | undefined;

  /** I3 — gọi thử provider. Mỗi lần tốn 1 lượt trong hạn mức ngày của nhà cung cấp; lỗi 429 / 503 cập nhật luôn trạng thái nghỉ và đèn AI. */
  async runTest(): Promise<void> {
    if (this.testing()) {
      return;
    }
    const started = Date.now();
    this.testing.set(true);
    this.testElapsed.set(0);
    this.lastTest.set(null);
    this.testTimer = setInterval(() => this.testElapsed.set(Math.round((Date.now() - started) / 1000)), 500);
    let result: AiTestDto;
    try {
      result = await this.api.test();
    } catch (e) {
      result = { ok: false, latencyMs: Date.now() - started, message: toApiError(e).message };
    } finally {
      clearInterval(this.testTimer);
      this.testTimer = undefined;
      this.testing.set(false);
    }
    await this.refresh();
    this.lastTest.set({ result, at: new Date() });
  }

  /**
   * Tải lại I1; lỗi mạng thì giữ trạng thái cũ (không làm hỏng màn hình đang xem). Engine đang tự kiểm tra kết nối khi khởi động
   * (`CHECKING`) thì hỏi lại sau vài giây tới khi có kết quả.
   */
  async refresh(): Promise<void> {
    try {
      this.settings.set(await this.api.settings());
    } catch {
      /* Engine chưa chạy: các khung khác đã báo */
    }
    clearTimeout(this.pollTimer);
    if (this.settings()?.status === 'CHECKING') {
      this.pollTimer = setTimeout(() => void this.refresh(), 2000);
    }
  }

  /** Mục đích có đang bật trong I2 không (chưa nạp xong thì coi như bật để không ẩn nút một cách nhầm). */
  purposeEnabled(purpose: string): boolean {
    const p = this.settings()?.purposes?.find((x) => x.purpose === purpose);
    return p ? !!p.enabled : true;
  }
}
