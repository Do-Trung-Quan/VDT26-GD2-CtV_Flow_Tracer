import { ChangeDetectionStrategy, Component, DestroyRef, HostListener, OnInit, computed, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import type { SearchHitDto } from './core/api/api-types';
import { ExplorerNavigation } from './core/explorer-navigation';
import { Icon } from './core/ui/icon';
import { ToastService } from './core/ui/toast.service';
import { ToastHost } from './core/ui/toast-host';
import { ScanToast } from './features/scan/scan-toast';
import { WorkspaceContext } from './core/workspace-context';
import { SearchPalette } from './features/search/search-palette';
import { ImportWizard } from './features/workspaces/import-wizard';

/**
 * Khung ứng dụng: header (chọn Workspace, ô tìm kiếm ở giữa, đèn AI, nút Import), thanh icon bên trái, toast, khung
 * tiến độ quét và wizard Import dùng chung.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Icon, ToastHost, ScanToast, ImportWizard, SearchPalette],
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

  protected readonly searchHint = computed(() => {
    const current = this.ctx.workspaces().find((w) => w.id === this.ctx.currentId());
    return current ? `Tìm kiếm nhanh thành phần trên Workspace ${current.name}…` : 'Chọn một Workspace để tìm kiếm…';
  });

  ngOnInit(): void {
    void this.ctx.reload();
    this.router.events
      .pipe(
        filter((e): e is NavigationEnd => e instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((e) => this.ctx.currentId.set(/^\/w\/([^/?#]+)/.exec(e.urlAfterRedirects)?.[1] ?? null));
  }

  protected selectWorkspace(e: Event): void {
    const id = (e.target as HTMLSelectElement).value;
    void this.router.navigate(id ? ['/w', id] : ['/']);
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
