import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { Icon } from './core/ui/icon';
import { ToastHost } from './core/ui/toast-host';
import { ScanToast } from './features/scan/scan-toast';
import { WorkspaceContext } from './core/workspace-context';
import { ImportWizard } from './features/workspaces/import-wizard';

/**
 * Khung ứng dụng: header (chọn Workspace, ô tìm kiếm ở giữa, đèn AI, nút Import), thanh icon bên trái, toast, khung
 * tiến độ quét và wizard Import dùng chung.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Icon, ToastHost, ScanToast, ImportWizard],
  templateUrl: './app.html',
  styleUrl: './app.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class App implements OnInit {
  protected readonly ctx = inject(WorkspaceContext);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

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
}
