import type { LabelFact } from '../facts.js';
import { commonDirDepth } from '../workspace/paths.js';

export interface Dictionary {
  /** File chứa từ điển (để chọn từ điển gần component nhất). */
  file: string;
  entries: Readonly<Record<string, string>>;
}

export interface ResolvedLabel {
  /** Chữ hiển thị; vắng khi chỉ biết khóa mà không tìm thấy bản dịch. */
  text?: string;
  /** Khóa dịch (`t('create')`, `'order.confirm' | translate`). */
  key?: string;
}

const MAX_LABEL = 80;

/**
 * Đổi nhãn đọc từ template thành chữ tiếng Việt: chữ tĩnh dùng nguyên; `t('khóa')` / `'khóa' | translate` tra từ điển
 * tiếng Việt (`*.vi.ts`, `assets/i18n/vi.json`) gần file chứa template nhất. Không có bản dịch thì chỉ giữ khóa — không
 * đoán chữ từ tên khóa.
 */
export class LabelResolver {
  private readonly dictionaries: readonly Dictionary[];

  constructor(dictionaries: readonly Dictionary[]) {
    this.dictionaries = [...dictionaries].sort((a, b) => a.file.localeCompare(b.file));
  }

  resolve(label: LabelFact | undefined, fromFile: string): ResolvedLabel | undefined {
    if (!label) {
      return undefined;
    }
    if (label.kind === 'text') {
      const text = clip(label.value);
      return text ? { text } : undefined;
    }
    const key = label.value;
    let best: { text: string; depth: number } | undefined;
    for (const dictionary of this.dictionaries) {
      const text = dictionary.entries[key];
      if (text === undefined) {
        continue;
      }
      const depth = commonDirDepth(fromFile, dictionary.file);
      if (best === undefined || depth > best.depth) {
        best = { text, depth };
      }
    }
    return best ? { text: clip(best.text), key } : { key };
  }
}

function clip(text: string): string {
  const collapsed = text.replace(/\s+/g, ' ').trim();
  return collapsed.length <= MAX_LABEL ? collapsed : collapsed.slice(0, MAX_LABEL - 1) + '…';
}
