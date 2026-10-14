import type { ClassFact, MethodFact, TemplateFacts, TplElement, TplEvent, UiActionFact } from '../facts.js';
import type { Candidate, Confidence, Diagnostic, EdgeKind, Guard, IrEdge, IrNode, NodeKind, SourceLocation } from '../../../protocol/generated/ir-fragment.js';
import { NodeKeys } from '../../../protocol/node-keys.js';
import { dirname, joinPath } from '../workspace/paths.js';
import type { CallResolver, CallTarget } from './call.resolver.js';
import type { DiResolver } from './di.resolver.js';
import { assignElementIds, elementTypeOf, isFormTag, isInteractive, isTabTag } from './element.builder.js';
import type { LabelResolver } from './label.resolver.js';
import type { ScreenRoute } from './route-tree.builder.js';
import { refKey, SymbolTable, type ClassRef } from './symbol-table.js';
import type { UrlEvaluator } from './url-evaluator.js';

export interface BuilderDeps {
  table: SymbolTable;
  di: DiResolver;
  calls: CallResolver;
  urls: UrlEvaluator;
  labels: LabelResolver;
  templates: ReadonlyMap<string, TemplateFacts>;
  /** Selector (thẻ) → component. */
  selectors: ReadonlyMap<string, readonly ClassRef[]>;
  groupOf: (file: string) => string;
}

export interface AppInfo {
  name: string;
  root: string;
  declaredIn: string;
}

interface TemplateView {
  file: string;
  nodes: readonly TplElement[];
  heading?: { kind: 'text' | 'key' | 'pipe'; value: string };
  ids: Map<TplElement, string>;
}

interface AppCtx {
  app: string;
  screenByPath: Map<string, string>;
  dialogRefs: Map<string, ClassRef>;
  dialogsBuilt: Set<string>;
  methodDialogs: Map<string, Set<string>>;
  methodCallees: Map<string, string[]>;
}

interface Acc {
  /** Method được sự kiện gọi trực tiếp trong các template đã duyệt. */
  methods: string[];
}

interface Walk {
  ref: ClassRef;
  tpl: TemplateView;
  container: string;
  owner: string;
  ctx: AppCtx;
  stack: readonly string[];
  acc: Acc;
  insideForm: boolean;
  formSubmit?: TplEvent;
  tabCounter: { n: number };
}

const MAX_COMPONENT_DEPTH = 8;

/** Hàm vòng đời Angular chạy khi component hiện ra, theo thứ tự chạy. */
const LIFECYCLE = ['constructor', 'ngOnInit', 'ngAfterViewInit'] as const;

/**
 * Dựng phần Frontend của IR cho MỘT ứng dụng: `UI_APP → UI_SCREEN → UI_TAB / UI_FORM / UI_DIALOG → UI_ELEMENT`, các
 * `FE_METHOD` mà sự kiện gọi tới (facade, repository…), và `HTTP_CALL` do chúng phát ra. Thành phần con (thẻ trùng
 * `selector` của một component trong dự án) được gộp phẳng vào màn hình chứa nó.
 */
export class GraphBuilder {
  readonly nodes = new Map<string, IrNode>();
  readonly edges: IrEdge[] = [];
  readonly diagnostics: Diagnostic[] = [];
  private readonly edgeKeys = new Set<string>();
  private readonly templateCache = new Map<string, TemplateView | null>();
  private readonly containsOrder = new Map<string, number>();

  constructor(private readonly deps: BuilderDeps) {}

  buildApp(app: AppInfo, screens: readonly ScreenRoute[], chrome: ClassRef | undefined): void {
    const appKey = NodeKeys.ngApp(app.name);
    this.addNode({
      key: appKey,
      kind: 'UI_APP',
      name: app.name,
      ...(app.declaredIn ? { location: { filePath: app.declaredIn } } : {}),
      attrs: { name: app.name, root: app.root }
    });
    const ctx: AppCtx = {
      app: app.name,
      screenByPath: new Map(),
      dialogRefs: new Map(),
      dialogsBuilt: new Set(),
      methodDialogs: new Map(),
      methodCallees: new Map()
    };
    const built: { key: string; ref: ClassRef }[] = [];
    for (const screen of [...screens].sort((a, b) => a.fullPath.localeCompare(b.fullPath) || refKey(a.component).localeCompare(refKey(b.component)))) {
      let key = NodeKeys.ngScreen(app.name, screen.fullPath);
      if (this.nodes.has(key)) {
        if (this.nodes.get(key)?.attrs && (this.nodes.get(key)!.attrs as Record<string, unknown>)['component'] === screen.component.cls.name) {
          continue;
        }
        key = `${key}#${screen.component.cls.name}`;
        if (this.nodes.has(key)) {
          continue;
        }
      }
      const tpl = this.templateOf(screen.component);
      const heading = tpl?.heading ? this.deps.labels.resolve(tpl.heading, tpl.file)?.text : undefined;
      const displayName = screen.title ?? heading;
      this.addNode({
        key,
        kind: 'UI_SCREEN',
        name: screen.component.cls.name,
        ...(displayName ? { displayName } : {}),
        location: classLocation(screen.component),
        attrs: {
          routePath: screen.fullPath,
          component: screen.component.cls.name,
          ...(screen.title ? { title: screen.title } : {})
        }
      });
      this.edge(appKey, key, 'CONTAINS', 'CERTAIN', undefined, undefined, { filePath: screen.routeFile, startLine: screen.routeLine });
      ctx.screenByPath.set(normalizeRoute(screen.fullPath), key);
      built.push({ key, ref: screen.component });
    }
    for (const { key, ref } of built) {
      this.buildContent(ref, key, ctx);
    }
    if (chrome) {
      this.buildContent(chrome, appKey, ctx);
    }
  }

  // -------------------------------------------------------------------------------------------------------------------
  // Nội dung của một component
  // -------------------------------------------------------------------------------------------------------------------

  private buildContent(ref: ClassRef, owner: string, ctx: AppCtx): void {
    const acc: Acc = { methods: [] };
    const tpl = this.templateOf(ref);
    this.lifecycle(ref, owner, ctx, acc);
    if (tpl) {
      this.visitList(tpl.nodes, {
        ref,
        tpl,
        container: owner,
        owner,
        ctx,
        stack: [refKey(ref)],
        acc,
        insideForm: false,
        tabCounter: { n: 0 }
      });
    }
    const dialogs = new Set<string>();
    for (const method of acc.methods) {
      this.collectDialogs(method, ctx, dialogs, new Set());
    }
    for (const dialogKey of [...dialogs].sort()) {
      const dialogRef = ctx.dialogRefs.get(dialogKey);
      if (!dialogRef || dialogKey === owner) {
        continue;
      }
      this.edge(owner, dialogKey, 'CONTAINS', 'CERTAIN', 'Hộp thoại được mở từ một hàm xử lý của thành phần này', undefined, classLocation(dialogRef));
      if (!ctx.dialogsBuilt.has(dialogKey)) {
        ctx.dialogsBuilt.add(dialogKey);
        this.buildContent(dialogRef, dialogKey, ctx);
      }
    }
  }

  /**
   * Hàm chạy khi component được tạo ({@code constructor}, {@code ngOnInit}, {@code ngAfterViewInit}, kể cả khởi tạo field)
   * — thường là nơi tải dữ liệu khi mở màn hình. Nối từ nút chứa (màn hình, hộp thoại hoặc ứng dụng) tới hàm đó bằng
   * {@code TRIGGERS}; không có phần tử nào bấm nên đây là cách duy nhất biết màn hình "gọi API nào khi mở".
   */
  private lifecycle(ref: ClassRef, owner: string, ctx: AppCtx, acc: Acc): void {
    LIFECYCLE.forEach((name, index) => {
      const method =
        name === 'constructor'
          ? ref.cls.methods.find((m) => m.kind === 'constructor')
          : this.deps.calls.findMethodWithOwner(ref, name, 0)?.method;
      if (!method || !this.hasEffect(ref, method)) {
        return;
      }
      const methodKey = this.ensureMethod(ref, method, ctx);
      acc.methods.push(methodKey);
      this.edge(owner, methodKey, 'TRIGGERS', 'CERTAIN', `Chạy khi ${ref.cls.name} được tạo (${name})`, index, {
        filePath: ref.file,
        startLine: method.startLine
      });
    });
  }

  /** Hàm có làm gì đáng kể không: gọi HTTP, mở hộp thoại / chuyển trang, hoặc gọi một hàm của dự án (không tính thư viện). */
  private hasEffect(ref: ClassRef, method: MethodFact): boolean {
    return (
      method.httpCalls.length > 0 ||
      method.uiActions.length > 0 ||
      method.calls.some(
        (c) => this.deps.calls.resolve(ref, c.root, c.path, c.method, c.argCount, { rootType: c.rootType, via: c.via }) !== undefined
      )
    );
  }

  private collectDialogs(method: string, ctx: AppCtx, out: Set<string>, seen: Set<string>): void {
    if (seen.has(method)) {
      return;
    }
    seen.add(method);
    ctx.methodDialogs.get(method)?.forEach((d) => out.add(d));
    for (const callee of ctx.methodCallees.get(method) ?? []) {
      this.collectDialogs(callee, ctx, out, seen);
    }
  }

  private visitList(list: readonly TplElement[], w: Walk): void {
    for (const el of list) {
      this.visitElement(el, w);
    }
  }

  private visitElement(el: TplElement, w: Walk): void {
    const className = w.ref.cls.name;
    const id = w.tpl.ids.get(el) ?? el.tag;
    // Hộp thoại / drawer đặt thẳng trong template (`<author-drawer [open]="show">`, `<sw-dialog>nội dung</sw-dialog>`): là MỘT hộp
    // thoại chứa các thành phần bên trong, không gộp phẳng vào màn hình
    const hosted = this.pickChild(el.tag, w);
    if (hosted && isOverlay(hosted)) {
      this.visitHostedDialog(el, hosted, w);
      return;
    }
    let inner = w.container;
    let formSubmit = w.formSubmit;
    let insideForm = w.insideForm;
    const label = this.deps.labels.resolve(el.label, w.tpl.file);

    if (isFormTag(el)) {
      const key = NodeKeys.ngForm(w.ctx.app, className, id);
      this.addNode({
        key,
        kind: 'UI_FORM',
        name: id,
        ...(label?.text ? { displayName: label.text } : {}),
        location: { filePath: w.tpl.file, startLine: el.line, ...(el.endLine ? { endLine: el.endLine } : {}) },
        attrs: { component: className, fields: collectFields(el) }
      });
      this.edge(w.container, key, 'CONTAINS', 'CERTAIN', undefined, undefined, { filePath: w.tpl.file, startLine: el.line });
      inner = key;
      insideForm = true;
      formSubmit = el.events.find((e) => e.name === 'ngSubmit') ?? formSubmit;
    } else if (isTabTag(el.tag)) {
      const tabLabel = label?.text ?? el.attrs['label'] ?? el.attrs['header'] ?? el.attrs['title'];
      const tabId = tabLabel ? tabLabel.replace(/[\s#/]+/g, '_') : `tab~${++w.tabCounter.n}`;
      const key = NodeKeys.ngTab(w.ctx.app, className, tabId);
      this.addNode({
        key,
        kind: 'UI_TAB',
        name: tabLabel ?? tabId,
        ...(tabLabel ? { displayName: tabLabel } : {}),
        location: { filePath: w.tpl.file, startLine: el.line, ...(el.endLine ? { endLine: el.endLine } : {}) },
        attrs: { component: className, label: tabLabel ?? '' }
      });
      this.edge(w.container, key, 'CONTAINS', 'CERTAIN', undefined, undefined, { filePath: w.tpl.file, startLine: el.line });
      inner = key;
    }

    const rawHandlers = el.events.length > 0 ? el.events : formSubmit && isSubmit(el) ? [formSubmit] : [];
    const hasPageChange = rawHandlers.some((e) => e.name === 'pageChange');
    const handlers = hasPageChange
      ? rawHandlers.filter((e) => e.name !== 'pageSizeChange')
      : rawHandlers;
    if (isInteractive(el, w.insideForm) || handlers.length > 0) {
      if (!isFormTag(el) && !isTabTag(el.tag)) {
        const key = NodeKeys.ngElement(w.ctx.app, className, id);
        const handlerNames = unique(handlers.flatMap((e) => e.calls.map((c) => c.method)));
        this.addNode({
          key,
          kind: 'UI_ELEMENT',
          name: handlerNames[0] ?? el.tag,
          ...(label?.text ? { displayName: label.text } : {}),
          location: { filePath: w.tpl.file, startLine: el.line, ...(el.endLine ? { endLine: el.endLine } : {}) },
          attrs: {
            elementType: elementTypeOf(el),
            tag: el.tag,
            component: className,
            ...(handlers[0] ? { event: handlers[0].name } : {}),
            ...(handlers.length > 0 ? { events: unique(handlers.map((e) => e.name)) } : {}),
            ...(handlerNames.length > 0 ? { handlers: handlerNames } : {}),
            ...(label?.key ? { i18nKey: label.key } : {}),
            ...(controlOf(el) ? { control: controlOf(el) } : {}),
            ...(el.attrs['routerLink'] ? { routerLink: el.attrs['routerLink'] } : {})
          }
        });
        this.edge(w.container, key, 'CONTAINS', 'CERTAIN', undefined, undefined, { filePath: w.tpl.file, startLine: el.line });
        this.wire(key, handlers, w, el.events.length === 0);
        this.linkRoute(key, el, w);
      }
    }

    this.visitList(el.children, { ...w, container: inner, insideForm, ...(formSubmit ? { formSubmit } : {}) });

    const child = this.pickChild(el.tag, w);
    if (child) {
      this.lifecycle(child, w.owner, w.ctx, w.acc);
      const childTpl = this.templateOf(child);
      if (childTpl) {
        this.visitList(childTpl.nodes, {
          ...w,
          ref: child,
          tpl: childTpl,
          container: inner,
          stack: [...w.stack, refKey(child)],
          insideForm: false,
          tabCounter: { n: 0 }
        });
      }
    }
  }

  /** Hộp thoại đặt trực tiếp trong template: nút `UI_DIALOG` dưới nút chứa hiện tại; nội dung chiếu vào (`el.children`) và template của chính nó nằm bên trong. */
  private visitHostedDialog(el: TplElement, dialogRef: ClassRef, w: Walk): void {
    const dialogKey = NodeKeys.ngDialog(w.ctx.app, dialogRef.cls.name);
    w.ctx.dialogRefs.set(dialogKey, dialogRef);
    if (!this.nodes.has(dialogKey)) {
      const tpl = this.templateOf(dialogRef);
      const heading = tpl?.heading ? this.deps.labels.resolve(tpl.heading, tpl.file)?.text : undefined;
      this.addNode({
        key: dialogKey,
        kind: 'UI_DIALOG',
        name: dialogRef.cls.name,
        ...(heading ? { displayName: heading } : {}),
        location: { filePath: w.tpl.file, startLine: el.line, ...(el.endLine ? { endLine: el.endLine } : {}) },
        attrs: { component: dialogRef.cls.name, hosted: true, ...(heading ? { label: heading } : {}) }
      });
    }
    this.edge(w.container, dialogKey, 'CONTAINS', 'CERTAIN', 'Hộp thoại đặt trực tiếp trong template', undefined, {
      filePath: w.tpl.file,
      startLine: el.line
    });
    this.visitList(el.children, { ...w, container: dialogKey, owner: dialogKey });
    if (w.ctx.dialogsBuilt.has(dialogKey) || dialogKey === w.owner) {
      return;
    }
    w.ctx.dialogsBuilt.add(dialogKey);
    this.buildContent(dialogRef, dialogKey, w.ctx);
  }

  private pickChild(tag: string, w: Walk): ClassRef | undefined {
    if (!tag.includes('-') || w.stack.length >= MAX_COMPONENT_DEPTH) {
      return undefined;
    }
    const candidates = (this.deps.selectors.get(tag) ?? []).filter((c) => !w.stack.includes(refKey(c)));
    if (candidates.length === 0) {
      return undefined;
    }
    const group = this.deps.groupOf(w.ref.file);
    return [...candidates].sort(
      (a, b) =>
        Number(this.deps.groupOf(b.file) === group) - Number(this.deps.groupOf(a.file) === group) ||
        SymbolTable.proximity(b.file, w.ref.file) - SymbolTable.proximity(a.file, w.ref.file) ||
        refKey(a).localeCompare(refKey(b))
    )[0];
  }

  // -------------------------------------------------------------------------------------------------------------------
  // Sự kiện → hàm xử lý
  // -------------------------------------------------------------------------------------------------------------------

  private wire(elementKey: string, events: readonly TplEvent[], w: Walk, viaFormSubmit: boolean): void {
    let order = 0;
    const wiredTargets = new Set<string>();
    for (const event of events) {
      for (const call of event.calls) {
        const target = this.deps.calls.resolve(w.ref, 'this', call.path, call.method, -1);
        if (!target) {
          continue;
        }
        const location: SourceLocation = { filePath: w.tpl.file, startLine: event.line };
        const baseReason = viaFormSubmit ? 'Nút gửi của form: sự kiện ngSubmit chạy khi bấm nút này' : undefined;
        if (target.kind === 'method') {
          const methodKey = this.ensureMethod(target.ref, target.method, w.ctx);
          if (wiredTargets.has(methodKey)) {
            continue;
          }
          wiredTargets.add(methodKey);
          w.acc.methods.push(methodKey);
          this.edge(elementKey, methodKey, 'TRIGGERS', target.confidence, joinReasons(baseReason, target.reason), order++, location);
        } else {
          const candidates = this.candidatesOf(target, w.ctx);
          const candKey = candidates.map((c) => c.nodeKey).sort().join(',');
          if (candKey && wiredTargets.has(candKey)) {
            continue;
          }
          if (candKey) {
            wiredTargets.add(candKey);
          }
          this.edge(elementKey, undefined, 'TRIGGERS', 'UNRESOLVED', joinReasons(baseReason, target.reason), order++, location, candidates);
        }
      }
    }
  }

  /** `routerLink="/orders"` → NAVIGATES tới màn hình khớp đường dẫn. */
  private linkRoute(elementKey: string, el: TplElement, w: Walk): void {
    const link = el.attrs['routerLink'];
    if (!link) {
      return;
    }
    const screen = w.ctx.screenByPath.get(normalizeRoute(link));
    if (screen) {
      this.edge(elementKey, screen, 'NAVIGATES', 'CERTAIN', 'routerLink khớp đường dẫn của màn hình', undefined, {
        filePath: w.tpl.file,
        startLine: el.line
      });
    }
  }

  // -------------------------------------------------------------------------------------------------------------------
  // FE_METHOD, HTTP_CALL
  // -------------------------------------------------------------------------------------------------------------------

  private ensureMethod(ref: ClassRef, method: MethodFact, ctx: AppCtx): string {
    const key = NodeKeys.ngMethod(ctx.app, ref.cls.name, method.name);
    if (this.nodes.has(key)) {
      return key;
    }
    const layer = layerOf(ref.cls);
    this.addNode({
      key,
      kind: 'FE_METHOD',
      name: `${ref.cls.name}.${method.name}`,
      location: { filePath: ref.file, startLine: method.startLine, endLine: method.endLine },
      attrs: {
        layer: layer.layer,
        layerBasis: layer.basis,
        className: ref.cls.name,
        signature: `${method.name}(${method.params.map((p) => p.name).join(', ')})`,
        paramNames: method.params.map((p) => p.name),
        ...(method.returnTypeName ? { returnType: method.returnTypeName } : {})
      }
    });
    this.explore(ref, method, key, ctx);
    return key;
  }

  private explore(ref: ClassRef, method: MethodFact, key: string, ctx: AppCtx): void {
    ctx.methodCallees.set(key, []);
    for (const call of method.calls) {
      const target = this.deps.calls.resolve(ref, call.root, call.path, call.method, call.argCount, {
        rootType: call.rootType,
        via: call.via
      });
      if (!target) {
        continue;
      }
      const location: SourceLocation = { filePath: ref.file, startLine: call.line };
      if (target.kind === 'method') {
        const callee = this.ensureMethod(target.ref, target.method, ctx);
        ctx.methodCallees.get(key)?.push(callee);
        this.edge(key, callee, 'CALLS', target.confidence, target.reason, call.callOrder, location, undefined, call.guard);
      } else {
        this.edge(key, undefined, 'CALLS', 'UNRESOLVED', target.reason, call.callOrder, location, this.candidatesOf(target, ctx), call.guard);
      }
    }
    method.httpCalls.forEach((http, index) => {
      const httpKey = NodeKeys.ngHttpCall(ctx.app, ref.cls.name, method.name, index);
      const url = this.deps.urls.evaluate(http.url, ref.file, ref);
      const location: SourceLocation = { filePath: ref.file, startLine: http.line, ...(http.endLine ? { endLine: http.endLine } : {}) };
      this.addNode({
        key: httpKey,
        kind: 'HTTP_CALL',
        name: `${http.httpMethod} ${url.pattern || http.urlText}`,
        location,
        attrs: {
          httpMethod: http.httpMethod,
          urlExpr: http.urlText,
          ...(url.pattern ? { urlPattern: url.pattern } : {}),
          urlResolved: url.resolved,
          ...(url.issues.length > 0 ? { urlIssue: url.issues.join('; ') } : {}),
          ...(url.host ? { urlHost: url.host } : {}),
          ...(http.bodyType ? { bodyType: http.bodyType } : {}),
          origin: 'FE'
        }
      });
      this.edge(key, httpKey, 'SENDS', 'CERTAIN', undefined, http.callOrder, location, undefined, http.guard);
      if (!url.resolved) {
        this.diagnostics.push({
          severity: 'INFO',
          filePath: ref.file,
          line: http.line,
          message: `Chưa tính được đầy đủ URL của lời gọi HTTP: ${url.issues.join('; ') || http.urlText}`
        });
      }
    });
    for (const action of method.uiActions) {
      this.uiAction(ref, key, action, ctx);
    }
  }

  private uiAction(ref: ClassRef, methodKey: string, action: UiActionFact, ctx: AppCtx): void {
    const location: SourceLocation = { filePath: ref.file, startLine: action.line };
    if (action.action === 'OPEN_DIALOG' && action.target) {
      const symbol = this.deps.table.lookup(ref.file, action.target);
      if (symbol?.kind !== 'class' || symbol.cls.decorator !== 'Component') {
        return; // không phải component của dự án (vd window.open)
      }
      const dialogRef: ClassRef = { file: symbol.file, cls: symbol.cls };
      const dialogKey = NodeKeys.ngDialog(ctx.app, symbol.cls.name);
      ctx.dialogRefs.set(dialogKey, dialogRef);
      if (!this.nodes.has(dialogKey)) {
        const tpl = this.templateOf(dialogRef);
        const heading = tpl?.heading ? this.deps.labels.resolve(tpl.heading, tpl.file)?.text : undefined;
        this.addNode({
          key: dialogKey,
          kind: 'UI_DIALOG',
          name: symbol.cls.name,
          ...(heading ? { displayName: heading } : {}),
          location: classLocation(dialogRef),
          attrs: { component: symbol.cls.name, ...(heading ? { label: heading } : {}) }
        });
      }
      this.edge(methodKey, dialogKey, 'OPENS', 'CERTAIN', undefined, action.callOrder, location);
      const set = ctx.methodDialogs.get(methodKey) ?? new Set<string>();
      set.add(dialogKey);
      ctx.methodDialogs.set(methodKey, set);
    } else if (action.action === 'NAVIGATE' && action.target) {
      const screen = ctx.screenByPath.get(normalizeRoute(action.target));
      if (screen) {
        this.edge(methodKey, screen, 'NAVIGATES', 'CERTAIN', 'Đường dẫn chuyển trang khớp route của màn hình', action.callOrder, location);
      }
    }
  }

  private candidatesOf(target: Extract<CallTarget, { kind: 'unresolved' }>, ctx: AppCtx): Candidate[] {
    return target.candidates.map((c) => ({
      nodeKey: this.ensureMethod(c.ref, c.method, ctx),
      score: 1,
      reason: `Lớp hiện thực ${c.ref.cls.name}`
    }));
  }

  // -------------------------------------------------------------------------------------------------------------------
  // Template
  // -------------------------------------------------------------------------------------------------------------------

  private templateOf(ref: ClassRef): TemplateView | undefined {
    const key = refKey(ref);
    const cached = this.templateCache.get(key);
    if (cached !== undefined) {
      return cached ?? undefined;
    }
    let view: TemplateView | null = null;
    const inline = ref.cls.inlineTemplate;
    if (inline) {
      view = { file: ref.file, nodes: inline.nodes, ...(inline.heading ? { heading: inline.heading } : {}), ids: assignElementIds(inline.nodes) };
    } else if (ref.cls.templateUrl) {
      const path = joinPath(dirname(ref.file), ref.cls.templateUrl);
      const facts = this.deps.templates.get(path);
      if (facts) {
        view = { file: path, nodes: facts.nodes, ...(facts.heading ? { heading: facts.heading } : {}), ids: assignElementIds(facts.nodes) };
      } else {
        this.diagnostics.push({
          severity: 'WARNING',
          filePath: ref.file,
          line: ref.cls.line,
          message: `Không tìm thấy template ${ref.cls.templateUrl} của ${ref.cls.name}`
        });
      }
    }
    this.templateCache.set(key, view);
    return view ?? undefined;
  }

  // -------------------------------------------------------------------------------------------------------------------

  private addNode(node: IrNode): void {
    if (!this.nodes.has(node.key)) {
      this.nodes.set(node.key, node);
    }
  }

  private edge(
    from: string,
    to: string | undefined,
    kind: EdgeKind,
    confidence: Confidence,
    reason: string | undefined,
    callOrder: number | undefined,
    location: SourceLocation | undefined,
    candidates?: Candidate[],
    guard?: Guard
  ): void {
    const isContains = kind === 'CONTAINS';
    const dedupe = `${from}|${kind}|${to ?? ''}|${isContains ? '' : (callOrder ?? '')}|${(candidates ?? []).map((c) => c.nodeKey).join(',')}`;
    if (this.edgeKeys.has(dedupe)) {
      return;
    }
    this.edgeKeys.add(dedupe);
    if (isContains && callOrder === undefined) {
      // thứ tự các con trong nút chứa theo đúng thứ tự duyệt template (để cây hiển thị đúng thứ tự trên giao diện)
      callOrder = this.containsOrder.get(from) ?? 0;
      this.containsOrder.set(from, callOrder + 1);
    }
    if (!location && !reason) {
      reason = 'Suy ra từ cấu trúc mã nguồn';
    }
    this.edges.push({
      fromKey: from,
      ...(to ? { toKey: to } : {}),
      kind,
      confidence,
      ...(reason ? { reason } : {}),
      ...(callOrder !== undefined ? { callOrder } : {}),
      ...(guard ? { guard } : {}),
      ...(candidates && candidates.length > 0 ? { candidates } : {}),
      ...(location ? { location } : {})
    });
  }
}

// ---------------------------------------------------------------------------------------------------------------------

function classLocation(ref: ClassRef): SourceLocation {
  return { filePath: ref.file, startLine: ref.cls.line, endLine: ref.cls.endLine };
}

function unique<T>(items: readonly T[]): T[] {
  return [...new Set(items)];
}

/** Tên control của ô nhập (`formControlName` hoặc `name`) — dùng làm tên dự phòng khi không có nhãn. */
function controlOf(el: TplElement): string | undefined {
  return el.attrs['formControlName'] ?? (el.bindings.includes('ngModel') ? el.attrs['name'] : undefined);
}

/** Component là hộp thoại / drawer / modal theo tên class hoặc selector. */
function isOverlay(ref: ClassRef): boolean {
  const re = /dialog|modal|drawer|popup|overlay|offcanvas|bottom-?sheet/i;
  return re.test(ref.cls.name) || re.test(ref.cls.selector ?? '');
}

/** Nút gửi form: `<button>` (mặc định là submit) hoặc component bọc nút có `type="submit"`. */
function isSubmit(el: TplElement): boolean {
  const type = el.attrs['type'];
  return el.tag === 'button' ? type === undefined || type === 'submit' : type === 'submit' && /(^|-)(button|btn)$/.test(el.tag);
}

function joinReasons(a: string | undefined, b: string | undefined): string | undefined {
  return [a, b].filter(Boolean).join('. ') || undefined;
}

/** Trường của form: các control bên trong (có `formControlName` / `name`). */
function collectFields(form: TplElement): { name: string; controlType: string }[] {
  const fields = new Map<string, string>();
  const visit = (el: TplElement): void => {
    const name = el.attrs['formControlName'] ?? (el.bindings.includes('ngModel') ? el.attrs['name'] : undefined);
    if (name && !fields.has(name)) {
      fields.set(name, elementTypeOf(el));
    }
    el.children.forEach(visit);
  };
  form.children.forEach(visit);
  return [...fields].map(([name, controlType]) => ({ name, controlType }));
}

/** `/orders/{id}` và `/orders/:id` về cùng một dạng; bỏ query, dấu '/' cuối. */
export function normalizeRoute(path: string): string {
  const base = path.replace(/[?#].*$/, '').replace(/\{[^}]*\}|:[^/]+/g, ':');
  const segments = base.split('/').filter((s) => s.length > 0);
  return '/' + segments.join('/');
}

function layerOf(cls: ClassFact): { layer: string; basis: string } {
  if (cls.decorator === 'Component' || cls.decorator === 'Directive') {
    return { layer: 'COMPONENT', basis: 'DECORATOR' };
  }
  const name = cls.name;
  if (/Facade$/.test(name)) {
    return { layer: 'FACADE', basis: 'NAME' };
  }
  if (/(UseCase|Usecase|Interactor)$/.test(name)) {
    return { layer: 'USECASE', basis: 'NAME' };
  }
  if (/(Repository|Api|Client|Gateway|Dao|Adapter)$/.test(name)) {
    return { layer: 'REPOSITORY', basis: 'NAME' };
  }
  if (/(Service|Store|State|Manager|Handler)$/.test(name) || cls.decorator === 'Injectable') {
    return { layer: 'SERVICE', basis: /(Service|Store|State|Manager|Handler)$/.test(name) ? 'NAME' : 'DECORATOR' };
  }
  return { layer: 'OTHER', basis: 'NAME' };
}

export type { NodeKind };
