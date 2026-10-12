import ts from 'typescript';
import type { InputFile } from '../../adapter.js';
import type {
  AngularFacts,
  ImportFact,
  JsonFacts,
  ParseError,
  ProviderFact,
  ReexportFact,
  RouteArrayFact,
  RouterRefFact,
  TemplateFacts,
  TokenFact,
  TsFacts
} from '../facts.js';
import { extractClass } from './class-decl.extractor.js';
import { extractConstants, extractDictionary } from './constant.extractor.js';
import { federationFacts, i18nJsonFacts, isViI18nJson, stripBom } from './i18n.extractor.js';
import { parseProvider, parseRouterRef } from './provider.extractor.js';
import { looksLikeRoutes, parseRoutes } from './route.extractor.js';
import { extractTemplate } from './template.extractor.js';
import { firstTypeArgName, hasModifier, lineOf, unwrap } from './ts-utils.js';

/** File JSON nhỏ giữ nguyên nội dung (angular.json, tsconfig, proxy). */
const MAX_JSON_CONTENT = 512 * 1024;

/** Phân loại file và gọi bộ đọc tương ứng. Không ném lỗi vì file sai cú pháp: lỗi được ghi vào `parseErrors`. */
export async function extractFile(file: InputFile): Promise<AngularFacts> {
  const { path, hash, content } = file;
  const base = path.split('/').pop() ?? path;
  try {
    if (path.endsWith('.ts')) {
      return extractTs(path, hash, content);
    }
    if (path.endsWith('.html')) {
      const parsed = extractTemplate(content, path);
      return {
        kind: 'template',
        path,
        hash,
        nodes: parsed.nodes,
        parseErrors: parsed.errors,
        ...(parsed.heading ? { heading: parsed.heading } : {})
      } satisfies TemplateFacts;
    }
    if (path.endsWith('.json')) {
      return extractJson(path, hash, base, content);
    }
    if (/^federation\.config\.(js|cjs|mjs)$/.test(base)) {
      return federationFacts(path, hash, content);
    }
    return { kind: 'other', path, hash, parseErrors: [] };
  } catch (e) {
    return { kind: 'other', path, hash, parseErrors: [{ message: `Bộ phân tích gặp lỗi: ${(e as Error).message}` }] };
  }
}

function extractJson(path: string, hash: string, base: string, content: string): JsonFacts {
  const role: JsonFacts['role'] =
    base === 'angular.json'
      ? 'angular'
      : /^tsconfig.*\.json$/.test(base)
        ? 'tsconfig'
        : /^proxy.*\.json$/.test(base)
          ? 'proxy'
          : base === 'package.json'
            ? 'package'
            : isViI18nJson(path)
              ? 'i18n'
              : 'other';
  if (role === 'i18n') {
    return i18nJsonFacts(path, hash, content);
  }
  if (role === 'other') {
    return { kind: 'json', role, path, hash, parseErrors: [] };
  }
  // angular.json / tsconfig có thể có chú thích: dùng bộ đọc của TypeScript
  const parsed = ts.parseConfigFileTextToJson(path, stripBom(content));
  if (parsed.error) {
    const message = ts.flattenDiagnosticMessageText(parsed.error.messageText, '\n');
    return { kind: 'json', role, path, hash, parseErrors: [{ message: `JSON không hợp lệ: ${message}` }] };
  }
  if (content.length > MAX_JSON_CONTENT) {
    return { kind: 'json', role, path, hash, parseErrors: [] };
  }
  return { kind: 'json', role, path, hash, content: parsed.config as unknown, parseErrors: [] };
}

// ---------------------------------------------------------------------------------------------------------------------

function extractTs(path: string, hash: string, content: string): TsFacts {
  const sf = ts.createSourceFile(path, content, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS);
  const facts: TsFacts = {
    kind: 'ts',
    path,
    hash,
    parseErrors: syntaxErrors(sf),
    imports: [],
    reexports: [],
    classes: [],
    routeArrays: [],
    routerRefs: [],
    providers: [],
    constants: [],
    dictionaries: [],
    tokens: [],
    bootstraps: []
  };
  for (const statement of sf.statements) {
    collectStatement(statement, sf, path, facts);
  }
  collectWholeFile(sf, facts);
  return facts;
}

function syntaxErrors(sf: ts.SourceFile): ParseError[] {
  const diagnostics = (sf as unknown as { parseDiagnostics?: ts.DiagnosticWithLocation[] }).parseDiagnostics ?? [];
  return diagnostics.slice(0, 3).map((d) => ({
    message: `Lỗi cú pháp TypeScript: ${ts.flattenDiagnosticMessageText(d.messageText, ' ')}`,
    line: sf.getLineAndCharacterOfPosition(d.start).line + 1
  }));
}

function collectStatement(statement: ts.Statement, sf: ts.SourceFile, path: string, facts: TsFacts): void {
  if (ts.isImportDeclaration(statement)) {
    facts.imports.push(...importFacts(statement));
    return;
  }
  if (ts.isExportDeclaration(statement) && statement.moduleSpecifier && ts.isStringLiteral(statement.moduleSpecifier)) {
    facts.reexports.push(reexportFact(statement, statement.moduleSpecifier.text));
    return;
  }
  const cls = extractClass(statement, sf);
  if (cls) {
    facts.classes.push(cls);
    return;
  }
  facts.constants.push(...extractConstants(statement, sf));
  const dictionary = extractDictionary(statement, path);
  if (dictionary) {
    facts.dictionaries.push(dictionary);
  }
  if (ts.isVariableStatement(statement)) {
    for (const decl of statement.declarationList.declarations) {
      collectVariable(decl, hasModifier(statement, ts.SyntaxKind.ExportKeyword), sf, facts);
    }
  }
  if (ts.isExpressionStatement(statement)) {
    collectBootstrap(statement.expression, facts);
  }
}

function importFacts(node: ts.ImportDeclaration): ImportFact[] {
  if (!ts.isStringLiteral(node.moduleSpecifier) || !node.importClause) {
    return [];
  }
  const from = node.moduleSpecifier.text;
  const clause = node.importClause;
  const out: ImportFact[] = [];
  if (clause.name) {
    out.push({ local: clause.name.text, imported: 'default', from });
  }
  const bindings = clause.namedBindings;
  if (bindings && ts.isNamespaceImport(bindings)) {
    out.push({ local: bindings.name.text, imported: '*', from });
  } else if (bindings && ts.isNamedImports(bindings)) {
    for (const element of bindings.elements) {
      out.push({ local: element.name.text, imported: (element.propertyName ?? element.name).text, from });
    }
  }
  return out;
}

function reexportFact(node: ts.ExportDeclaration, from: string): ReexportFact {
  const clause = node.exportClause;
  if (clause && ts.isNamedExports(clause)) {
    return {
      from,
      names: clause.elements.map((e) => ({ local: (e.propertyName ?? e.name).text, exported: e.name.text }))
    };
  }
  return { from, names: null };
}

function collectVariable(decl: ts.VariableDeclaration, exported: boolean, sf: ts.SourceFile, facts: TsFacts): void {
  if (!ts.isIdentifier(decl.name) || !decl.initializer) {
    return;
  }
  const init = unwrap(decl.initializer);
  if (ts.isNewExpression(init) && ts.isIdentifier(init.expression) && init.expression.text === 'InjectionToken') {
    const token: TokenFact = { name: decl.name.text, line: lineOf(sf, decl) };
    const generic = firstTypeArgName(init.typeArguments);
    if (generic) {
      token.typeName = generic;
    }
    facts.tokens.push(token);
    return;
  }
  if (ts.isArrayLiteralExpression(init) && looksLikeRoutes(init)) {
    const array: RouteArrayFact = {
      name: decl.name.text,
      exported,
      routes: parseRoutes(init, sf),
      line: lineOf(sf, decl)
    };
    facts.routeArrays.push(array);
  }
}

function collectBootstrap(expr: ts.Expression, facts: TsFacts): void {
  // bootstrapApplication(App, cfg) hoặc platformBrowserDynamic().bootstrapModule(AppModule)
  let call = unwrap(expr);
  while (ts.isCallExpression(call) && ts.isPropertyAccessExpression(call.expression) && ts.isCallExpression(call.expression.expression)) {
    call = call.expression.expression;
  }
  const direct = unwrap(expr);
  for (const candidate of [direct, call]) {
    if (!ts.isCallExpression(candidate)) {
      continue;
    }
    const callee = candidate.expression;
    const first = candidate.arguments[0];
    const isBootstrap =
      (ts.isIdentifier(callee) && callee.text === 'bootstrapApplication') ||
      (ts.isPropertyAccessExpression(callee) && callee.name.text === 'bootstrapModule');
    if (isBootstrap && first && ts.isIdentifier(first) && !facts.bootstraps.includes(first.text)) {
      facts.bootstraps.push(first.text);
    }
  }
}

/** Duyệt toàn file một lần để lấy các khai báo có thể nằm ở bất cứ đâu: provider, `provideRouter(...)`. */
function collectWholeFile(sf: ts.SourceFile, facts: TsFacts): void {
  const seenProviders = new Set<number>();
  const visit = (node: ts.Node): void => {
    if (ts.isObjectLiteralExpression(node)) {
      const provider: ProviderFact | null = parseProvider(node, sf);
      if (provider && !seenProviders.has(node.pos)) {
        seenProviders.add(node.pos);
        facts.providers.push(provider);
      }
    } else if (ts.isCallExpression(node)) {
      const ref: RouterRefFact | null = parseRouterRef(node, sf);
      if (ref && ref.kind === 'provideRouter') {
        facts.routerRefs.push(ref);
      }
    }
    ts.forEachChild(node, visit);
  };
  visit(sf);
}

