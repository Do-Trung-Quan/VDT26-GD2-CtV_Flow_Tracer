import { TestBed } from '@angular/core/testing';
import { describe, expect, it, vi } from 'vitest';
import { AiApi } from '../api/ai.api';
import { AiStatus, clockOf, lampOf } from './ai-status';

function setup(test: () => Promise<unknown>) {
  const api = {
    settings: vi.fn(async () => ({ provider: 'gemini', status: 'READY', purposes: [] })),
    test: vi.fn(test)
  };
  TestBed.configureTestingModule({ providers: [{ provide: AiApi, useValue: api }] });
  return { api, status: TestBed.inject(AiStatus) };
}

describe('AiStatus.runTest', () => {
  it('đặt testing trong lúc chờ, giữ kết quả kèm thời điểm và nạp lại trạng thái', async () => {
    const { api, status } = setup(async () => ({ ok: true, latencyMs: 1200, message: 'ok' }));
    const running = status.runTest();
    expect(status.testing()).toBe(true);
    expect(status.lastTest()).toBeNull();
    await running;
    expect(status.testing()).toBe(false);
    expect(status.lastTest()?.result.ok).toBe(true);
    expect(status.lastTest()?.at).toBeInstanceOf(Date);
    expect(api.settings).toHaveBeenCalledTimes(1);
  });

  it('lỗi mạng / 503 từ Engine vẫn thành kết quả thất bại có thông điệp, không ném lỗi', async () => {
    const { status } = setup(async () => {
      throw new Error('Engine không phản hồi');
    });
    await status.runTest();
    expect(status.lastTest()?.result.ok).toBe(false);
    expect(status.lastTest()?.result.message).toContain('Engine không phản hồi');
    expect(status.testing()).toBe(false);
  });

  it('bấm lần nữa khi đang chạy thì bỏ qua (không bắn thêm lượt gọi AI)', async () => {
    const { api, status } = setup(() => new Promise((r) => setTimeout(() => r({ ok: true, latencyMs: 1, message: '' }), 20)));
    const first = status.runTest();
    await status.runTest();
    await first;
    expect(api.test).toHaveBeenCalledTimes(1);
  });
});

describe('đèn AI và giờ thử lại', () => {
  it('mỗi loại lỗi một nhãn: 429 đạt giới hạn, 503 quá tải, lỗi khác bị lỗi, sẵn sàng, tắt', () => {
    expect(lampOf({ status: 'COOLING' })).toEqual({ lamp: 'cooling', label: 'AI đạt giới hạn' });
    expect(lampOf({ status: 'OVERLOADED' })).toEqual({ lamp: 'overloaded', label: 'AI quá tải' });
    expect(lampOf({ status: 'ERROR' })).toEqual({ lamp: 'error', label: 'AI bị lỗi' });
    expect(lampOf({ status: 'NOT_CONFIGURED' }).label).toBe('AI chưa cấu hình');
    expect(lampOf({ status: 'READY' }).label).toBe('AI sẵn sàng');
    expect(lampOf(null).label).toBe('AI tắt');
  });

  it('chỉ chặn gọi AI khi hết hạn mức; quá tải / lỗi khác vẫn cho thử lại', async () => {
    const { status } = setup(async () => ({ ok: true }));
    for (const [s, usable] of [['READY', true], ['OVERLOADED', true], ['ERROR', true], ['COOLING', false], ['OFF', false], ['NOT_CONFIGURED', false]] as const) {
      status.settings.set({ status: s });
      expect(status.usable()).toBe(usable);
    }
  });

  it('clockOf bỏ qua giá trị không đọc được', () => {
    expect(clockOf(undefined)).toBe('');
    expect(clockOf('không phải ngày')).toBe('');
  });
});

describe('AiStatus.refresh khi Engine đang tự kiểm tra kết nối', () => {
  it('đèn "đang kiểm tra" vẫn cho gọi AI và tự hỏi lại tới khi có kết quả', async () => {
    vi.useFakeTimers();
    try {
      const answers = [{ status: 'CHECKING' }, { status: 'READY' }];
      const api = { settings: vi.fn(async () => answers.shift() ?? { status: 'READY' }) };
      TestBed.configureTestingModule({ providers: [{ provide: AiApi, useValue: api }] });
      const status = TestBed.inject(AiStatus);

      await status.refresh();
      expect(lampOf(status.settings()).lamp).toBe('checking');
      expect(status.usable()).toBe(true);

      await vi.advanceTimersByTimeAsync(2100);
      expect(api.settings).toHaveBeenCalledTimes(2);
      expect(lampOf(status.settings()).lamp).toBe('on');
    } finally {
      vi.useRealTimers();
    }
  });
});
