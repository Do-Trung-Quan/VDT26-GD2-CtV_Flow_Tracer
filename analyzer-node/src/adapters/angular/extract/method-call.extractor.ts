import ts from 'typescript';
import type { CallFact, CallRef, Guard, HttpCallFact, UiActionFact, UrlPart } from '../facts.js';
import { classifyHttpCall } from './http-call.extractor.js';
import { classifyUiAction } from './ui-action.extractor.js';
import { accessPath, endLineOf, isStringLike, snippet, stringParts, typeName, unwrap } from './ts-utils.js';

/** Điều kiện ghi cho lời gọi nằm trong callback chạy khi hộp thoại đóng; `branch` để Engine nhận ra mà không cần so chữ. */
export const AFTER_CLOSED: Guard = { type: 'IF', text: 'sau khi hộp thoại đóng', branch: 'afterClosed' };

/** Sự kiện đóng hộp thoại: `afterClosed()`, `afterClosed$`, `closed`, `onClose`… (hậu tố `$` của Observable được bỏ khi so). */
const DIALOG_CLOSE_EVENT = /^(afterClosed|afterDismissed|afterClose|closed|onClose|onClosed|onDismiss|onDismissed|onDidDismiss|dismissed)$/;
const isCloseEvent = (name: string): boolean => DIALOG_CLOSE_EVENT.test(name.replace(/\$$/, ''));
const SUBSCRIBE_LIKE = new Set(['subscribe', 'then']);

/** `x.afterClosed().subscribe(cb)` / `x.closed.pipe(...).subscribe(cb)`: callee là `subscribe`/`then` của một chuỗi có sự kiện đóng hộp thoại. */
function opensAfterDialogClose(callee: ts.PropertyAccessExpression): boolean {
  if (!SUBSCRIBE_LIKE.has(callee.name.text)) {
    return false;
  }
  let current: ts.Expression = unwrap(callee.expression);
  for (let hop = 0; hop < 8; hop++) {
    if (ts.isCallExpression(current)) {
      const inner = unwrap(current.expression);
      if (ts.isPropertyAccessExpression(inner)) {
        if (isCloseEvent(inner.name.text)) {
          return true;
        }
        current = unwrap(inner.expression);
        continue;
      }
      return false;
    }
    if (ts.isPropertyAccessExpression(current)) {
      if (isCloseEvent(current.name.text)) {
        return true;
      }
      current = unwrap(current.expression);
      continue;
    }
    return false;
  }
  return false;
}

/** Kiểu đã biết của tên trong phạm vi method: field của class (`this.x`), tham số, biến cục bộ. */
export interface TypeScope {
  /** Field của class: tên → tên kiểu (hoặc đối số `inject(X)`). */
  fields: ReadonlyMap<string, string | undefined>;
  /** Tham số của method: tên → tên kiểu. */
  params: ReadonlyMap<string, string | undefined>;
}

export interface BodyFacts {
  calls: CallFact[];
  httpCalls: HttpCallFact[];
  uiActions: UiActionFact[];
}

/**
 * Duyệt thân một method theo ĐÚNG thứ tự thực thi (receiver rồi đối số rồi lời gọi bao ngoài) và ghi lại lời gọi hàm,
 * lời gọi HTTP, thao tác giao diện (mở hộp thoại, chuyển trang) cùng điều kiện bao quanh. Hàm lồng (callback của
 * `subscribe`, `then`…) được tính vào method chứa nó.
 */
export function extractBody(body: ts.Node | undefined, sf: ts.SourceFile, scope: TypeScope): BodyFacts {
  const result: BodyFacts = { calls: [], httpCalls: [], uiActions: [] };
  if (!body) {
    return result;
  }
  const locals = new Map<string, UrlPart[]>();
  const localTypes = new Map<string, string | undefined>();
  /** Biến cục bộ gán từ kết quả một lời gọi: `const repo = this.facade.getRepo()`. */
  const localCalls = new Map<string, CallRef>();
  const params = new Set(scope.params.keys());
  let order = 0;
  /** > 0 khi đang ở trong callback chạy khi hộp thoại đóng: mọi lời gọi trong đó mang điều kiện `afterClosed`, kể cả khi còn lồng thêm `if`. */
  let afterClosedDepth = 0;

  /** Điều kiện ghi cho lời gọi: trong callback đóng hộp thoại thì giữ `branch = afterClosed` và nối thêm điều kiện bên trong (nếu có). */
  const effectiveGuard = (guard: Guard | undefined): Guard | undefined =>
    afterClosedDepth > 0
      ? { ...AFTER_CLOSED, ...(guard?.text ? { text: `${AFTER_CLOSED.text} — ${guard.text}` } : {}) }
      : guard;

  const typeOfName = (name: string): string | undefined => localTypes.get(name) ?? scope.params.get(name);

  const receiverType = (root: string | null, path: string[]): string | undefined => {
    if (root === 'this' && path.length === 1 && path[0] !== undefined) {
      return scope.fields.get(path[0]);
    }
    if (root !== null && root !== 'this' && path.length === 0) {
      return typeOfName(root);
    }
    return undefined;
  };

  /** Receiver của một lời gọi: đường truy cập từ gốc, hoặc kết quả của lời gọi khác (nối chuỗi / biến gán từ lời gọi). */
  function receiverOf(expr: ts.Expression): Pick<CallRef, 'root' | 'path' | 'rootType' | 'via'> {
    let awaited = false;
    let node = unwrap(expr);
    if (ts.isAwaitExpression(node)) {
      awaited = true;
      node = unwrap(node.expression);
    }
    if (ts.isCallExpression(node)) {
      const via = callRefOf(node);
      return { root: null, path: [], ...(via ? { via: { ...via, ...(awaited ? { awaited } : {}) } } : {}) };
    }
    const full = accessPath(node);
    if (!full) {
      return { root: null, path: [] };
    }
    const root = full[0] ?? null;
    const path = full.slice(1);
    if (root === null || root === 'this') {
      return { root, path };
    }
    const bound = path.length === 0 ? localCalls.get(root) : undefined;
    if (bound) {
      return { root, path, via: bound };
    }
    const rootType = typeOfName(root);
    return { root, path, ...(rootType ? { rootType } : {}) };
  }

  function callRefOf(call: ts.CallExpression): CallRef | undefined {
    const callee = unwrap(call.expression);
    if (!ts.isPropertyAccessExpression(callee)) {
      return undefined;
    }
    return { ...receiverOf(callee.expression), method: callee.name.text, argCount: call.arguments.length };
  }

  function record(call: ts.CallExpression, rawGuard: Guard | undefined): void {
    const guard = effectiveGuard(rawGuard);
    const callee = unwrap(call.expression);
    if (!ts.isPropertyAccessExpression(callee)) {
      return;
    }
    const full = accessPath(callee.expression);
    const root = full ? (full[0] ?? null) : null;
    const path = full ? full.slice(1) : [];
    const receiver = receiverOf(callee.expression);
    const method = callee.name.text;
    const line = sf.getLineAndCharacterOfPosition(call.getStart(sf)).line + 1;
    const ctx = { params: new Set(params), locals };

    const http = classifyHttpCall(call, method, receiverType(root, path), sf, ctx, scope.params);
    if (http) {
      result.httpCalls.push({ ...http, callOrder: order++, ...(guard ? { guard } : {}), line, endLine: endLineOf(sf, enclosingStatement(call)) });
      return;
    }
    const ui = classifyUiAction(call, method, receiverType(root, path), path, sf);
    if (ui) {
      result.uiActions.push({ ...ui, callOrder: order++, line });
      return;
    }
    result.calls.push({
      root,
      path,
      method,
      argCount: call.arguments.length,
      ...(receiver.rootType ? { rootType: receiver.rootType } : {}),
      ...(receiver.via ? { via: receiver.via } : {}),
      callOrder: order++,
      ...(guard ? { guard } : {}),
      line
    });
  }

  function declare(decl: ts.VariableDeclaration): void {
    if (!ts.isIdentifier(decl.name)) {
      return;
    }
    const name = decl.name.text;
    const declared = typeName(decl.type);
    if (decl.initializer) {
      const init = unwrap(decl.initializer);
      if (ts.isCallExpression(init) && ts.isIdentifier(init.expression) && init.expression.text === 'inject') {
        const arg = init.arguments[0];
        localTypes.set(name, arg && ts.isIdentifier(arg) ? arg.text : declared);
        return;
      }
      if (ts.isNewExpression(init) && ts.isIdentifier(init.expression)) {
        localTypes.set(name, declared ?? init.expression.text); // `const x = new Foo()`
        return;
      }
      // `const repo = this.facade.getRepo()` / `await this.facade.load()`: kiểu suy ra từ kiểu trả về đã khai báo
      const fromCall = declared ? undefined : receiverOf(init);
      if (fromCall?.via) {
        localCalls.set(name, fromCall.via);
      }
      const parts = stringParts(init, sf, { params, locals });
      if (isStringLike(parts)) {
        locals.set(name, parts);
      }
    }
    localTypes.set(name, declared);
  }

  function visit(node: ts.Node, guard: Guard | undefined): void {
    if (ts.isIfStatement(node)) {
      const text = snippet(sf, node.expression, 120);
      visit(node.expression, guard);
      visit(node.thenStatement, { type: 'IF', text, branch: 'then' });
      if (node.elseStatement) {
        visit(node.elseStatement, { type: 'ELSE', text, branch: 'else' });
      }
      return;
    }
    if (ts.isConditionalExpression(node)) {
      const text = snippet(sf, node.condition, 120);
      visit(node.condition, guard);
      visit(node.whenTrue, { type: 'TERNARY', text, branch: 'then' });
      visit(node.whenFalse, { type: 'TERNARY', text, branch: 'else' });
      return;
    }
    if (ts.isTryStatement(node)) {
      visit(node.tryBlock, { type: 'TRY' });
      if (node.catchClause) {
        visit(node.catchClause.block, { type: 'CATCH' });
      }
      if (node.finallyBlock) {
        visit(node.finallyBlock, guard);
      }
      return;
    }
    if (ts.isForStatement(node) || ts.isWhileStatement(node) || ts.isDoStatement(node) || ts.isForOfStatement(node) || ts.isForInStatement(node)) {
      ts.forEachChild(node, (child) => visit(child, { type: 'LOOP', text: snippet(sf, node, 80) }));
      return;
    }
    if (ts.isCaseClause(node)) {
      ts.forEachChild(node, (child) => visit(child, { type: 'SWITCH', text: 'case ' + snippet(sf, node.expression, 60) }));
      return;
    }
    if (ts.isDefaultClause(node)) {
      ts.forEachChild(node, (child) => visit(child, { type: 'SWITCH', text: 'default' }));
      return;
    }
    if (ts.isVariableDeclaration(node)) {
      if (node.initializer) {
        visit(node.initializer, guard);
      }
      declare(node);
      return;
    }
    if (ts.isCallExpression(node)) {
      const callee = unwrap(node.expression);
      // receiver chạy trước: `a.b(x).c(y)` ghi `b` rồi `c`
      if (ts.isPropertyAccessExpression(callee)) {
        visit(callee.expression, guard);
      } else {
        visit(callee, guard);
      }
      // callback của `ref.afterClosed().subscribe(...)`: chạy khi hộp thoại đóng, không thuộc luồng của lần bấm
      const closing = ts.isPropertyAccessExpression(callee) && opensAfterDialogClose(callee);
      node.arguments.forEach((arg) => {
        afterClosedDepth += closing ? 1 : 0;
        visit(arg, guard);
        afterClosedDepth -= closing ? 1 : 0;
      });
      record(node, guard);
      return;
    }
    ts.forEachChild(node, (child) => visit(child, guard));
  }

  visit(body, undefined);
  return result;
}

/** Câu lệnh chứa biểu thức (để lấy dòng cuối của cả chuỗi gọi nhiều dòng); không thấy thì chính biểu thức. */
function enclosingStatement(node: ts.Node): ts.Node {
  let current: ts.Node = node;
  while (current.parent && !ts.isBlock(current.parent) && !ts.isSourceFile(current.parent) && !ts.isClassDeclaration(current.parent)) {
    current = current.parent;
    if (ts.isExpressionStatement(current) || ts.isVariableStatement(current) || ts.isReturnStatement(current)) {
      return current;
    }
  }
  return node;
}
