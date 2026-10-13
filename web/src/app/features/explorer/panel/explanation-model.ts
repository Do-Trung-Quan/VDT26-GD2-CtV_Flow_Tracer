import type { ApiIoDto, CodeTabDto, NodeRelationDto, ScenarioItemDto } from '../../../core/api/api-types';
import { fullLabel } from '../frontend-tree-model';

/** Loại node (kind của Engine) có thể mở trên màn hình chính; loại khác chỉ hiện chữ. */
export function isOpenable(kind: string | undefined): boolean {
  return kind === 'API_ENDPOINT' || (kind?.startsWith('UI_') ?? false);
}

/** Đầu vào của API ở dạng hiển thị: đủ các phần có dữ liệu (`path`, `query`, `body`) như bản vẽ; null nếu API không nhận gì. */
export function inputValue(api: ApiIoDto | undefined): Record<string, unknown> | null {
  const input = api?.input;
  return input && Object.keys(input).length > 0 ? input : null;
}

/** Đầu ra hiển thị; null nếu API không trả nội dung. */
export function outputValue(api: ApiIoDto | undefined): Record<string, unknown> | null {
  const output = api?.output;
  return output && Object.keys(output).length > 0 ? output : null;
}

/** JSON thụt 2 dấu cách, dùng cho khối mã và nút Sao chép. */
export function jsonText(value: unknown): string {
  return JSON.stringify(value, null, 2);
}

function escapeHtml(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

/** Tô màu cú pháp JSON như bản vẽ: khóa xanh dương, chuỗi xanh lá, số vàng, true/false/null hồng. Kết quả đã thoát HTML. */
export function highlightJson(text: string): string {
  return escapeHtml(text).replace(
    /(&quot;(?:\\\\|\\&quot;|(?!&quot;).)*&quot;)(\s*:)?|\b(-?\d+(?:\.\d+)?)\b|\b(true|false|null)\b/g,
    (_m, str?: string, colon?: string, num?: string, bool?: string) =>
      str ? (colon ? `<span class="jk">${str}</span>${colon}` : `<span class="js">${str}</span>`) : num ? `<span class="jn">${num}</span>` : `<span class="jb">${bool}</span>`
  );
}

/** Khóa API → nhãn ngắn "POST /api/v1/categories". */
export function apiLabel(api: ApiIoDto): string {
  return `${api.method ?? ''} ${api.path ?? ''}`.trim();
}

/** Một dòng của danh sách kịch bản đã chuẩn hóa. */
export interface ScenarioRow {
  /** Số bước = số mũi tên; null với dòng điều kiện. */
  index: number | null;
  text: string;
  depth: number;
  unresolved: boolean;
}

export function scenarioRows(items: ScenarioItemDto[] | undefined): ScenarioRow[] {
  return (items ?? []).map((i) => ({
    index: i.index ?? null,
    text: i.text ?? '',
    depth: i.depth ?? 0,
    unresolved: i.unresolved ?? false
  }));
}

/** Tên hiển thị của quan hệ: thành phần giao diện đặt nhãn như trên cây Frontend ("Nút Lưu", "Màn hình Danh mục"). */
export function relationName(r: NodeRelationDto): string {
  if (r.kind?.startsWith('UI_')) {
    return fullLabel({
      kind: r.kind,
      name: r.name,
      displayName: r.displayName,
      elementType: r.elementType,
      fallbackName: r.fallbackName,
      routePath: r.routePath
    });
  }
  return r.displayName || r.name || r.nodeKey || '';
}

/** Một dòng của khung xem code: số dòng trong file, nội dung và có được tô sáng không. */
export interface CodeLine {
  n: number;
  text: string;
  hl: boolean;
}

export function codeLines(tab: CodeTabDto): CodeLine[] {
  const start = tab.startLine ?? 1;
  const highlighted = new Set(tab.highlightLines ?? []);
  return (tab.code ?? '').split(/\r?\n/).map((text, i) => ({ n: start + i, text, hl: highlighted.has(start + i) }));
}
