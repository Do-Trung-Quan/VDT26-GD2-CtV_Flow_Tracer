import ts from 'typescript';
import type { UiActionFact } from '../facts.js';
import { stringValue, unwrap } from './ts-utils.js';

const DIALOG_TYPE = /dialog|modal|drawer|overlay|popup|sheet|offcanvas/i;
const OPEN_METHODS = /^open(dialog|modal|drawer|popup)?$/i;

/**
 * Nhận ra thao tác chỉ trên giao diện:
 *
 * - `dialog.open(Component, …)` (mọi receiver có hàm `open` với đối số đầu là tên class; pha 2 kiểm tra đó là component
 *   của dự án nên `window.open(url)` không bị nhận nhầm);
 * - `router.navigate([...])` / `router.navigateByUrl('...')` trên receiver kiểu `Router` hoặc tên `router`.
 */
export function classifyUiAction(
  call: ts.CallExpression,
  method: string,
  receiverType: string | undefined,
  receiverPath: string[],
  sf: ts.SourceFile
): Omit<UiActionFact, 'callOrder' | 'line'> | null {
  const first = call.arguments[0];
  if (OPEN_METHODS.test(method) && first && ts.isIdentifier(unwrap(first))) {
    const typeOk = receiverType !== undefined && DIALOG_TYPE.test(receiverType);
    const nameOk = method.toLowerCase() !== 'open' || receiverPath.some((p) => DIALOG_TYPE.test(p));
    if (typeOk || nameOk) {
      return {
        action: 'OPEN_DIALOG',
        target: (unwrap(first) as ts.Identifier).text,
        ...(receiverType ? { receiverType } : {})
      };
    }
  }
  if ((method === 'navigate' || method === 'navigateByUrl') && isRouter(receiverType, receiverPath)) {
    const target = first ? routeTarget(first, sf) : undefined;
    return { action: 'NAVIGATE', ...(target ? { target } : {}), receiverType: 'Router' };
  }
  return null;
}

function isRouter(receiverType: string | undefined, receiverPath: string[]): boolean {
  return receiverType === 'Router' || receiverPath[receiverPath.length - 1]?.toLowerCase() === 'router';
}

/** `'/orders'` → `/orders`; `['/orders', id]` → `/orders/{id}`; không đọc được thì undefined. */
function routeTarget(arg: ts.Expression, sf: ts.SourceFile): string | undefined {
  const node = unwrap(arg);
  const literal = stringValue(node);
  if (literal !== undefined) {
    return literal;
  }
  if (ts.isArrayLiteralExpression(node)) {
    const segments: string[] = [];
    for (const element of node.elements) {
      const e = unwrap(element);
      const value = stringValue(e);
      if (value !== undefined) {
        segments.push(value.replace(/^\/+|\/+$/g, ''));
      } else if (ts.isIdentifier(e)) {
        segments.push(`{${e.text}}`);
      } else if (ts.isPropertyAccessExpression(e)) {
        segments.push(`{${e.name.text}}`);
      } else {
        return undefined;
      }
    }
    const first = node.elements[0];
    const absolute = first !== undefined && (stringValue(unwrap(first)) ?? '').startsWith('/');
    void sf;
    return (absolute ? '/' : '') + segments.filter((s) => s.length > 0).join('/');
  }
  return undefined;
}
