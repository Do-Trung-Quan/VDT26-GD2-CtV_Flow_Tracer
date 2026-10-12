import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ScanToast } from './scan-toast';
import { ScanTracker } from './scan-tracker';

const state = (el: Element) => [...el.classList].find((c) => c !== 'stg');

describe('ScanToast', () => {
  function render(run: object, progress: object | null = null) {
    TestBed.configureTestingModule({ imports: [ScanToast], providers: [provideHttpClient(), provideHttpClientTesting()] });
    TestBed.inject(ScanTracker).current.set({
      workspaceId: 'w1',
      workspaceName: 'Shop',
      run,
      progress: progress as never,
      minimized: false,
      cancelling: false
    });
    const fixture = TestBed.createComponent(ScanToast);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('đang chạy: giai đoạn hiện tại theo sự kiện SSE, có nút hủy', () => {
    const el = render(
      { id: 'r1', status: 'RUNNING' },
      { stage: 'EXTRACT', stageIndex: 3, stageCount: 6, processed: 5, total: 10, message: 'Đã xử lý 5/10 file' }
    );
    expect(el.querySelector('.ttl')?.textContent).toContain('Đang quét Shop');
    expect([...el.querySelectorAll('.stg')].map((s) => state(s))).toEqual([
      'done', 'done', 'cur', 'todo', 'todo', 'todo'
    ]);
    expect(el.textContent).toContain('5/10');
    expect(el.textContent).toContain('Hủy quét');
  });

  it('xong mà không có thay đổi: giai đoạn không chạy hiện "bỏ qua", thời gian lấy từ Engine', () => {
    const el = render({
      id: 'r2',
      status: 'SUCCEEDED',
      durationMs: 3100,
      stats: {
        noChanges: true,
        files: { total: 3, errors: 0 },
        stageDurations: [
          { stage: 'DISCOVER', durationMs: 2900 },
          { stage: 'DIFF', durationMs: 40 },
          { stage: 'FINALIZE', durationMs: 12 }
        ]
      }
    });
    const stages = [...el.querySelectorAll('.stg')];
    expect(stages.map((s) => state(s))).toEqual(['done', 'done', 'skipped', 'skipped', 'skipped', 'done']);
    expect(stages[2].textContent).toContain('bỏ qua');
    expect(stages[0].textContent).toContain('2,9 giây');
    expect(el.textContent).toContain('Không có file nào thay đổi');
    expect(el.textContent).not.toContain('Hủy quét');
  });

  it('có file lỗi: hiện liên kết xem file lỗi với đúng số lượng', () => {
    const el = render({
      id: 'r3',
      status: 'PARTIAL',
      durationMs: 9000,
      stats: { noChanges: false, endpoints: 10, files: { total: 6, added: 6, modified: 0, removed: 0, errors: 1 }, stageDurations: [] }
    });
    expect(el.textContent).toContain('Xem 1 file lỗi');
    expect(el.textContent).toContain('10 endpoint');
  });
});
