import { Routes } from '@angular/router';

/** Các màn hình theo overview §2.6. Mỗi trang hiện chỉ là chỗ giữ chỗ. */
export const routes: Routes = [
  { path: '', loadComponent: () => import('./features/workspaces/workspace-list.page').then((m) => m.WorkspaceListPage) },
  { path: 'w/:workspaceId', loadComponent: () => import('./features/explorer/explorer.page').then((m) => m.ExplorerPage) },
  {
    path: 'w/:workspaceId/settings',
    loadComponent: () => import('./features/workspaces/workspace-settings.page').then((m) => m.WorkspaceSettingsPage)
  },
  { path: 'settings/ai', loadComponent: () => import('./features/ai-settings/ai-settings.page').then((m) => m.AiSettingsPage) },
  { path: '**', redirectTo: '' }
];
