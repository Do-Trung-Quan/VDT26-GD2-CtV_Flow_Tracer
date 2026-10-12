import ts from 'typescript';
import type { HttpCallFact } from '../facts.js';
import { snippet, stringParts, stringValue, typeName, unwrap, type UrlContext } from './ts-utils.js';

const HTTP_METHODS = new Set(['get', 'post', 'put', 'delete', 'patch', 'head', 'options']);
const BODY_METHODS = new Set(['post', 'put', 'patch']);

/**
 * Nhận ra lời gọi `HttpClient`: receiver có kiểu `HttpClient` (field, tham số hoặc biến `inject(HttpClient)`), method là
 * `get / post / put / delete / patch / head / options / request`.
 *
 * @returns null nếu không phải lời gọi HTTP của Angular
 */
export function classifyHttpCall(
  call: ts.CallExpression,
  method: string,
  receiverType: string | undefined,
  sf: ts.SourceFile,
  ctx: UrlContext,
  paramTypes: ReadonlyMap<string, string | undefined>
): Omit<HttpCallFact, 'callOrder' | 'line' | 'guard'> | null {
  if (receiverType !== 'HttpClient') {
    return null;
  }
  const args = call.arguments;
  if (method === 'request') {
    const verb = stringValue(args[0]);
    const url = args[1];
    if (!verb || !url) {
      return null;
    }
    return build(verb.toUpperCase(), url, undefined, sf, ctx);
  }
  if (!HTTP_METHODS.has(method) || !args[0]) {
    return null;
  }
  const bodyArg = BODY_METHODS.has(method) ? args[1] : undefined;
  return build(method.toUpperCase(), args[0], bodyArg ? bodyTypeOf(bodyArg, paramTypes) : undefined, sf, ctx);
}

function build(
  httpMethod: string,
  urlExpr: ts.Expression,
  bodyType: string | undefined,
  sf: ts.SourceFile,
  ctx: UrlContext
): Omit<HttpCallFact, 'callOrder' | 'line' | 'guard'> {
  return {
    httpMethod,
    url: stringParts(urlExpr, sf, ctx),
    urlText: snippet(sf, urlExpr, 200),
    ...(bodyType ? { bodyType } : {})
  };
}

/** Kiểu của body: chỉ biết khi body là tham số có khai báo kiểu hoặc biểu thức `x as Kiểu`. */
function bodyTypeOf(arg: ts.Expression, paramTypes: ReadonlyMap<string, string | undefined>): string | undefined {
  if (ts.isAsExpression(arg)) {
    return typeName(arg.type);
  }
  const node = unwrap(arg);
  return ts.isIdentifier(node) ? paramTypes.get(node.text) : undefined;
}
