import type { SearchCategory, SearchDto, SearchHitDto } from '../../core/api/api-types';
import type { IconName } from '../../core/ui/icon';

/** Các nhóm bảng lệnh hiển thị (khớp bản vẽ: API, Màn hình, Phần tử, Bảng dữ liệu). Nhóm "Hàm / lớp" chưa có chỗ mở nên không hỏi. */
export const PALETTE_CATEGORIES: SearchCategory[] = ['API', 'SCREEN', 'ELEMENT', 'TABLE'];

/** Số kết quả mỗi nhóm trong bảng lệnh. */
export const PALETTE_LIMIT = 6;

export interface PaletteItem {
  hit: SearchHitDto;
  category: SearchCategory;
  title: string;
  subtitle: string;
  icon: IconName;
}

/** Dòng của danh sách: tiêu đề nhóm hoặc một kết quả (kèm vị trí trong danh sách để chọn bằng phím). */
export type PaletteRow = { header: string } | { item: PaletteItem; index: number };

const KIND_LABEL: Record<string, string> = {
  API_ENDPOINT: 'API',
  UI_APP: 'Ứng dụng',
  UI_SCREEN: 'Màn hình',
  UI_TAB: 'Tab',
  UI_DIALOG: 'Hộp thoại',
  UI_FORM: 'Form',
  UI_ELEMENT: 'Phần tử',
  DB_TABLE: 'Bảng dữ liệu'
};

const CATEGORY_ICON: Record<SearchCategory, IconName> = {
  API: 'bolt',
  SCREEN: 'monitor',
  ELEMENT: 'cursor',
  TABLE: 'db',
  CODE: 'code'
};

/** Kết quả → mục hiển thị: tên (nhãn tiếng Việt nếu có), dòng phụ "loại · ngữ cảnh". */
export function toItem(category: SearchCategory, hit: SearchHitDto): PaletteItem {
  const kind = KIND_LABEL[hit.kind ?? ''] ?? '';
  // API: tên là "METHOD đường dẫn" (thứ người dùng nhớ), mô tả ngắn và module đi kèm ở dòng phụ
  const isApi = category === 'API';
  const subtitle = (isApi ? [hit.displayName, hit.context] : [kind, hit.context]).filter((s) => !!s).join(' · ');
  return {
    hit,
    category,
    title: isApi ? (hit.name ?? '') : hit.displayName || hit.name || '',
    subtitle: isApi && !subtitle ? kind : subtitle,
    icon: CATEGORY_ICON[category]
  };
}

/** Kết quả C3 → danh sách dòng (tiêu đề nhóm xen kẽ kết quả) theo thứ tự PALETTE_CATEGORIES. */
export function toRows(dto: SearchDto | null): { rows: PaletteRow[]; items: PaletteItem[] } {
  const rows: PaletteRow[] = [];
  const items: PaletteItem[] = [];
  for (const category of PALETTE_CATEGORIES) {
    const group = (dto?.groups ?? []).find((g) => g.category === category);
    if (!group?.hits?.length) {
      continue;
    }
    rows.push({ header: group.label ?? category });
    for (const hit of group.hits) {
      const item = toItem(category, hit);
      rows.push({ item, index: items.length });
      items.push(item);
    }
  }
  return { rows, items };
}

/** Phím mũi tên: đi vòng trong `count` mục; không có mục nào thì 0. */
export function moveIndex(current: number, delta: number, count: number): number {
  return count <= 0 ? 0 : (((current + delta) % count) + count) % count;
}
