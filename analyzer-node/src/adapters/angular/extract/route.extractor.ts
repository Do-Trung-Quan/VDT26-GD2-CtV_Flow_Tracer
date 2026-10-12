import ts from 'typescript';
import type { LazyRef, RouteFact } from '../facts.js';
import { lineOf, propertyName, stringValue, unwrap } from './ts-utils.js';

/** Một mảng literal có phải mảng route (phần tử là object có `path`, hoặc `redirectTo`/`component`/`children`). */
export function looksLikeRoutes(array: ts.ArrayLiteralExpression): boolean {
  const objects = array.elements.filter(ts.isObjectLiteralExpression);
  if (objects.length === 0) {
    return false;
  }
  return objects.every((o) => o.properties.some((p) => ['path', 'matcher'].includes(propertyName(p.name) ?? '')));
}

/** Đọc mảng route; phần tử không phải object literal (spread, biến) bị bỏ qua. */
export function parseRoutes(array: ts.ArrayLiteralExpression, sf: ts.SourceFile): RouteFact[] {
  const routes: RouteFact[] = [];
  for (const element of array.elements) {
    const node = unwrap(element);
    if (!ts.isObjectLiteralExpression(node)) {
      continue;
    }
    const route: RouteFact = { path: '', line: lineOf(sf, node) };
    for (const prop of node.properties) {
      if (!ts.isPropertyAssignment(prop)) {
        continue;
      }
      const key = propertyName(prop.name);
      const value = unwrap(prop.initializer);
      switch (key) {
        case 'path':
          route.path = stringValue(value) ?? '';
          break;
        case 'component':
          if (ts.isIdentifier(value)) {
            route.component = value.text;
          }
          break;
        case 'redirectTo':
          route.redirectTo = stringValue(value);
          break;
        case 'title': {
          const title = stringValue(value);
          if (title !== undefined) {
            route.title = title;
          }
          break;
        }
        case 'loadComponent': {
          const ref = lazyRef(value);
          if (ref) {
            route.loadComponent = ref;
          }
          break;
        }
        case 'loadChildren': {
          const ref = lazyRef(value);
          if (ref) {
            route.loadChildren = ref;
          }
          break;
        }
        case 'children':
          if (ts.isArrayLiteralExpression(value)) {
            route.children = parseRoutes(value, sf);
          }
          break;
        default:
          break;
      }
    }
    routes.push(route);
  }
  return routes;
}

/**
 * `() => import('./x').then(m => m.Y)` → `{importPath: './x', exportName: 'Y'}`;
 * `() => import('./x')` → không có exportName (file xuất mặc định hoặc một mảng route);
 * `() => loadRemoteModule({ remoteName: 'r', exposedModule: './M' }).then(m => m.Y)` → `remote:r/./M`.
 */
function lazyRef(fn: ts.Expression): LazyRef | undefined {
  if (!ts.isArrowFunction(fn) && !ts.isFunctionExpression(fn)) {
    return undefined;
  }
  let importPath: string | undefined;
  let exportName: string | undefined;
  const visit = (node: ts.Node): void => {
    if (ts.isCallExpression(node)) {
      if (node.expression.kind === ts.SyntaxKind.ImportKeyword) {
        importPath ??= stringValue(node.arguments[0]);
      } else if (ts.isIdentifier(node.expression) && node.expression.text === 'loadRemoteModule') {
        importPath ??= remotePath(node);
      } else if (ts.isPropertyAccessExpression(node.expression) && node.expression.name.text === 'then') {
        exportName ??= thenExport(node.arguments[0]);
      }
    }
    ts.forEachChild(node, visit);
  };
  visit(fn.body);
  return importPath === undefined ? undefined : { importPath, ...(exportName ? { exportName } : {}) };
}

function thenExport(callback: ts.Expression | undefined): string | undefined {
  if (!callback || !(ts.isArrowFunction(callback) || ts.isFunctionExpression(callback))) {
    return undefined;
  }
  const body = ts.isArrowFunction(callback) && !ts.isBlock(callback.body) ? unwrap(callback.body) : undefined;
  if (body && ts.isPropertyAccessExpression(body)) {
    return body.name.text;
  }
  if (body && ts.isIdentifier(body)) {
    return body.text; // `({ Y }) => Y`
  }
  return undefined;
}

function remotePath(call: ts.CallExpression): string | undefined {
  const first = call.arguments[0];
  const second = call.arguments[1];
  if (first && ts.isObjectLiteralExpression(first)) {
    let remote: string | undefined;
    let exposed: string | undefined;
    for (const prop of first.properties) {
      if (ts.isPropertyAssignment(prop)) {
        const key = propertyName(prop.name);
        if (key === 'remoteName') {
          remote = stringValue(prop.initializer);
        } else if (key === 'exposedModule') {
          exposed = stringValue(prop.initializer);
        }
      }
    }
    return remote && exposed ? `remote:${remote}/${exposed}` : undefined;
  }
  const remote = stringValue(first);
  const exposed = stringValue(second);
  return remote && exposed ? `remote:${remote}/${exposed}` : undefined;
}
