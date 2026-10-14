import { ChangeDetectionStrategy, Component, DestroyRef, HostListener, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import type { SearchHitDto } from './core/api/api-types';
import { AiStatus } from './core/ai/ai-status';
import { ExplorerNavigation } from './core/explorer-navigation';
import { Icon } from './core/ui/icon';
import { MenuAction, SelectMenu, SelectOption } from './core/ui/select-menu';
import { ToastService } from './core/ui/toast.service';
import { ToastHost } from './core/ui/toast-host';
import { ScanToast } from './features/scan/scan-toast';
import { WorkspaceContext } from './core/workspace-context';
import { SearchPalette } from './features/search/search-palette';
import { ImportWizard } from './features/workspaces/import-wizard';

/** Khóa lưu trạng thái mở rộng / thu gọn của menu bên trái (tiện ích hiển thị trong trình duyệt). */
const RAIL_KEY = 'ft.rail.wide';

function readRailWide(): boolean {
  try {
    return localStorage.getItem(RAIL_KEY) === '1';
  } catch {
    return false;
  }
}

/**
 * Khung ứng dụng: header (nút 3 gạch mở / thu menu, logo, chọn Workspace, ô tìm kiếm ở giữa, đèn AI, nút Import), menu bên trái
 * (thu gọn chỉ icon / mở rộng có tên), toast, khung tiến độ quét và wizard Import dùng chung.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Icon, SelectMenu, ToastHost, ScanToast, ImportWizard, SearchPalette],
  templateUrl: './app.html',
  styleUrl: './app.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class App implements OnInit {
  protected readonly ctx = inject(WorkspaceContext);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly toast = inject(ToastService);
  private readonly navigation = inject(ExplorerNavigation);
  protected readonly ai = inject(AiStatus);

  /** Menu bên trái đang mở rộng (hiện tên) hay thu gọn (chỉ icon). */
  protected readonly railWide = signal(readRailWide());
  protected readonly workspaceOptions = computed<SelectOption[]>(() => this.ctx.workspaces().map((w) => ({ value: w.id ?? '', label: w.name ?? '' })));
  protected readonly workspaceActions: MenuAction[] = [{ id: 'list', label: 'Danh sách Workspace', icon: 'home' }];

  protected readonly searchHint = computed(() => {
    const current = this.ctx.workspaces().find((w) => w.id === this.ctx.currentId());
    return current ? `Tìm kiếm nhanh thành phần trên Workspace ${current.name}…` : 'Chọn một Workspace để tìm kiếm…';
  });

  protected readonly aiTitle = computed(() => {
    const s = this.ai.settings();
    return s?.statusMessage ? `Cấu hình AI — ${s.statusMessage}` : 'Cấu hình AI';
  });

  ngOnInit(): void {
    void this.ctx.reload();
    void this.ai.refresh();
    this.router.events
      .pipe(
        filter((e): e is NavigationEnd => e instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((e) => this.ctx.currentId.set(/^\/w\/([^/?#]+)/.exec(e.urlAfterRedirects)?.[1] ?? null));
  }

  protected toggleRail(): void {
    const next = !this.railWide();
    this.railWide.set(next);
    try {
      localStorage.setItem(RAIL_KEY, next ? '1' : '0');
    } catch {
      // trình duyệt chặn lưu trữ: chỉ mất việc ghi nhớ
    }
  }

  protected selectWorkspace(id: string): void {
    void this.router.navigate(id ? ['/w', id] : ['/']);
  }

  protected onWorkspaceAction(id: string): void {
    if (id === 'list') {
      void this.router.navigate(['/']);
    }
  }

  /** Ctrl+K (hoặc Cmd+K) mở bảng tìm nhanh, kể cả khi đang gõ trong một ô nhập. */
  @HostListener('document:keydown', ['$event'])
  protected onKeydown(e: KeyboardEvent): void {
    if ((e.ctrlKey || e.metaKey) && !e.shiftKey && !e.altKey && e.key.toLowerCase() === 'k') {
      e.preventDefault();
      this.openPalette();
    }
  }

  protected openPalette(): void {
    if (!this.ctx.currentId()) {
      this.toast.show('Hãy chọn một Workspace trước khi tìm kiếm.', 'warn');
      return;
    }
    this.ctx.paletteOpen.set(true);
  }

  protected openHit(workspaceId: string, hit: SearchHitDto): void {
    this.ctx.paletteOpen.set(false);
    void this.navigation.open(workspaceId, hit);
  }
}
