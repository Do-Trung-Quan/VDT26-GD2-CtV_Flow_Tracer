import type { ApiModuleDto, EndpointNodeDto } from '../../core/api/api-types';

/** Một đoạn chữ, đánh dấu phần khớp từ khóa để tô sáng (không dùng innerHTML). */
export interface TextPart {
  text: string;
  hit: boolean;
}

const norm = (s: string | undefined) => (s ?? '').toLowerCase();

function endpointMatches(e: EndpointNodeDto, q: string): boolean {
  return [e.httpMethod, e.path, e.summary, `${e.httpMethod} ${e.path}`].some((v) => norm(v).includes(q));
}

/**
 * Lọc cây Module → Endpoint theo từ khóa (không phân biệt hoa thường): module khớp tên thì giữ mọi endpoint; ngược lại
 * chỉ giữ endpoint khớp phương thức, đường dẫn hoặc mô tả. Từ khóa rỗng thì giữ nguyên.
 */
export function filterModules(modules: ApiModuleDto[], query: string): ApiModuleDto[] {
  const q = norm(query.trim());
  if (!q) {
    return modules;
  }
  const result: ApiModuleDto[] = [];
  for (const m of modules) {
    if (norm(m.name).includes(q)) {
      result.push(m);
      continue;
    }
    const endpoints = (m.endpoints ?? []).filter((e) => endpointMatches(e, q));
    if (endpoints.length) {
      result.push({ ...m, endpoints, endpointCount: endpoints.length });
    }
  }
  return result;
}

/** Tách chữ thành các đoạn khớp / không khớp từ khóa. */
export function highlight(text: string | undefined, query: string): TextPart[] {
  const value = text ?? '';
  const q = query.trim().toLowerCase();
  if (!q) {
    return [{ text: value, hit: false }];
  }
  const parts: TextPart[] = [];
  const lower = value.toLowerCase();
  let from = 0;
  for (let i = lower.indexOf(q); i >= 0; i = lower.indexOf(q, from)) {
    if (i > from) {
      parts.push({ text: value.slice(from, i), hit: false });
    }
    parts.push({ text: value.slice(i, i + q.length), hit: true });
    from = i + q.length;
  }
  if (from < value.length) {
    parts.push({ text: value.slice(from), hit: false });
  }
  return parts;
}
