import type { LazyRef, RouteArrayFact, RouteFact, RouterRefFact } from '../facts.js';
import type { Diagnostic } from '../../../protocol/generated/ir-fragment.js';
import type { FederationMap } from '../workspace/federation.reader.js';
import type { SymbolTable, ClassRef } from './symbol-table.js';

/** Một màn hình: component được gắn vào một đường dẫn route. */
export interface ScreenRoute {
  /** Đường dẫn đầy đủ, bắt đầu bằng '/'; route gốc là '/'. */
  fullPath: string;
  component: ClassRef;
  title?: string;
  /** Nơi khai báo route (để ghi vị trí cho cạnh CONTAINS). */
  routeFile: string;
  routeLine: number;
}

interface RouteSet {
  id: string;
  routes: readonly RouteFact[];
  file: string;
}

const MAX_MODULE_DEPTH = 4;
const MAX_ROUTE_DEPTH = 12;

/**
 * Dựng danh sách màn hình của một ứng dụng từ khai báo route: gốc là `provideRouter(...)` / `RouterModule.forRoot(...)`
 * (nếu không có thì các mảng route không được route nào khác trỏ tới), đi theo `children`, `loadChildren` (file routes,
 * NgModule với `RouterModule.forChild`, microfrontend qua `federation.config.js`) và `loadComponent`.
 */
export class RouteTreeBuilder {
  constructor(
    private readonly table: SymbolTable,
    private readonly federation: FederationMap,
    private readonly diagnostics: Diagnostic[]
  ) {}

  screensOf(appFiles: readonly string[]): ScreenRoute[] {
    const files = [...appFiles].sort();
    const roots = this.rootsOf(files);
    const screens: ScreenRoute[] = [];
    for (const root of roots) {
      this.walk(root, '', screens, new Set([root.id]), 0);
    }
    return screens;
  }

  // -------------------------------------------------------------------------------------------------------------------

  private rootsOf(files: readonly string[]): RouteSet[] {
    const explicit: RouteSet[] = [];
    const candidates: RouteSet[] = [];
    // `RouterModule.forChild(...)` trong NgModule: nếu module đó được import trực tiếp (nạp sớm) thì route của nó nằm ở GỐC
    // cấu hình route (không có tiền tố), như Angular gộp mọi ROUTES của các module đã import
    const children: RouteSet[] = [];
    for (const file of files) {
      const facts = this.table.files.get(file);
      if (!facts) {
        continue;
      }
      for (const array of facts.routeArrays) {
        candidates.push(fromArray(file, array));
      }
      for (const ref of facts.routerRefs) {
        const set = this.fromRef(file, ref);
        if (set) {
          explicit.push(set);
        }
      }
      for (const cls of facts.classes) {
        for (const ref of cls.routerRefs) {
          const set = this.fromRef(file, ref);
          if (set) {
            (ref.kind === 'forRoot' ? explicit : candidates).push(set);
            if (ref.kind === 'forChild') {
              children.push(set);
            }
          }
        }
      }
    }
    // route nào được nạp lười (loadChildren / children) từ một tập route khác thì không phải gốc
    const referenced = new Set<string>();
    for (const set of [...explicit, ...candidates]) {
      this.collectReferenced(set, referenced, new Set(), 0);
    }
    if (explicit.length > 0) {
      // ứng dụng NgModule: ngoài `forRoot`, các NgModule con nạp sớm đóng góp route ở gốc
      return dedupe([...explicit, ...children.filter((c) => !referenced.has(c.id))]);
    }
    // không có provideRouter / forRoot: lấy các mảng route không được route nào khác trỏ tới
    return dedupe(candidates.filter((c) => !referenced.has(c.id)));
  }

  private collectReferenced(set: RouteSet, out: Set<string>, seen: Set<string>, depth: number): void {
    if (depth > MAX_ROUTE_DEPTH || seen.has(set.id)) {
      return;
    }
    seen.add(set.id);
    const visit = (routes: readonly RouteFact[]): void => {
      for (const route of routes) {
        if (route.children) {
          visit(route.children);
        }
        if (route.loadChildren) {
          for (const target of this.resolveLazy(set.file, route.loadChildren, true)) {
            out.add(target.id);
            this.collectReferenced(target, out, seen, depth + 1);
          }
        }
      }
    };
    visit(set.routes);
  }

  private walk(set: RouteSet, prefix: string, out: ScreenRoute[], stack: Set<string>, depth: number): void {
    if (depth > MAX_ROUTE_DEPTH) {
      return;
    }
    this.walkRoutes(set.routes, set, prefix, out, stack, depth);
  }

  private walkRoutes(
    routes: readonly RouteFact[],
    set: RouteSet,
    prefix: string,
    out: ScreenRoute[],
    stack: Set<string>,
    depth: number
  ): void {
    for (const route of routes) {
      const full = joinRoute(prefix, route.path);
      const component = this.componentOf(route, set.file);
      if (component) {
        out.push({
          fullPath: full,
          component,
          ...(route.title ? { title: route.title } : {}),
          routeFile: set.file,
          routeLine: route.line
        });
      } else if (route.component || route.loadComponent) {
        const lazy = route.loadComponent;
        if (lazy && this.targetFile(set.file, lazy)) {
          // file tồn tại nhưng không xuất class component: thường là hàm nạp microfrontend (màn hình thật ở ứng dụng khác)
          this.diagnostics.push({
            severity: 'INFO',
            filePath: set.file,
            line: route.line,
            message: `Route "${route.path}" nạp component qua ${lazy.importPath}; không đọc được component đích (có thể là microfrontend nằm ở ứng dụng khác)`
          });
        } else {
          const from = lazy ? ` (nạp từ ${lazy.importPath})` : '';
          this.warn(set.file, route.line, `Không tìm thấy component của route "${route.path}"${from}`);
        }
      }
      if (route.children) {
        this.walkRoutes(route.children, set, full, out, stack, depth + 1);
      }
      if (route.loadChildren) {
        for (const target of this.resolveLazy(set.file, route.loadChildren, false, route.line)) {
          if (stack.has(target.id)) {
            continue;
          }
          this.walk(target, full, out, new Set(stack).add(target.id), depth + 1);
        }
      }
    }
  }

  private componentOf(route: RouteFact, file: string): ClassRef | undefined {
    if (route.component) {
      const symbol = this.table.lookup(file, route.component);
      return symbol?.kind === 'class' ? { file: symbol.file, cls: symbol.cls } : undefined;
    }
    if (route.loadComponent) {
      const target = this.targetFile(file, route.loadComponent);
      return target ? this.exportedComponent(target, route.loadComponent.exportName) : undefined;
    }
    return undefined;
  }

  private exportedComponent(file: string, exportName: string | undefined): ClassRef | undefined {
    if (exportName && exportName !== 'default') {
      const symbol = this.table.exported(file, exportName, new Set(), 0);
      return symbol?.kind === 'class' ? { file: symbol.file, cls: symbol.cls } : undefined;
    }
    const cls = this.table.files.get(file)?.classes.find((c) => c.decorator === 'Component' && c.exported);
    return cls ? { file, cls } : undefined;
  }

  private targetFile(from: string, lazy: LazyRef): string | undefined {
    if (lazy.importPath.startsWith('remote:')) {
      const [remote, ...rest] = lazy.importPath.slice('remote:'.length).split('/');
      return remote ? this.federation.get(remote)?.get(rest.join('/')) : undefined;
    }
    return this.table.resolveModule(from, lazy.importPath) ?? undefined;
  }

  /** `loadChildren` → các tập route nằm trong đích (file routes, NgModule). */
  private resolveLazy(from: string, lazy: LazyRef, quiet: boolean, line = 1): RouteSet[] {
    const file = this.targetFile(from, lazy);
    if (!file) {
      if (!quiet && !lazy.importPath.startsWith('remote:')) {
        this.warn(from, line, `Không tìm thấy file route được nạp lười: ${lazy.importPath}`);
      }
      return [];
    }
    const name = lazy.exportName;
    if (name && name !== 'default') {
      const symbol = this.table.exported(file, name, new Set(), 0);
      if (symbol?.kind === 'routes') {
        return [fromArray(symbol.file, symbol.array)];
      }
      if (symbol?.kind === 'class') {
        return this.routesOfModule({ file: symbol.file, cls: symbol.cls }, 0, new Set());
      }
      return [];
    }
    const facts = this.table.files.get(file);
    const sets: RouteSet[] = [];
    for (const array of facts?.routeArrays.filter((a) => a.exported) ?? []) {
      sets.push(fromArray(file, array));
    }
    for (const cls of facts?.classes.filter((c) => c.decorator === 'NgModule') ?? []) {
      sets.push(...this.routesOfModule({ file, cls }, 0, new Set()));
    }
    return dedupe(sets);
  }

  private routesOfModule(module: ClassRef, depth: number, seen: Set<string>): RouteSet[] {
    const key = `${module.file}#${module.cls.name}`;
    if (depth > MAX_MODULE_DEPTH || seen.has(key)) {
      return [];
    }
    seen.add(key);
    const sets: RouteSet[] = [];
    for (const ref of module.cls.routerRefs) {
      const set = this.fromRef(module.file, ref);
      if (set) {
        sets.push(set);
      }
    }
    for (const name of module.cls.imports) {
      const symbol = this.table.lookup(module.file, name);
      if (symbol?.kind === 'class' && symbol.cls.decorator === 'NgModule') {
        sets.push(...this.routesOfModule({ file: symbol.file, cls: symbol.cls }, depth + 1, seen));
      }
    }
    return dedupe(sets);
  }

  private fromRef(file: string, ref: RouterRefFact): RouteSet | undefined {
    if (ref.inline) {
      return { id: `${file}#inline@${ref.line}`, routes: ref.inline, file };
    }
    if (ref.ref) {
      const symbol = this.table.lookup(file, ref.ref);
      if (symbol?.kind === 'routes') {
        return fromArray(symbol.file, symbol.array);
      }
    }
    return undefined;
  }

  private warn(file: string, line: number, message: string): void {
    this.diagnostics.push({ severity: 'WARNING', filePath: file, line, message });
  }
}

function fromArray(file: string, array: RouteArrayFact): RouteSet {
  return { id: `${file}#${array.name ?? array.line}`, routes: array.routes, file };
}

function dedupe(sets: RouteSet[]): RouteSet[] {
  const seen = new Set<string>();
  return sets.filter((s) => !seen.has(s.id) && seen.add(s.id));
}

/** Ghép tiền tố với đoạn route: `'/categories' + ':id'` → `/categories/:id`; rỗng → `/`. */
export function joinRoute(prefix: string, path: string): string {
  const segments = [...prefix.split('/'), ...path.split('/')].filter((s) => s.length > 0);
  return '/' + segments.join('/');
}
