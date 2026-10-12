import ts from 'typescript';
import type { UrlPart } from '../facts.js';

/** Số dòng (từ 1) nơi node bắt đầu. */
export function lineOf(sf: ts.SourceFile, node: ts.Node): number {
  return sf.getLineAndCharacterOfPosition(node.getStart(sf)).line + 1;
}

export function endLineOf(sf: ts.SourceFile, node: ts.Node): number {
  return sf.getLineAndCharacterOfPosition(node.getEnd()).line + 1;
}

/** Bỏ ngoặc, `as X`, `satisfies X`, `!` bao ngoài. */
export function unwrap(node: ts.Expression): ts.Expression {
  let current = node;
  for (;;) {
    if (
      ts.isParenthesizedExpression(current) ||
      ts.isAsExpression(current) ||
      ts.isNonNullExpression(current) ||
      ts.isSatisfiesExpression(current) ||
      ts.isTypeAssertionExpression(current)
    ) {
      current = current.expression;
    } else {
      return current;
    }
  }
}

export function stringValue(node: ts.Node | undefined): string | undefined {
  if (node && (ts.isStringLiteral(node) || ts.isNoSubstitutionTemplateLiteral(node))) {
    return node.text;
  }
  return undefined;
}

export function decoratorsOf(node: ts.Node): readonly ts.Decorator[] {
  return ts.canHaveDecorators(node) ? (ts.getDecorators(node) ?? []) : [];
}

/** Tên decorator: `@Component(...)` → `Component`. */
export function decoratorName(decorator: ts.Decorator): string | undefined {
  const expr = decorator.expression;
  const callee = ts.isCallExpression(expr) ? expr.expression : expr;
  if (ts.isIdentifier(callee)) {
    return callee.text;
  }
  if (ts.isPropertyAccessExpression(callee)) {
    return callee.name.text;
  }
  return undefined;
}

export function decoratorArgs(decorator: ts.Decorator): readonly ts.Expression[] {
  return ts.isCallExpression(decorator.expression) ? decorator.expression.arguments : [];
}

export function propertyName(name: ts.PropertyName | ts.BindingName | undefined): string | undefined {
  if (!name) {
    return undefined;
  }
  if (ts.isIdentifier(name) || ts.isStringLiteral(name) || ts.isNoSubstitutionTemplateLiteral(name)) {
    return name.text;
  }
  if (ts.isNumericLiteral(name)) {
    return name.text;
  }
  return undefined;
}

/** Tên kiểu ngoài cùng: `Foo`, `ns.Foo` → `Foo`, `Promise<Foo>` → `Promise`; không xác định được thì undefined. */
export function typeName(type: ts.TypeNode | undefined): string | undefined {
  if (!type) {
    return undefined;
  }
  if (ts.isTypeReferenceNode(type)) {
    return entityName(type.typeName);
  }
  if (ts.isExpressionWithTypeArguments(type)) {
    return expressionName(type.expression);
  }
  if (ts.isParenthesizedTypeNode(type)) {
    return typeName(type.type);
  }
  return undefined;
}

/** Tên đối số generic đầu tiên của một kiểu tham chiếu: `Promise<Foo>` → `Foo`. */
export function typeArgOf(type: ts.TypeNode | undefined): string | undefined {
  return type && ts.isTypeReferenceNode(type) ? firstTypeArgName(type.typeArguments) : undefined;
}

/** Tên đối số generic đầu tiên: `InjectionToken<Foo>` → `Foo`. */
export function firstTypeArgName(typeArgs: readonly ts.TypeNode[] | undefined): string | undefined {
  return typeArgs && typeArgs.length > 0 ? typeName(typeArgs[0]) : undefined;
}

function entityName(name: ts.EntityName): string {
  return ts.isIdentifier(name) ? name.text : name.right.text;
}

/** Tên cuối của `A`, `a.B` (dùng cho `extends` / `implements`). */
export function expressionName(expr: ts.Expression): string | undefined {
  if (ts.isIdentifier(expr)) {
    return expr.text;
  }
  if (ts.isPropertyAccessExpression(expr)) {
    return expr.name.text;
  }
  return undefined;
}

export function hasModifier(node: ts.Node, kind: ts.SyntaxKind): boolean {
  return ts.canHaveModifiers(node) && (ts.getModifiers(node)?.some((m) => m.kind === kind) ?? false);
}

export function snippet(sf: ts.SourceFile, node: ts.Node, limit = 160): string {
  const text = node.getText(sf).replace(/\s+/g, ' ').trim();
  return text.length <= limit ? text : text.slice(0, limit - 1) + '…';
}

export interface UrlContext {
  /** Tên tham số của hàm đang phân tích. */
  params: ReadonlySet<string>;
  /** Biến cục bộ đã biết giá trị (`const url = …`). */
  locals: ReadonlyMap<string, UrlPart[]>;
}

const NO_CONTEXT: UrlContext = { params: new Set(), locals: new Map() };

/**
 * Biểu thức chuỗi → các thành phần ({@link UrlPart}). Chỉ nhận chuỗi, ghép chuỗi (`+`, template), tham chiếu tên, và vài
 * hàm bọc không đổi giá trị (`String(x)`, `encodeURIComponent(x)`, `x.toString()`); còn lại là `unk`.
 */
export function stringParts(expr: ts.Expression, sf: ts.SourceFile, ctx: UrlContext = NO_CONTEXT): UrlPart[] {
  const node = unwrap(expr);
  const literal = stringValue(node);
  if (literal !== undefined) {
    return [{ t: 'lit', v: literal }];
  }
  if (ts.isNumericLiteral(node)) {
    return [{ t: 'lit', v: node.text }];
  }
  if (ts.isTemplateExpression(node)) {
    const parts: UrlPart[] = [];
    if (node.head.text) {
      parts.push({ t: 'lit', v: node.head.text });
    }
    for (const span of node.templateSpans) {
      parts.push(...stringParts(span.expression, sf, ctx));
      if (span.literal.text) {
        parts.push({ t: 'lit', v: span.literal.text });
      }
    }
    return parts;
  }
  if (ts.isBinaryExpression(node) && node.operatorToken.kind === ts.SyntaxKind.PlusToken) {
    return [...stringParts(node.left, sf, ctx), ...stringParts(node.right, sf, ctx)];
  }
  if (ts.isIdentifier(node)) {
    const local = ctx.locals.get(node.text);
    if (local) {
      return local;
    }
    if (ctx.params.has(node.text)) {
      return [{ t: 'param', name: node.text }];
    }
    return [{ t: 'ref', path: [node.text] }];
  }
  if (ts.isPropertyAccessExpression(node)) {
    const path = accessPath(node);
    if (path) {
      const root = path[0];
      if (root && root !== 'this' && ctx.params.has(root) && path.length > 1) {
        // `item.id` với `item` là tham số: tên cuối làm tên biến đường dẫn
        return [{ t: 'param', name: path[path.length - 1] ?? root }];
      }
      return [{ t: 'ref', path }];
    }
  }
  if (ts.isCallExpression(node)) {
    const callee = node.expression;
    const calleeName = ts.isIdentifier(callee) ? callee.text : undefined;
    if (calleeName && ['String', 'Number', 'encodeURIComponent', 'encodeURI'].includes(calleeName) && node.arguments[0]) {
      return stringParts(node.arguments[0], sf, ctx);
    }
    if (ts.isPropertyAccessExpression(callee) && callee.name.text === 'toString' && node.arguments.length === 0) {
      return stringParts(callee.expression, sf, ctx);
    }
  }
  return [{ t: 'unk', text: snippet(sf, node, 60) }];
}

/** `a.b.c` / `this.a.b` → `['a','b','c']` / `['this','a','b']`; không phải chuỗi truy cập thuần thì null. */
export function accessPath(expr: ts.Expression): string[] | null {
  const node = unwrap(expr);
  if (ts.isIdentifier(node)) {
    return [node.text];
  }
  if (node.kind === ts.SyntaxKind.ThisKeyword) {
    return ['this'];
  }
  if (ts.isPropertyAccessExpression(node)) {
    const base = accessPath(node.expression);
    return base ? [...base, node.name.text] : null;
  }
  return null;
}

/** Chuỗi chỉ gồm thành phần chuỗi / tham chiếu tên (đủ để làm hằng) — loại biểu thức có `unk` duy nhất. */
export function isStringLike(parts: UrlPart[]): boolean {
  return parts.length > 0 && parts.some((p) => p.t === 'lit' || p.t === 'ref') && !parts.every((p) => p.t === 'unk');
}
