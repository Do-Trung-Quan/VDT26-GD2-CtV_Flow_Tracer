import ts from 'typescript';
import type { ClassFact, FieldFact, MethodFact, RouterRefFact } from '../facts.js';
import { extractBody, type BodyFacts, type TypeScope } from './method-call.extractor.js';
import { parseRouterRef } from './provider.extractor.js';
import { extractTemplate } from './template.extractor.js';
import {
  decoratorArgs,
  decoratorName,
  decoratorsOf,
  endLineOf,
  expressionName,
  firstTypeArgName,
  hasModifier,
  isStringLike,
  lineOf,
  propertyName,
  stringParts,
  stringValue,
  typeArgOf,
  typeName,
  unwrap
} from './ts-utils.js';

const CLASS_DECORATORS = new Set(['Component', 'Injectable', 'Directive', 'Pipe', 'NgModule']);

/** Class hoặc interface cấp file; các khai báo khác trả null. */
export function extractClass(node: ts.Statement, sf: ts.SourceFile): ClassFact | null {
  if (ts.isInterfaceDeclaration(node)) {
    return {
      name: node.name.text,
      kind: 'interface',
      exported: hasModifier(node, ts.SyntaxKind.ExportKeyword),
      isAbstract: true,
      line: lineOf(sf, node),
      endLine: endLineOf(sf, node),
      decorator: 'None',
      imports: [],
      declarations: [],
      bootstrap: [],
      routerRefs: [],
      implements: [],
      ...(node.heritageClauses ? { extends: firstHeritage(node.heritageClauses, ts.SyntaxKind.ExtendsKeyword) } : {}),
      fields: [],
      methods: node.members.filter(ts.isMethodSignature).map((m) => signature(m, sf))
    };
  }
  if (!ts.isClassDeclaration(node) || !node.name) {
    return null;
  }
  const fact: ClassFact = {
    name: node.name.text,
    kind: 'class',
    exported: hasModifier(node, ts.SyntaxKind.ExportKeyword),
    isAbstract: hasModifier(node, ts.SyntaxKind.AbstractKeyword),
    line: lineOf(sf, node),
    endLine: endLineOf(sf, node),
    decorator: 'None',
    imports: [],
    declarations: [],
    bootstrap: [],
    routerRefs: [],
    implements: [],
    fields: [],
    methods: []
  };
  const parent = firstHeritage(node.heritageClauses, ts.SyntaxKind.ExtendsKeyword);
  if (parent) {
    fact.extends = parent;
  }
  fact.implements = allHeritage(node.heritageClauses, ts.SyntaxKind.ImplementsKeyword);
  applyDecorators(node, sf, fact);
  collectMembers(node, sf, fact);
  return fact;
}

function firstHeritage(clauses: readonly ts.HeritageClause[] | undefined, kind: ts.SyntaxKind): string | undefined {
  return allHeritage(clauses, kind)[0];
}

function allHeritage(clauses: readonly ts.HeritageClause[] | undefined, kind: ts.SyntaxKind): string[] {
  return (clauses ?? [])
    .filter((c) => c.token === kind)
    .flatMap((c) => c.types.map((t) => expressionName(t.expression)))
    .filter((n): n is string => n !== undefined);
}

// ---------------------------------------------------------------------------------------------------------------------
// Decorator
// ---------------------------------------------------------------------------------------------------------------------

function applyDecorators(node: ts.ClassDeclaration, sf: ts.SourceFile, fact: ClassFact): void {
  for (const decorator of decoratorsOf(node)) {
    const name = decoratorName(decorator);
    if (!name || !CLASS_DECORATORS.has(name)) {
      continue;
    }
    fact.decorator = name as ClassFact['decorator'];
    const arg = decoratorArgs(decorator)[0];
    const meta = arg && ts.isObjectLiteralExpression(unwrap(arg)) ? (unwrap(arg) as ts.ObjectLiteralExpression) : undefined;
    if (!meta) {
      return;
    }
    for (const prop of meta.properties) {
      if (!ts.isPropertyAssignment(prop)) {
        continue;
      }
      const key = propertyName(prop.name);
      const value = unwrap(prop.initializer);
      switch (key) {
        case 'selector':
          fact.selector = stringValue(value);
          break;
        case 'standalone':
          fact.standalone = value.kind === ts.SyntaxKind.TrueKeyword ? true : value.kind === ts.SyntaxKind.FalseKeyword ? false : undefined;
          break;
        case 'templateUrl':
          fact.templateUrl = stringValue(value);
          break;
        case 'template':
          if (ts.isNoSubstitutionTemplateLiteral(value) || ts.isStringLiteral(value)) {
            const parsed = extractTemplate(value.text, `${fact.name}.template`, lineOf(sf, value) - 1);
            fact.inlineTemplate = { nodes: parsed.nodes, parseErrors: parsed.errors, ...(parsed.heading ? { heading: parsed.heading } : {}) };
          }
          break;
        case 'imports':
          readImports(value, sf, fact);
          break;
        case 'declarations':
          if (ts.isArrayLiteralExpression(value)) {
            fact.declarations = identifiers(value);
          }
          break;
        case 'bootstrap':
          if (ts.isArrayLiteralExpression(value)) {
            fact.bootstrap = identifiers(value);
          }
          break;
        default:
          break;
      }
    }
  }
}

function readImports(value: ts.Expression, sf: ts.SourceFile, fact: ClassFact): void {
  if (!ts.isArrayLiteralExpression(value)) {
    return;
  }
  fact.imports = identifiers(value);
  const refs: RouterRefFact[] = [];
  const visit = (node: ts.Node): void => {
    if (ts.isCallExpression(node)) {
      const ref = parseRouterRef(node, sf);
      if (ref) {
        refs.push(ref);
        return;
      }
    }
    ts.forEachChild(node, visit);
  };
  visit(value);
  fact.routerRefs = refs;
}

/** Tên định danh trong mảng (kể cả `forwardRef(() => X)`); phần tử khác (gọi hàm, spread) bỏ qua. */
function identifiers(array: ts.ArrayLiteralExpression): string[] {
  const out: string[] = [];
  for (const element of array.elements) {
    const node = unwrap(element);
    if (ts.isIdentifier(node)) {
      out.push(node.text);
    } else if (ts.isCallExpression(node) && ts.isIdentifier(node.expression) && node.expression.text === 'forwardRef') {
      const arrow = node.arguments[0];
      if (arrow && ts.isArrowFunction(arrow) && !ts.isBlock(arrow.body) && ts.isIdentifier(unwrap(arrow.body))) {
        out.push((unwrap(arrow.body) as ts.Identifier).text);
      }
    } else if (ts.isPropertyAccessExpression(node)) {
      out.push(node.name.text);
    }
  }
  return out;
}

// ---------------------------------------------------------------------------------------------------------------------
// Thành viên
// ---------------------------------------------------------------------------------------------------------------------

function collectMembers(node: ts.ClassDeclaration, sf: ts.SourceFile, fact: ClassFact): void {
  // Pass 1: field và tham số constructor kiểu property — để biết kiểu của `this.x` khi phân tích thân method
  const fieldTypes = new Map<string, string | undefined>();
  for (const member of node.members) {
    if (ts.isPropertyDeclaration(member)) {
      const field = propertyField(member, sf);
      if (field) {
        fact.fields.push(field);
        fieldTypes.set(field.name, field.typeName ?? field.injectArg);
      }
    } else if (ts.isConstructorDeclaration(member)) {
      for (const param of member.parameters) {
        const field = parameterField(param, sf);
        if (field) {
          fact.fields.push(field);
          fieldTypes.set(field.name, field.typeName ?? field.injectArg);
        }
      }
    }
  }
  // Khởi tạo field chạy trước thân constructor: lời gọi trong đó (`items = this.facade.load()`) thuộc "constructor"
  const initCalls = fieldInitializerBody(node, sf, fieldTypes);
  // Pass 2: thân method
  for (const member of node.members) {
    if (ts.isMethodDeclaration(member) && member.name) {
      fact.methods.push(methodFact(member, propertyName(member.name) ?? '', 'method', fieldTypes, sf));
    } else if (ts.isConstructorDeclaration(member)) {
      fact.methods.push(methodFact(member, 'constructor', 'constructor', fieldTypes, sf));
    } else if (ts.isGetAccessorDeclaration(member)) {
      fact.methods.push(methodFact(member, propertyName(member.name) ?? '', 'accessor', fieldTypes, sf));
    } else if (ts.isPropertyDeclaration(member) && member.initializer) {
      const init = unwrap(member.initializer);
      if (ts.isArrowFunction(init) || ts.isFunctionExpression(init)) {
        fact.methods.push(methodFact(init, propertyName(member.name) ?? '', 'arrow-property', fieldTypes, sf, member));
      }
    }
  }
  mergeFieldInitializers(fact, initCalls, node, sf);
}

/** Lời gọi trong khởi tạo các field không phải hàm (`readonly items = this.facade.list()`), theo thứ tự khai báo. */
function fieldInitializerBody(node: ts.ClassDeclaration, sf: ts.SourceFile, fieldTypes: ReadonlyMap<string, string | undefined>): BodyFacts {
  const merged: BodyFacts = { calls: [], httpCalls: [], uiActions: [] };
  for (const member of node.members) {
    if (!ts.isPropertyDeclaration(member) || !member.initializer || hasModifier(member, ts.SyntaxKind.StaticKeyword)) {
      continue;
    }
    const init = unwrap(member.initializer);
    if (ts.isArrowFunction(init) || ts.isFunctionExpression(init)) {
      continue;
    }
    const body = extractBody(init, sf, { fields: fieldTypes, params: new Map() });
    const shift = merged.calls.length + merged.httpCalls.length + merged.uiActions.length;
    merged.calls.push(...body.calls.map((c) => ({ ...c, callOrder: c.callOrder + shift })));
    merged.httpCalls.push(...body.httpCalls.map((c) => ({ ...c, callOrder: c.callOrder + shift })));
    merged.uiActions.push(...body.uiActions.map((c) => ({ ...c, callOrder: c.callOrder + shift })));
  }
  return merged;
}

/** Đưa lời gọi của khởi tạo field vào đầu constructor (tạo constructor giả nếu class không khai báo). */
function mergeFieldInitializers(fact: ClassFact, init: BodyFacts, node: ts.ClassDeclaration, sf: ts.SourceFile): void {
  const count = init.calls.length + init.httpCalls.length + init.uiActions.length;
  if (count === 0) {
    return;
  }
  let ctor = fact.methods.find((m) => m.kind === 'constructor');
  if (!ctor) {
    ctor = {
      name: 'constructor',
      kind: 'constructor',
      params: [],
      isStatic: false,
      startLine: lineOf(sf, node),
      endLine: lineOf(sf, node),
      calls: [],
      httpCalls: [],
      uiActions: []
    };
    fact.methods.push(ctor);
  }
  const shift = (list: { callOrder: number }[]): void => list.forEach((c) => (c.callOrder += count));
  shift(ctor.calls);
  shift(ctor.httpCalls);
  shift(ctor.uiActions);
  ctor.calls.unshift(...init.calls);
  ctor.httpCalls.unshift(...init.httpCalls);
  ctor.uiActions.unshift(...init.uiActions);
}

function propertyField(member: ts.PropertyDeclaration, sf: ts.SourceFile): FieldFact | null {
  const name = propertyName(member.name);
  if (!name) {
    return null;
  }
  const field: FieldFact = {
    name,
    isStatic: hasModifier(member, ts.SyntaxKind.StaticKeyword),
    line: lineOf(sf, member)
  };
  const declared = typeName(member.type);
  if (declared) {
    field.typeName = declared;
  }
  const injectDecorator = decoratorsOf(member).find((d) => decoratorName(d) === 'Inject');
  if (injectDecorator) {
    const arg = decoratorArgs(injectDecorator)[0];
    if (arg && ts.isIdentifier(arg)) {
      field.injectArg = arg.text;
    }
  }
  if (member.initializer) {
    const init = unwrap(member.initializer);
    if (ts.isCallExpression(init) && ts.isIdentifier(init.expression) && init.expression.text === 'inject') {
      const arg = init.arguments[0];
      const generic = firstTypeArgName(init.typeArguments);
      if (arg && ts.isIdentifier(unwrap(arg))) {
        field.injectArg = (unwrap(arg) as ts.Identifier).text;
      }
      if (!field.typeName && generic) {
        field.typeName = generic;
      }
    } else if (!ts.isArrowFunction(init) && !ts.isFunctionExpression(init)) {
      const parts = stringParts(init, sf);
      if (isStringLike(parts)) {
        field.init = parts;
      }
    }
  }
  return field;
}

function parameterField(param: ts.ParameterDeclaration, sf: ts.SourceFile): FieldFact | null {
  const isProperty = (ts.getModifiers(param) ?? []).some((m) =>
    [ts.SyntaxKind.PrivateKeyword, ts.SyntaxKind.PublicKeyword, ts.SyntaxKind.ProtectedKeyword, ts.SyntaxKind.ReadonlyKeyword].includes(m.kind)
  );
  if (!isProperty || !ts.isIdentifier(param.name)) {
    return null;
  }
  const field: FieldFact = { name: param.name.text, isStatic: false, line: lineOf(sf, param) };
  const declared = typeName(param.type);
  if (declared) {
    field.typeName = declared;
  }
  const injectDecorator = decoratorsOf(param).find((d) => decoratorName(d) === 'Inject');
  if (injectDecorator) {
    const arg = decoratorArgs(injectDecorator)[0];
    if (arg && ts.isIdentifier(arg)) {
      field.injectArg = arg.text;
    }
  }
  return field;
}

function methodFact(
  node: ts.FunctionLikeDeclaration,
  name: string,
  kind: MethodFact['kind'],
  fieldTypes: ReadonlyMap<string, string | undefined>,
  sf: ts.SourceFile,
  lineNode: ts.Node = node
): MethodFact {
  const params = node.parameters
    .filter((p) => ts.isIdentifier(p.name))
    .map((p) => ({ name: (p.name as ts.Identifier).text, typeName: typeName(p.type) }));
  const scope: TypeScope = { fields: fieldTypes, params: new Map(params.map((p) => [p.name, p.typeName])) };
  const body = extractBody(node.body, sf, scope);
  return {
    name,
    kind,
    params: params.map((p) => (p.typeName ? p : { name: p.name })),
    ...(typeName(node.type) ? { returnTypeName: typeName(node.type) } : {}),
    ...(typeArgOf(node.type) ? { returnTypeArg: typeArgOf(node.type) } : {}),
    isStatic: hasModifier(node, ts.SyntaxKind.StaticKeyword),
    startLine: lineOf(sf, lineNode),
    endLine: endLineOf(sf, node),
    ...body
  };
}

function signature(node: ts.MethodSignature, sf: ts.SourceFile): MethodFact {
  const params = node.parameters
    .filter((p) => ts.isIdentifier(p.name))
    .map((p) => {
      const t = typeName(p.type);
      return t ? { name: (p.name as ts.Identifier).text, typeName: t } : { name: (p.name as ts.Identifier).text };
    });
  return {
    name: propertyName(node.name) ?? '',
    kind: 'method',
    params,
    ...(typeName(node.type) ? { returnTypeName: typeName(node.type) } : {}),
    ...(typeArgOf(node.type) ? { returnTypeArg: typeArgOf(node.type) } : {}),
    isStatic: false,
    startLine: lineOf(sf, node),
    endLine: endLineOf(sf, node),
    calls: [],
    httpCalls: [],
    uiActions: []
  };
}
