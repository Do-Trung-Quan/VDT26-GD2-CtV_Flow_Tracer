import { Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import type { SearchHitDto } from './api/api-types';

/** Yêu cầu mở một thành phần trên màn hình chính (từ bảng lệnh Ctrl+K). `seq` tăng mỗi lần để mở lại cùng một thành phần. */
export interface NavigationRequest {
  seq: number;
  workspaceId: string;
  hit: SearchHitDto;
}

/** Cầu nối giữa bảng lệnh (ở khung ứng dụng) và màn hình chính: bảng lệnh đặt yêu cầu, màn hình chính xử lý rồi xóa. */
@Injectable({ providedIn: 'root' })
export class ExplorerNavigation {
  private readonly router = inject(Router);
  private seq = 0;

  readonly request = signal<NavigationRequest | null>(null);

  /** Mở thành phần; đang ở trang khác của Workspace thì chuyển về màn hình chính trước. */
  async open(workspaceId: string, hit: SearchHitDto): Promise<void> {
    const onExplorer = this.router.url.split(/[?#]/)[0] === `/w/${workspaceId}`;
    if (!onExplorer) {
      await this.router.navigate(['/w', workspaceId]);
    }
    this.request.set({ seq: ++this.seq, workspaceId, hit });
  }

  consume(): void {
    this.request.set(null);
  }
}
