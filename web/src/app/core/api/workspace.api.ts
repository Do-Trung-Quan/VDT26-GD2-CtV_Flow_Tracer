import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, firstValueFrom, map } from 'rxjs';
import type {
  ApiEnvelope,
  CreateSourceRequest,
  CreateWorkspaceRequest,
  DetectionDto,
  FolderBrowseDto,
  NoiseFiltersDto,
  SourceDto,
  UpdateNoiseFiltersRequest,
  UpdateSourceRequest,
  UpdateWorkspaceRequest,
  WorkspaceDto
} from './api-types';

/** Lấy `data` trong vỏ bọc ApiResponse của Engine. */
function unwrap<T>(response: Observable<ApiEnvelope<T>>): Promise<T> {
  return firstValueFrom(response.pipe(map((r) => r.data)));
}

/** Nhóm API A (Workspace & nguồn mã) và I4–I5 (bộ lọc nhiễu) — xem .context/API_list.md. */
@Injectable({ providedIn: 'root' })
export class WorkspaceApi {
  private readonly http = inject(HttpClient);

  /** A1 */
  list(): Promise<WorkspaceDto[]> {
    return unwrap(this.http.get<ApiEnvelope<WorkspaceDto[]>>('/api/workspaces'));
  }

  /** A2 */
  create(body: CreateWorkspaceRequest): Promise<WorkspaceDto> {
    return unwrap(this.http.post<ApiEnvelope<WorkspaceDto>>('/api/workspaces', body));
  }

  /** A3 */
  get(id: string): Promise<WorkspaceDto> {
    return unwrap(this.http.get<ApiEnvelope<WorkspaceDto>>(`/api/workspaces/${id}`));
  }

  /** A4 */
  update(id: string, body: UpdateWorkspaceRequest): Promise<WorkspaceDto> {
    return unwrap(this.http.patch<ApiEnvelope<WorkspaceDto>>(`/api/workspaces/${id}`, body));
  }

  /** A5 */
  delete(id: string): Promise<void> {
    return unwrap(this.http.delete<ApiEnvelope<void>>(`/api/workspaces/${id}`));
  }

  /** A6 */
  browse(path: string): Promise<FolderBrowseDto> {
    return unwrap(this.http.get<ApiEnvelope<FolderBrowseDto>>('/api/fs/browse', { params: { path } }));
  }

  /** A7 */
  addSource(id: string, body: CreateSourceRequest): Promise<SourceDto[]> {
    return unwrap(this.http.post<ApiEnvelope<SourceDto[]>>(`/api/workspaces/${id}/sources`, body));
  }

  /** A8 */
  updateSource(id: string, sourceId: string, body: UpdateSourceRequest): Promise<SourceDto> {
    return unwrap(this.http.patch<ApiEnvelope<SourceDto>>(`/api/workspaces/${id}/sources/${sourceId}`, body));
  }

  /** A9 */
  deleteSource(id: string, sourceId: string): Promise<void> {
    return unwrap(this.http.delete<ApiEnvelope<void>>(`/api/workspaces/${id}/sources/${sourceId}`));
  }

  /** A10 */
  detect(rootPath: string): Promise<DetectionDto> {
    return unwrap(this.http.post<ApiEnvelope<DetectionDto>>('/api/fs/detect', { rootPath }));
  }

  /** I4 */
  noiseFilters(id: string): Promise<NoiseFiltersDto> {
    return unwrap(this.http.get<ApiEnvelope<NoiseFiltersDto>>(`/api/workspaces/${id}/noise-filters`));
  }

  /** I5 */
  updateNoiseFilters(id: string, body: UpdateNoiseFiltersRequest): Promise<NoiseFiltersDto> {
    return unwrap(this.http.put<ApiEnvelope<NoiseFiltersDto>>(`/api/workspaces/${id}/noise-filters`, body));
  }
}
