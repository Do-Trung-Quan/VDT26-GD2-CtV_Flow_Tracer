import ts from 'typescript';
import type { ConstantFact, DictionaryFact } from '../facts.js';
import { isStringLike, lineOf, propertyName, stringParts, stringValue, unwrap } from './ts-utils.js';

/**
 * Hằng số chuỗi cấp file — nguyên liệu để tính URL đầy đủ: `const A = '/x'`, `const B = `${A}/y``,
 * `export const environment = { apiBase: '/gateway' }` (→ `environment.apiBase`), `enum Api { Base = '/x' }`.
 * Chỉ giữ biểu thức chuỗi / tham chiếu tên; còn lại bỏ qua.
 */
export function extractConstants(statement: ts.Statement, sf: ts.SourceFile): ConstantFact[] {
  const out: ConstantFact[] = [];
  if (ts.isVariableStatement(statement)) {
    for (const decl of statement.declarationList.declarations) {
      if (ts.isIdentifier(decl.name) && decl.initializer) {
        collect(decl.name.text, unwrap(decl.initializer), sf, out, 0);
      }
    }
  } else if (ts.isEnumDeclaration(statement)) {
    for (const member of statement.members) {
      const name = propertyName(member.name);
      const value = stringValue(member.initializer);
      if (name && value !== undefined) {
        out.push({ name: `${statement.name.text}.${name}`, parts: [{ t: 'lit', v: value }], line: lineOf(sf, member) });
      }
    }
  }
  return out;
}

function collect(name: string, init: ts.Expression, sf: ts.SourceFile, out: ConstantFact[], depth: number): void {
  if (ts.isObjectLiteralExpression(init)) {
    if (depth >= 4) {
      return;
    }
    for (const prop of init.properties) {
      if (ts.isPropertyAssignment(prop)) {
        const key = propertyName(prop.name);
        if (key) {
          collect(`${name}.${key}`, unwrap(prop.initializer), sf, out, depth + 1);
        }
      }
    }
    return;
  }
  const parts = stringParts(init, sf);
  if (isStringLike(parts)) {
    out.push({ name, parts, line: lineOf(sf, init) });
  }
}

/** Tên / file trông như từ điển tiếng Việt: `categoryVi`, `vi.ts`, `category.vi.ts`, `messages_vi.ts`. */
export function looksLikeViDictionary(constantName: string, filePath: string): boolean {
  const base = filePath.split('/').pop() ?? '';
  return /(^|[._-])vi(-vn)?([._-]|$)/i.test(base) || /(Vi|VI|Vn|VN|_vi)$/.test(constantName);
}

/** Object literal toàn chuỗi (cho phép lồng) → từ điển với khóa làm phẳng `a.b`; null nếu không phải. */
export function extractDictionary(
  statement: ts.Statement,
  filePath: string
): DictionaryFact | null {
  if (!ts.isVariableStatement(statement)) {
    return null;
  }
  for (const decl of statement.declarationList.declarations) {
    if (!ts.isIdentifier(decl.name) || !decl.initializer) {
      continue;
    }
    const init = unwrap(decl.initializer);
    if (!ts.isObjectLiteralExpression(init) || !looksLikeViDictionary(decl.name.text, filePath)) {
      continue;
    }
    const entries: Record<string, string> = {};
    flatten('', init, entries, 0);
    if (Object.keys(entries).length > 0) {
      return { name: decl.name.text, entries };
    }
  }
  return null;
}

function flatten(prefix: string, node: ts.ObjectLiteralExpression, out: Record<string, string>, depth: number): void {
  if (depth >= 5) {
    return;
  }
  for (const prop of node.properties) {
    if (!ts.isPropertyAssignment(prop)) {
      continue;
    }
    const key = propertyName(prop.name);
    if (!key) {
      continue;
    }
    const value = unwrap(prop.initializer);
    const full = prefix ? `${prefix}.${key}` : key;
    const text = stringValue(value);
    if (text !== undefined) {
      out[full] = text;
    } else if (ts.isObjectLiteralExpression(value)) {
      flatten(full, value, out, depth + 1);
    }
  }
}
