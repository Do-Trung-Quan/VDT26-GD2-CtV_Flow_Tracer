import { Injectable, inject, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { toApiError } from '../../core/api/api-error';
import type { ScanProgressDto, ScanRunDto, ScanTrigger } from '../../core/api/api-types';
import { ScanApi } from '../../core/api/scan.api';
import { ToastService } from '../../core/ui/toast.service';
import { WorkspaceContext } from '../../core/workspace-context';
import { isRunning, statusLabel } from './scan-format';

/** Lần quét đang được theo dõi trên giao diện. */
export interface TrackedScan {
  workspaceId: string;
  workspaceName: string;
  run: ScanRunDto;
  progress: ScanProgressDto | null;
  minimized: boolean;
  cancelling: boolean;
}

/** Sự kiện "một lần quét vừa kết thúc" — trang cây / lịch sử dùng để tải lại dữ liệu. */
export interface ScanFinished {
  workspaceId: string;
  scanRunId: string;
}

const POLL_MS = 1000;

/**
 * Theo dõi một lần quét: bắt đầu (B1), nhận tiến độ qua SSE (B2), hủy (B4). Mất kết nối SSE thì hỏi trạng thái định kỳ
 * qua B3 cho tới khi lần quét kết thúc.
 */
@Injectable({ providedIn: 'root' })
export class ScanTracker {
  private readonly api = inject(ScanApi);
  private readonly toast = inject(ToastService);
  private readonly ctx = inject(WorkspaceContext);

  readonly current = signal<TrackedScan | null>(null);
  readonly finished = signal<ScanFinished | null>(null);

  private subscription: Subscription | null = null;
  private pollTimer: ReturnType<typeof setInterval> | null = null;

  /** Bắt đầu quét; báo lỗi (vd 409 đang quét) bằng toast. */
  async start(workspaceId: string, workspaceName: string, trigger: ScanTrigger): Promise<void> {
    try {
      const run = await this.api.start(workspaceId, trigger);
      this.track(workspaceId, workspaceName, run);
      void this.ctx.reload();
    } catch (e) {
      this.toast.show(toApiError(e).message, 'err');
    }
  }

  /** Theo dõi tiếp một lần quét đang chạy (vd mở lại trang khi quét chưa xong). */
  resume(workspaceId: string, workspaceName: string, run: ScanRunDto): void {
    if (this.current()?.run.id === run.id) {
      return;
    }
    this.track(workspaceId, workspaceName, run);
  }

  async cancel(): Promise<void> {
    const scan = this.current();
    if (!scan || !isRunning(scan.run.status)) {
      return;
    }
    this.patch({ cancelling: true });
    try {
      this.finish(await this.api.cancel(scan.run.id!));
    } catch (e) {
      this.patch({ cancelling: false });
      this.toast.show(toApiError(e).message, 'err');
    }
  }

  toggleMinimized(): void {
    const scan = this.current();
    if (scan) {
      this.patch({ minimized: !scan.minimized });
    }
  }

  /** Đóng khung tiến độ — chỉ khi lần quét đã kết thúc. */
  dismiss(): void {
    if (!isRunning(this.current()?.run.status)) {
      this.stopListening();
      this.current.set(null);
    }
  }

  private track(workspaceId: string, workspaceName: string, run: ScanRunDto): void {
    this.stopListening();
    this.current.set({ workspaceId, workspaceName, run, progress: null, minimized: false, cancelling: false });
    this.subscription = this.api.events(run.id!).subscribe({
      next: (event) => (event.type === 'progress' ? this.patch({ progress: event.data }) : this.finish(event.data)),
      error: () => this.poll(run.id!)
    });
  }

  /** SSE mất kết nối: hỏi trạng thái qua B3 cho tới khi xong. */
  private poll(scanRunId: string): void {
    this.subscription = null;
    this.pollTimer = setInterval(async () => {
      try {
        const run = await this.api.get(scanRunId);
        if (!isRunning(run.status)) {
          this.finish(run);
        }
      } catch {
        // thử lại ở lần sau
      }
    }, POLL_MS);
  }

  private finish(run: ScanRunDto): void {
    const scan = this.current();
    if (!scan || scan.run.id !== run.id || !isRunning(scan.run.status)) {
      return;
    }
    this.stopListening();
    this.patch({ run, cancelling: false, minimized: false });
    this.finished.set({ workspaceId: scan.workspaceId, scanRunId: run.id! });
    void this.ctx.reload();
    const kind = run.status === 'SUCCEEDED' ? 'ok' : run.status === 'PARTIAL' || run.status === 'CANCELLED' ? 'warn' : 'err';
    this.toast.show(`${scan.workspaceName}: ${statusLabel(run.status)}.`, kind);
  }

  private patch(change: Partial<TrackedScan>): void {
    const scan = this.current();
    if (scan) {
      this.current.set({ ...scan, ...change });
    }
  }

  private stopListening(): void {
    this.subscription?.unsubscribe();
    this.subscription = null;
    if (this.pollTimer) {
      clearInterval(this.pollTimer);
      this.pollTimer = null;
    }
  }
}
