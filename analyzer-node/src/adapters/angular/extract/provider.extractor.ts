import ts from 'typescript';
import type { ProviderFact, RouterRefFact } from '../facts.js';
import { expressionName, lineOf, propertyName, snippet, unwrap } from './ts-utils.js';
import { looksLikeRoutes, parseRoutes } from './route.extractor.js';

/**
 * `{ provide: TOKEN, useClass | useExisting | useFactory: … }` — "khi cần TOKEN thì dùng class nào". Trả về null nếu object
 * không phải khai báo provider.
 */
export function parseProvider(node: ts.ObjectLiteralExpression, sf: ts.SourceFile): ProviderFact | null {
  let provide: string | undefined;
  const fact: Partial<ProviderFact> = {};
  for (const prop of node.properties) {
    if (!ts.isPropertyAssignment(prop)) {
      continue;
    }
    const key = propertyName(prop.name);
    const value = unwrap(prop.initializer);
    if (key === 'provide') {
      provide = expressionName(value);
    } else if (key === 'useClass') {
      fact.useClass = expressionName(value);
    } else if (key === 'useExisting') {
      fact.useExisting = unwrapForwardRef(value);
    } else if (key === 'useFactory') {
      fact.useFactory = snippet(sf, value, 400);
    }
  }
  if (!provide) {
    return null;
  }
  return { provide, ...fact, line: lineOf(sf, node) };
}

function unwrapForwardRef(expr: ts.Expression): string | undefined {
  if (ts.isCallExpression(expr) && ts.isIdentifier(expr.expression) && expr.expression.text === 'forwardRef') {
    const arrow = expr.arguments[0];
    if (arrow && ts.isArrowFunction(arrow) && !ts.isBlock(arrow.body)) {
      return expressionName(unwrap(arrow.body));
    }
    return undefined;
  }
  return expressionName(expr);
}

/**
 * `provideRouter(routes)`, `RouterModule.forRoot(routes)`, `RouterModule.forChild(routes)` — tham chiếu mảng route
 * (tên biến) hoặc mảng viết tại chỗ.
 */
export function parseRouterRef(call: ts.CallExpression, sf: ts.SourceFile): RouterRefFact | null {
  const callee = call.expression;
  let kind: RouterRefFact['kind'] | undefined;
  if (ts.isIdentifier(callee) && callee.text === 'provideRouter') {
    kind = 'provideRouter';
  } else if (
    ts.isPropertyAccessExpression(callee) &&
    ts.isIdentifier(callee.expression) &&
    callee.expression.text === 'RouterModule' &&
    (callee.name.text === 'forRoot' || callee.name.text === 'forChild')
  ) {
    kind = callee.name.text;
  }
  const arg = call.arguments[0];
  if (!kind || !arg) {
    return null;
  }
  const node = unwrap(arg);
  if (ts.isIdentifier(node)) {
    return { kind, ref: node.text, line: lineOf(sf, call) };
  }
  if (ts.isArrayLiteralExpression(node) && looksLikeRoutes(node)) {
    return { kind, inline: parseRoutes(node, sf), line: lineOf(sf, call) };
  }
  return null;
}
