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

export type ScanRunDto = Schemas['ScanRunResponse'];
export type ScanStatsDto = Schemas['ScanStatsResponse'];
export type ScanFileErrorDto = Schemas['ScanFileErrorResponse'];
export type LastScanDto = Schemas['LastScanResponse'];
export type ScanStatus = NonNullable<ScanRunDto['status']>;
export type ScanTrigger = NonNullable<ScanRunDto['trigger']>;
export type BackendTreeDto = Schemas['BackendTreeResponse'];
export type ApiModuleDto = Schemas['ApiModuleResponse'];
export type EndpointNodeDto = Schemas['EndpointNodeResponse'];

export type TraceDto = Schemas['TraceResponse'];
export type TraceStepDto = Schemas['TraceStepResponse'];
export type TraceBlockDto = Schemas['TraceBlockResponse'];
export type TraceParticipantDto = Schemas['TraceParticipantResponse'];
export type TraceExportDto = Schemas['TraceExportResponse'];
export type ExportFormat = 'plantuml' | 'svg';

/** Kết quả phân trang dùng chung của Engine. */
export interface PageDto<T> {
  items: T[];
  total: number;
  page: number;
  limit: number;
}

/** Giai đoạn quét — khớp enum ScanStage của Engine. */
export type ScanStage = 'DISCOVER' | 'DIFF' | 'EXTRACT' | 'RESOLVE' | 'PERSIST' | 'FINALIZE';

/** Sự kiện `progress` của luồng SSE (API B2) — khớp record ScanProgressResponse của Engine (SSE không có trong OpenAPI). */
export interface ScanProgressDto {
  scanRunId: string;
  stage: ScanStage;
  stageLabel: string;
  stageIndex: number;
  stageCount: number;
  processed: number;
  total: number;
  message: string;
}

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
