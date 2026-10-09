import type { components } from './generated/schema';

/** Tên ngắn cho các kiểu sinh từ OpenAPI của Engine (npm run gen:api). */
type Schemas = components['schemas'];

export type WorkspaceDto = Schemas['WorkspaceResponse'];
export type SourceDto = Schemas['SourceResponse'];
export type CreateWorkspaceRequest = Schemas['CreateWorkspaceRequest'];
export type UpdateWorkspaceRequest = Schemas['UpdateWorkspaceRequest'];
export type CreateSourceRequest = Schemas['CreateSourceRequest'];
export type UpdateSourceRequest = Schemas['UpdateSourceRequest'];
export type DetectionDto = Schemas['DetectionResponse'];
export type DetectedRootDto = Schemas['DetectedRootResponse'];
export type FolderBrowseDto = Schemas['FolderBrowseResponse'];
export type NoiseFiltersDto = Schemas['NoiseFiltersResponse'];
export type UpdateNoiseFiltersRequest = Schemas['UpdateNoiseFiltersRequest'];
export type SourceRole = NonNullable<SourceDto['role']>;

/** Vỏ bọc chuẩn của mọi response REST: dữ liệu nằm trong `data`, lỗi có `code` và `errors`. */
export interface ApiEnvelope<T> {
  status: number;
  code: string;
  message: string;
  data: T;
  errors?: Record<string, unknown>;
}

/** Lỗi đã đọc từ vỏ bọc. */
export interface ApiError {
  status: number;
  code: string;
  message: string;
  /** Trường sai → lý do, hoặc ngữ cảnh lỗi (vd `suggestedRoot`). */
  errors: Record<string, unknown>;
}
