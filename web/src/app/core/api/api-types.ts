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
export type FrontendTreeDto = Schemas['FrontendTreeResponse'];
export type UiTreeNodeDto = Schemas['UiTreeNodeResponse'];
export type SearchDto = Schemas['SearchResponse'];
export type SearchGroupDto = Schemas['SearchGroupResponse'];
export type SearchHitDto = Schemas['SearchHitResponse'];
/** Nhóm tìm kiếm (API C3) — khớp enum SearchCategory của Engine. */
export type SearchCategory = 'API' | 'SCREEN' | 'ELEMENT' | 'TABLE' | 'CODE';

export type TraceDto = Schemas['TraceResponse'];
export type TraceStepDto = Schemas['TraceStepResponse'];
export type TraceBlockDto = Schemas['TraceBlockResponse'];
export type TraceParticipantDto = Schemas['TraceParticipantResponse'];
export type TraceExportDto = Schemas['TraceExportResponse'];
export type ExportFormat = 'plantuml' | 'svg';

export type EdgeDetailDto = Schemas['EdgeDetailResponse'];
export type EdgeNodeDto = Schemas['EdgeNodeResponse'];
export type EdgeCandidateDto = Schemas['EdgeCandidateResponse'];
export type LinkOverrideDto = Schemas['LinkOverrideResponse'];
export type CreateLinkOverrideRequest = Schemas['CreateLinkOverrideRequest'];
export type LinkRulesDto = Schemas['LinkRulesResponse'];
export type LinkRuleDto = Schemas['LinkRuleResponse'];
export type LinkRuleType = NonNullable<LinkRuleDto['type']>;
export type UpdateLinkRulesRequest = Schemas['UpdateLinkRulesRequest'];

export type ExplanationDto = Schemas['ExplanationResponse'];
export type PurposeDto = Schemas['PurposeResponse'];
export type ApiIoDto = Schemas['ApiIoResponse'];
export type NodeRelationDto = Schemas['NodeRelationResponse'];
export type NodeDetailDto = Schemas['NodeDetailResponse'];
export type MockDataDto = Schemas['MockDataResponse'];
export type ScenarioDto = Schemas['ScenarioResponse'];
export type ScenarioActorDto = Schemas['ActorResponse'];
export type ScenarioItemDto = Schemas['ItemResponse'];
export type CodeTabDto = Schemas['CodeTabResponse'];
export type OpenApiCheckDto = Schemas['OpenApiCheckResponse'];
export type OpenApiOperationDto = Schemas['OpenApiOperationResponse'];

export type AiSettingsDto = Schemas['AiSettingsResponse'];
export type AiPurposeSettingDto = Schemas['AiPurposeSettingResponse'];
export type AiTestDto = Schemas['AiTestResponse'];
export type AiUsageDto = Schemas['AiUsageResponse'];
export type AiUsageByPurposeDto = Schemas['ByPurpose'];
export type AiInfoDto = Schemas['AiInfoResponse'];
export type AiGenerationDto = Schemas['AiGenerationResponse'];
export type GenerateAiRequest = Schemas['GenerateAiRequest'];
export type AiPurposeName = GenerateAiRequest['purpose'];
export type EdgeAdviceDto = Schemas['EdgeAdviceResponse'];
export type CandidateAdviceDto = Schemas['CandidateAdvice'];

/** Kết quả phân trang dùng chung của Engine. */
export interface PageDto<T> {
  items: T[];
  total: number;
  page: number;
  limit: number;
}

/** Giai đoạn quét — khớp enum ScanStage của Engine. */
export type ScanStage = 'DISCOVER' | 'DIFF' | 'EXTRACT' | 'RESOLVE' | 'PERSIST' | 'LINK' | 'FINALIZE';

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
