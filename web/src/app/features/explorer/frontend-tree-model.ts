import type { UiTreeNodeDto } from '../../core/api/api-types';
import type { IconName } from '../../core/ui/icon';
import type { TextPart } from './tree-filter';

/** Nhãn hiển thị của một nút trên cây Frontend, theo quy ước của bản vẽ: "Nút Thêm mới", "Ô Mã", "Màn hình Danh mục". */
export interface NodeLabel {
  /** Loại bằng chữ ("Nút", "Ô", "Hộp thoại"…); rỗng nếu không có. */
  prefix: string;
  /** Tên: nhãn đọc được từ mã nguồn, hoặc tên kỹ thuật khi mã nguồn không có nhãn. */
  text: string;
  /** true nếu `text` là tên kỹ thuật lấy từ mã nguồn (không có nhãn tiếng Việt). */
  technical: boolean;
  /** true nếu `text` là tên do AI đặt (mục đích ELEMENT_NAMING) — chỉ đổi cách hiển thị, không đổi cây hay liên kết. */
  ai?: boolean;
}

const KIND_PREFIX: Record<string, string> = {
  UI_APP: 'Ứng dụng',
  UI_SCREEN: 'Màn hình',
  UI_TAB: 'Tab',
  UI_DIALOG: 'Hộp thoại',
  UI_FORM: 'Form'
};

const ELEMENT_PREFIX: Record<string, string> = {
  button: 'Nút',
  link: 'Liên kết',
  input: 'Ô',
  textarea: 'Ô',
  select: 'Ô chọn',
  checkbox: 'Hộp chọn',
  radio: 'Hộp chọn',
  toggle: 'Công tắc',
  date: 'Ô ngày',
  table: 'Bảng',
  pagination: 'Phân trang'
};

/** Tên loại phần tử bằng tiếng Việt (tiêu đề cột phải); `component` / `element` không có loại cụ thể. */
export function elementTypeLabel(type: string | undefined): string {
  return (type && ELEMENT_PREFIX[type]) || 'Phần tử';
}

/** Loại của nút bằng tiếng Việt cho tiêu đề cột phải ("Màn hình", "Nút", "Ô"…). */
export function typeLabel(node: UiTreeNodeDto): string {
  return node.kind === 'UI_ELEMENT' ? elementTypeLabel(node.elementType) : (KIND_PREFIX[node.kind ?? ''] ?? '');
}

/** Nhãn của nút: loại + tên; không có nhãn thì tên kỹ thuật (`fallbackName`, rồi tên class / tuyến) và đánh dấu `technical`. */
export function nodeLabel(node: UiTreeNodeDto): NodeLabel {
  const kind = node.kind ?? 'UI_ELEMENT';
  const prefix = kind === 'UI_ELEMENT' ? (node.elementType ? (ELEMENT_PREFIX[node.elementType] ?? '') : '') : (KIND_PREFIX[kind] ?? '');
  if (node.aiLabel) {
    return { prefix, text: node.aiLabel, technical: false, ai: true };
  }
  if (node.displayName) {
    return { prefix, text: node.displayName, technical: false };
  }
  // Form không có nhãn: tên do bộ phân tích đặt dạng "form.ngSubmit.onSubmit" → chỉ giữ tên hàm gửi ("Form onSubmit")
  const formName = kind === 'UI_FORM' ? (/^form\.[^.]+\.(.+)$/.exec(node.name ?? '')?.[1] ?? node.name) : undefined;
  const fallback =
    kind === 'UI_SCREEN' ? (node.routePath ?? node.name) : kind === 'UI_ELEMENT' ? (node.fallbackName ?? node.name) : (formName ?? node.name);
  return { prefix, text: fallback ?? '', technical: kind !== 'UI_APP' };
}

/** "Nút Thêm mới" — nhãn đầy đủ dạng một chuỗi. */
export function fullLabel(node: UiTreeNodeDto): string {
  const l = nodeLabel(node);
  return l.prefix ? `${l.prefix} ${l.text}` : l.text;
}

/** Icon theo loại nút, giống bản vẽ (ứng dụng = lớp, màn hình = màn hình, hộp thoại = cửa sổ, form = tệp, nút = con trỏ…). */
export function nodeIcon(node: UiTreeNodeDto): IconName {
  switch (node.kind) {
    case 'UI_APP':
      return 'layers';
    case 'UI_SCREEN':
      return 'monitor';
    case 'UI_TAB':
      return 'folder';
    case 'UI_DIALOG':
      return 'window';
    case 'UI_FORM':
      return 'file';
    default:
      return node.elementType === 'input' || node.elementType === 'textarea' || node.elementType === 'select' || node.elementType === 'date'
        ? 'input'
        : node.elementType === 'table'
          ? 'table'
          : 'cursor';
  }
}

/** Thành phần chứa các phần tử bên trong (không phải phần tử tương tác). */
export function isContainer(node: UiTreeNodeDto): boolean {
  return node.kind !== 'UI_ELEMENT';
}

/** Phần tử là một bước của nghiệp vụ gọi API: tự gọi API, hoặc mở hộp thoại / chuyển màn hình mà bên trong có gọi API. */
export function isApiStep(node: UiTreeNodeDto): boolean {
  return node.callsApi === true || node.leadsToApi === true;
}

/**
 * Rút gọn cây cho chế độ mặc định "chỉ phần tử gọi API": luôn giữ ứng dụng và màn hình (đơn vị nghiệp vụ người dùng thấy, kể cả
 * khi bộ phân tích không thấy lời gọi API nào); giữ phần tử gọi API và nút **mở hộp thoại / chuyển màn hình có gọi API** (vẫn là
 * một bước của nghiệp vụ); giữ hộp thoại / form / tab nếu chính nó gọi API khi mở hoặc còn thành phần giữ lại bên trong. Phần tử
 * chỉ thay đổi giao diện bị ẩn — chúng vẫn nằm trong IR để dò vết, tìm kiếm và diễn giải.
 */
export function pruneToApi(nodes: UiTreeNodeDto[]): UiTreeNodeDto[] {
  const result: UiTreeNodeDto[] = [];
  for (const n of nodes) {
    const children = pruneToApi(n.children ?? []);
    if (n.kind === 'UI_APP' || n.kind === 'UI_SCREEN' || isApiStep(n) || children.length > 0) {
      result.push({ ...n, children });
    }
  }
  return result;
}

/**
 * Số phần tử khác nhau (mỗi phần tử đếm một lần dù xuất hiện ở nhiều màn hình): gọi API; nút mở hộp thoại / chuyển màn hình có
 * gọi API (`gateway`); chỉ thay đổi giao diện (`uiOnly`, bị ẩn ở chế độ mặc định).
 */
export function countByApi(nodes: UiTreeNodeDto[]): { api: number; gateway: number; uiOnly: number } {
  const api = new Set<string>();
  const gateway = new Set<string>();
  const ui = new Set<string>();
  const visit = (n: UiTreeNodeDto): void => {
    if (n.kind === 'UI_ELEMENT' && n.nodeKey) {
      (n.callsApi ? api : n.leadsToApi ? gateway : ui).add(n.nodeKey);
    }
    (n.children ?? []).forEach(visit);
  };
  nodes.forEach(visit);
  return { api: api.size, gateway: gateway.size, uiOnly: ui.size };
}

/** Bỏ dấu tiếng Việt và hạ chữ thường để so khớp không phân biệt dấu: "Thêm mới" ~ "them moi", "Đơn" ~ "don". */
export function fold(text: string | undefined): string {
  return (text ?? '')
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .toLowerCase();
}

/** Một kết quả tìm kiếm trên cây Frontend: nút, các tổ tiên của nó (từ ứng dụng xuống, không gồm nút) và đường dẫn để hiển thị. */
export interface UiHit {
  node: UiTreeNodeDto;
  trail: UiTreeNodeDto[];
  /** "Màn hình Danh mục › Hộp thoại Thêm danh mục" — nơi nút nằm (bỏ ứng dụng khi chỉ có một cấp). */
  context: string;
}

/** Kết quả tìm trong cây Frontend, chia hai nhóm giống ô tìm kiếm nhanh trên header. */
export interface UiSearchResult {
  /** Ứng dụng, màn hình, tab, hộp thoại, form. */
  screens: UiHit[];
  /** Phần tử giao diện (nút, ô nhập…). */
  elements: UiHit[];
  /** Tổng số khớp mỗi nhóm trước khi cắt theo `limit`. */
  screenTotal: number;
  elementTotal: number;
}

function searchText(node: UiTreeNodeDto): string {
  return fold([fullLabel(node), node.displayName, node.aiLabel, node.name, node.fallbackName, node.routePath].filter(Boolean).join(' '));
}

/**
 * Tìm trong cây (cả phần tử chỉ thao tác giao diện), không phân biệt hoa thường và dấu; mọi từ trong từ khóa đều phải khớp. Khớp tên
 * hiển thị, tên do AI đặt, tên kỹ thuật, tên class và đường dẫn route. Mỗi nút chỉ tính một lần (lần xuất hiện đầu tiên).
 */
export function searchUi(nodes: UiTreeNodeDto[], query: string, limit = 30): UiSearchResult {
  const tokens = fold(query).split(/\s+/).filter(Boolean);
  const result: UiSearchResult = { screens: [], elements: [], screenTotal: 0, elementTotal: 0 };
  if (tokens.length === 0) {
    return result;
  }
  const seen = new Set<string>();
  const visit = (list: UiTreeNodeDto[], trail: UiTreeNodeDto[]): void => {
    for (const node of list) {
      const key = node.nodeKey;
      if (key && !seen.has(key)) {
        const text = searchText(node);
        if (tokens.every((t) => text.includes(t))) {
          seen.add(key);
          const isElement = node.kind === 'UI_ELEMENT';
          const hit: UiHit = { node, trail, context: trail.slice(trail.length > 1 ? 1 : 0).map(fullLabel).join(' › ') };
          if (isElement) {
            result.elementTotal++;
            if (result.elements.length < limit) {
              result.elements.push(hit);
            }
          } else {
            result.screenTotal++;
            if (result.screens.length < limit) {
              result.screens.push(hit);
            }
          }
        }
      }
      visit(node.children ?? [], [...trail, node]);
    }
  };
  visit(nodes, []);
  return result;
}

/** Tách chữ thành đoạn khớp / không khớp từ khóa, không phân biệt hoa thường và dấu (để tô sáng kết quả tìm kiếm). */
export function highlightFold(text: string | undefined, query: string): TextPart[] {
  const value = text ?? '';
  const tokens = fold(query).split(/\s+/).filter(Boolean);
  if (tokens.length === 0 || !value) {
    return [{ text: value, hit: false }];
  }
  let folded = '';
  const map: number[] = [];
  for (let i = 0; i < value.length; i++) {
    for (const ch of fold(value[i])) {
      folded += ch;
      map.push(i);
    }
  }
  const marked = new Array<boolean>(value.length).fill(false);
  for (const t of tokens) {
    for (let at = folded.indexOf(t); at >= 0; at = folded.indexOf(t, at + t.length)) {
      for (let j = at; j < at + t.length; j++) {
        marked[map[j]] = true;
      }
    }
  }
  const parts: TextPart[] = [];
  let from = 0;
  for (let i = 1; i <= value.length; i++) {
    if (i === value.length || marked[i] !== marked[from]) {
      parts.push({ text: value.slice(from, i), hit: marked[from] });
      from = i;
    }
  }
  return parts;
}

/** Đường từ gốc tới nút `key` (gồm cả nút), theo lần xuất hiện đầu tiên trong cây; null nếu không có. */
export function findTrail(nodes: UiTreeNodeDto[], key: string): UiTreeNodeDto[] | null {
  for (const n of nodes) {
    if (n.nodeKey === key) {
      return [n];
    }
    const below = findTrail(n.children ?? [], key);
    if (below) {
      return [n, ...below];
    }
  }
  return null;
}

/** Một dòng đang hiện trên cây (đã trải phẳng theo trạng thái mở). */
export interface UiRow {
  /** Định danh dòng = đường từ gốc (cùng một nút ở nhiều nơi là nhiều dòng khác nhau). */
  id: string;
  node: UiTreeNodeDto;
  depth: number;
  open: boolean;
  /** Tổ tiên theo thứ tự từ gốc, không gồm chính nút. */
  trail: UiTreeNodeDto[];
}

/** Trải phẳng cây: chỉ đi vào nút đang mở (`openAll` thì mở hết, dùng khi đang lọc). */
export function flattenRows(nodes: UiTreeNodeDto[], opened: ReadonlySet<string>, openAll: boolean): UiRow[] {
  const rows: UiRow[] = [];
  const visit = (list: UiTreeNodeDto[], depth: number, parentId: string, trail: UiTreeNodeDto[]): void => {
    for (const node of list) {
      const id = `${parentId}>${node.nodeKey}`;
      const hasChildren = (node.children?.length ?? 0) > 0;
      const open = hasChildren && (openAll || opened.has(id));
      rows.push({ id, node, depth, open, trail });
      if (open) {
        visit(node.children ?? [], depth + 1, id, [...trail, node]);
      }
    }
  };
  visit(nodes, 0, '', []);
  return rows;
}

/** Mã dòng của nút `key` theo đường đầu tiên tìm thấy, cùng mã của các tổ tiên cần mở để thấy nó. */
export function revealIds(nodes: UiTreeNodeDto[], key: string): { rowId: string; ancestors: string[] } | null {
  const trail = findTrail(nodes, key);
  if (!trail) {
    return null;
  }
  const ids: string[] = [];
  let id = '';
  for (const n of trail) {
    id = `${id}>${n.nodeKey}`;
    ids.push(id);
  }
  return { rowId: ids[ids.length - 1] ?? '', ancestors: ids.slice(0, -1) };
}

/** Đếm số phần tử tương tác (khác nhau) nằm trong một nút chứa. */
export function countElements(node: UiTreeNodeDto): number {
  const keys = new Set<string>();
  const visit = (n: UiTreeNodeDto): void => {
    if (n.kind === 'UI_ELEMENT' && n.nodeKey) {
      keys.add(n.nodeKey);
    }
    (n.children ?? []).forEach(visit);
  };
  visit(node);
  return keys.size;
}

export type { TextPart };
