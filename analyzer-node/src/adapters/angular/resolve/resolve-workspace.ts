import type { Diagnostic, IrFragment, LinkRule } from '../../../protocol/generated/ir-fragment.js';
import type { ResolveContext } from '../../adapter.js';
import { parseProxyConfig } from '../config/proxy-config.reader.js';
import type { AngularFacts, FederationFacts, JsonFacts, TemplateFacts, TsFacts } from '../facts.js';
import { projectOf, readAngularProjects, type AngularProject } from '../workspace/angular-json.reader.js';
import { readFederation } from '../workspace/federation.reader.js';
import { readTsconfigPaths } from '../workspace/tsconfig-paths.reader.js';
import { CallResolver } from './call.resolver.js';
import { DiResolver } from './di.resolver.js';
import { LabelResolver, type Dictionary } from './label.resolver.js';
import { RouteTreeBuilder } from './route-tree.builder.js';
import { GraphBuilder } from './screen-tree.builder.js';
import { SymbolTable, type ClassRef } from './symbol-table.js';
import { UrlEvaluator } from './url-evaluator.js';

/**
 * PHA 2: ghép dữ kiện mọi file của một Source Angular thành IR:
 * `angular.json` → ứng dụng; route → màn hình; template → tab / form / hộp thoại / phần tử; sự kiện → hàm xử lý →
 * facade / repository (qua DI và InjectionToken) → lời gọi HTTP.
 */
export async function resolveWorkspace(facts: AngularFacts[], ctx: ResolveContext): Promise<IrFragment> {
  void ctx;
  const sorted = [...facts].sort((a, b) => a.path.localeCompare(b.path));
  const ts = new Map<string, TsFacts>();
  const templates = new Map<string, TemplateFacts>();
  const json: JsonFacts[] = [];
  const federationFacts: FederationFacts[] = [];
  const diagnostics: Diagnostic[] = [];
  for (const f of sorted) {
    switch (f.kind) {
      case 'ts':
        ts.set(f.path, f);
        break;
      case 'template':
        templates.set(f.path, f);
        break;
      case 'json':
        json.push(f);
        break;
      case 'federation':
        federationFacts.push(f);
        break;
      default:
        break;
    }
    for (const err of f.parseErrors) {
      diagnostics.push({ severity: 'ERROR', filePath: f.path, ...(err.line ? { line: err.line } : {}), message: err.message });
    }
  }
  for (const f of ts.values()) {
    for (const cls of f.classes) {
      for (const err of cls.inlineTemplate?.parseErrors ?? []) {
        diagnostics.push({ severity: 'ERROR', filePath: f.path, ...(err.line ? { line: err.line } : {}), message: err.message });
      }
    }
  }

  const projects = readAngularProjects(json);
  const groupOf = (file: string): string => projectOf(projects, file)?.name ?? '';
  const table = new SymbolTable(ts, readTsconfigPaths(json));
  const di = new DiResolver(table, groupOf);
  const calls = new CallResolver(table, di);
  const urls = new UrlEvaluator(table);
  const labels = new LabelResolver(dictionaries(ts, json));
  const federation = readFederation(federationFacts);
  const builder = new GraphBuilder({ table, di, calls, urls, labels, templates, selectors: selectorsOf(ts), groupOf });
  builder.diagnostics.push(...diagnostics);

  for (const project of projects.filter((p) => p.type === 'application')) {
    const files = [...ts.keys()].filter((p) => projectOf(projects, p)?.name === project.name);
    const screens = new RouteTreeBuilder(table, federation, builder.diagnostics).screensOf(files);
    builder.buildApp(
      { name: project.name, root: project.root, declaredIn: project.declaredIn },
      screens,
      chromeOf(project, files, table)
    );
  }

  return {
    adapterId: 'angular',
    nodes: [...builder.nodes.values()],
    edges: builder.edges,
    detectedLinkRules: proxyRules(json, projects),
    diagnostics: builder.diagnostics
  };
}

/** Selector kiểu thẻ (`app-pager`) → component. Selector thuộc tính (`[appX]`) và selector ghép bị bỏ qua. */
function selectorsOf(ts: ReadonlyMap<string, TsFacts>): Map<string, ClassRef[]> {
  const map = new Map<string, ClassRef[]>();
  for (const [file, facts] of ts) {
    for (const cls of facts.classes) {
      if (cls.decorator !== 'Component' || !cls.selector) {
        continue;
      }
      for (const selector of cls.selector.split(',').map((s) => s.trim())) {
        if (/^[a-z][\w-]*$/i.test(selector)) {
          const list = map.get(selector) ?? [];
          list.push({ file, cls });
          map.set(selector, list);
        }
      }
    }
  }
  return map;
}

/** Component khung ngoài cùng của ứng dụng: class đầu tiên trong `bootstrapApplication(X)` / `@NgModule({bootstrap})`. */
function chromeOf(project: AngularProject, files: readonly string[], table: SymbolTable): ClassRef | undefined {
  for (const file of files) {
    const facts = table.files.get(file);
    for (const name of facts?.bootstraps ?? []) {
      const symbol = table.lookup(file, name);
      if (symbol?.kind !== 'class') {
        continue;
      }
      if (symbol.cls.decorator === 'Component') {
        return { file: symbol.file, cls: symbol.cls };
      }
      const first = symbol.cls.bootstrap[0];
      const root = first ? table.lookup(symbol.file, first) : undefined;
      if (root?.kind === 'class') {
        return { file: root.file, cls: root.cls };
      }
    }
  }
  void project;
  return undefined;
}

function dictionaries(ts: ReadonlyMap<string, TsFacts>, json: readonly JsonFacts[]): Dictionary[] {
  const out: Dictionary[] = [];
  for (const [file, facts] of ts) {
    for (const d of facts.dictionaries) {
      out.push({ file, entries: d.entries });
    }
  }
  for (const f of json) {
    if (f.role === 'i18n' && f.entries) {
      out.push({ file: f.path, entries: f.entries });
    }
  }
  return out;
}

/** Luật `REWRITE` từ `pathRewrite` trong proxy.conf.json (JSON thuần; không chạy proxy.conf.js). */
function proxyRules(json: readonly JsonFacts[], projects: readonly AngularProject[]): LinkRule[] {
  const rules: LinkRule[] = [];
  const seen = new Set<string>();
  for (const file of json.filter((f) => f.role === 'proxy' && f.content !== undefined)) {
    const project = projectOf(projects, file.path);
    for (const rule of parseProxyConfig(JSON.stringify(file.content))) {
      for (const [pattern, replacement] of Object.entries(rule.pathRewrite).sort(([a], [b]) => a.localeCompare(b))) {
        const key = `${pattern}→${replacement}`;
        if (seen.has(key)) {
          continue;
        }
        seen.add(key);
        rules.push({
          type: 'REWRITE',
          pattern,
          replacement,
          origin: 'AUTO',
          description: `${file.path}: proxy "${rule.context}"${project ? ` của ứng dụng ${project.name}` : ''}`
        });
      }
    }
  }
  return rules;
}
