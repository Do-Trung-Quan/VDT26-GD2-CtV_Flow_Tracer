import type { OpenApiCheckDto, OpenApiOperationDto } from '../../core/api/api-types';

/** Một dòng của bảng kết quả: API chỉ có ở một phía kèm gợi ý. */
export interface OpenApiRow {
  side: 'CODE' | 'SPEC';
  pill: string;
  pillClass: string;
  api: string;
  hint: string;
}

const CODE_ONLY_HINT = 'Tài liệu Swagger có thể đã lỗi thời — nên bổ sung.';
const SPEC_ONLY_HINT = 'Tool có thể đã bỏ sót API này — kiểm tra bộ lọc hoặc thư mục loại trừ.';

function apiText(o: OpenApiOperationDto): string {
  return `${o.method ?? ''} ${o.path ?? ''}`.trim();
}

/** Các dòng lệch nhau giữa mã nguồn và tài liệu: API chỉ có trong mã trước, rồi API chỉ có trong Swagger. */
export function openApiRows(result: OpenApiCheckDto): OpenApiRow[] {
  const inCode = (result.onlyInCode ?? []).map<OpenApiRow>((o) => ({
    side: 'CODE',
    pill: 'Chỉ trong mã',
    pillClass: 'p-amber',
    api: apiText(o),
    hint: CODE_ONLY_HINT
  }));
  const inSpec = (result.onlyInSpec ?? []).map<OpenApiRow>((o) => ({
    side: 'SPEC',
    pill: 'Chỉ trong Swagger',
    pillClass: 'p-red',
    api: apiText(o),
    hint: SPEC_ONLY_HINT
  }));
  return [...inCode, ...inSpec];
}
