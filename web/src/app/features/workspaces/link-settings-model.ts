import type { LinkOverrideDto, LinkRuleDto, LinkRuleType } from '../../core/api/api-types';

/** Tên loại luật tiếng Việt, mô tả, mẫu gợi ý và có cần "Thay bằng" hay không. */
export const RULE_TYPES: readonly {
  type: LinkRuleType;
  label: string;
  help: string;
  patternHint: string;
  replacementHint: string | null;
}[] = [
  {
    type: 'PREFIX',
    label: 'Tiền tố URL / thay biến',
    help: 'Đường dẫn ở Frontend bắt đầu bằng mẫu thì thay bằng giá trị mới — kể cả phần tool chưa tính được, vd {apiUrl}.',
    patternHint: '{apiUrl}',
    replacementHint: '/api/v1'
  },
  {
    type: 'REWRITE',
    label: 'Proxy (biểu thức chính quy)',
    help: 'Giống pathRewrite của proxy: phần khớp biểu thức chính quy được thay bằng giá trị mới (để trống = bỏ phần khớp).',
    patternHint: '^/gateway',
    replacementHint: '(để trống = bỏ phần khớp)'
  },
  {
    type: 'CONTEXT_PATH',
    label: 'Context-path Backend',
    help: 'Tiền tố thêm vào đường dẫn API của Backend chưa khai context-path trong cấu hình.',
    patternHint: '/shop-api',
    replacementHint: null
  },
  {
    type: 'QUERY_SINK',
    label: 'Điểm thực thi truy vấn',
    help: 'Hàm thư viện (không có mã nguồn) thực thi truy vấn, dạng Lớp#hàm. Có hiệu lực từ lần quét sau.',
    patternHint: 'com.example.db.QueryRunner#run',
    replacementHint: null
  }
];

export function ruleTypeLabel(type: LinkRuleType | undefined): string {
  return RULE_TYPES.find((t) => t.type === type)?.label ?? type ?? '';
}

export function needsReplacement(type: LinkRuleType): boolean {
  return RULE_TYPES.find((t) => t.type === type)?.replacementHint !== null;
}

/** Một luật thủ công gửi lên E7. */
export interface RuleItem {
  type: LinkRuleType;
  pattern: string;
  replacement?: string;
  description?: string;
}

export function toItem(rule: LinkRuleDto): RuleItem {
  return {
    type: rule.type ?? 'PREFIX',
    pattern: rule.pattern ?? '',
    ...(rule.replacement ? { replacement: rule.replacement } : {}),
    ...(rule.description ? { description: rule.description } : {})
  };
}

/** Danh sách luật thủ công sau khi thêm (index = null) / sửa (index) một luật. */
export function withRule(manual: readonly LinkRuleDto[], index: number | null, item: RuleItem): RuleItem[] {
  const items = manual.map(toItem);
  if (index === null) {
    items.push(item);
  } else {
    items[index] = item;
  }
  return items;
}

export function withoutRule(manual: readonly LinkRuleDto[], index: number): RuleItem[] {
  return manual.map(toItem).filter((_, i) => i !== index);
}

/** Lỗi của trường mẫu theo luật thứ `index` trong danh sách gửi lên (Engine trả `manual[i].pattern`). */
export function patternError(errors: Record<string, unknown>, index: number): string | null {
  const value = errors[`manual[${index}].pattern`];
  return typeof value === 'string' ? value : null;
}

/** Nhãn "Loại" của một liên kết chốt tay. */
export function decisionLabel(o: LinkOverrideDto): { label: string; pill: string } {
  return o.decision === 'REJECT' ? { label: 'Loại bỏ', pill: 'p-red' } : { label: 'Ghim', pill: 'p-green' };
}
