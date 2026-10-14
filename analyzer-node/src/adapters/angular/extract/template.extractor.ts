import {
  ASTWithSource,
  BindingPipe,
  Binary,
  Call,
  Chain,
  Conditional,
  ImplicitReceiver,
  Interpolation,
  KeyedRead,
  LiteralPrimitive,
  NonNullAssert,
  PrefixNot,
  PropertyRead,
  SafeCall,
  SafePropertyRead,
  ThisReceiver,
  TmplAstBoundText,
  TmplAstElement,
  TmplAstText,
  parseTemplate,
  type AST,
  type TmplAstNode
} from '@angular/compiler';
import type { LabelFact, ParseError, TplElement, TplEvent } from '../facts.js';

/** Hàm dịch được nhận khi gặp `{{ t('khóa') }}`. */
const I18N_FUNCTIONS = new Set(['t', 'tr', 'translate', 'instant', 'i18n', 'text', 'label', 'getText', 'getLabel', 'trans']);
const I18N_PIPES = new Set(['translate', 'i18n', 'transloco', 't']);

/** Thuộc tính tĩnh giữ lại. */
const KEPT_ATTRS = new Set([
  'id',
  'type',
  'name',
  'label',
  'title',
  'placeholder',
  'aria-label',
  'header',
  'role',
  'routerLink',
  'formControlName',
  'formGroupName',
  'tooltip',
  'text'
]);

/** Binding giữ lại tên (để nhận ra form / ô nhập / liên kết). */
const KEPT_BINDINGS = new Set([
  'formGroup',
  'formControl',
  'formControlName',
  'formGroupName',
  'formArrayName',
  'ngModel',
  'ngForm',
  'routerLink',
  'label',
  'title',
  'placeholder',
  'header',
  'aria-label'
]);

/** Thẻ làm ô nhập / điều khiển. */
const CONTROL_TAGS = /^(input|select|textarea|mat-select|mat-checkbox|mat-radio-group|mat-radio-button|mat-slide-toggle|mat-datepicker|mat-form-field|nz-select|nz-input|nz-date-picker|p-dropdown|p-calendar|p-inputtext)$/;

/** Thẻ biểu tượng / không đóng góp chữ cho nhãn. */
const NO_LABEL_TAGS = new Set(['mat-icon', 'svg', 'i', 'style', 'script', 'img']);
const INTERACTIVE_TAGS = new Set(['button', 'a', 'input', 'select', 'textarea']);

export interface ExtractedTemplate {
  nodes: TplElement[];
  errors: ParseError[];
  /** Tiêu đề của template (h1–h4, `mat-dialog-title`) — đặt tên cho màn hình / hộp thoại. */
  heading?: LabelFact;
}

const HEADING = /^(h1|h2|h3|h4|mat-dialog-title)$/;

/**
 * Đọc template Angular (file `.html` hoặc `template:` trong decorator): giữ cây phần tử có ý nghĩa — phần tử có sự kiện,
 * form, ô nhập, liên kết, thẻ có thể là component con (chứa dấu `-`) — còn lại gộp lên cấp trên.
 *
 * @param lineOffset số dòng cộng thêm vào mọi số dòng (template viết trong file `.ts`)
 */
export function extractTemplate(text: string, url: string, lineOffset = 0): ExtractedTemplate {
  const errors: ParseError[] = [];
  let nodes: TmplAstNode[];
  try {
    const parsed = parseTemplate(text, url, { preserveWhitespaces: false });
    nodes = parsed.nodes;
    for (const err of parsed.errors ?? []) {
      errors.push({ message: err.msg, line: err.span.start.line + 1 + lineOffset });
    }
  } catch (e) {
    return { nodes: [], errors: [{ message: `Không đọc được template: ${(e as Error).message}` }] };
  }
  const heading = findHeading(nodes);
  return { nodes: convert(nodes, lineOffset, false, undefined), errors, ...(heading ? { heading } : {}) };
}

function findHeading(nodes: readonly TmplAstNode[]): LabelFact | undefined {
  for (const node of nodes) {
    if (node instanceof TmplAstElement) {
      const isTitle = HEADING.test(node.name) || node.attributes.some((a) => a.name === 'mat-dialog-title' || a.name === 'matDialogTitle');
      if (isTitle) {
        const label = contentLabel(node);
        if (label) {
          return label;
        }
      }
    }
    const nested = findHeading(childrenOf(node));
    if (nested) {
      return nested;
    }
  }
  return undefined;
}

// ---------------------------------------------------------------------------------------------------------------------

/** Mọi nút con ở các vị trí Angular đặt (children, nhánh @if, @for, @switch, @defer…) theo thứ tự. */
function childrenOf(node: unknown): TmplAstNode[] {
  const out: TmplAstNode[] = [];
  const n = node as Record<string, unknown>;
  const push = (v: unknown): void => {
    if (Array.isArray(v)) {
      v.forEach((x) => out.push(x as TmplAstNode));
    }
  };
  push(n['children']);
  push(n['branches']);
  push(n['cases']);
  push(n['groups']);
  for (const key of ['empty', 'placeholder', 'loading', 'error']) {
    const v = n[key];
    if (v && typeof v === 'object') {
      out.push(v as TmplAstNode);
    }
  }
  return out;
}

function convert(nodes: readonly TmplAstNode[], lineOffset: number, insideForm: boolean, outerLabel: LabelFact | undefined): TplElement[] {
  const out: TplElement[] = [];
  // `<label>Mã</label><shared-input formControlName="code"/>`: label đứng ngay trước ô nhập là nhãn của ô đó
  let siblingLabel: LabelFact | undefined;
  for (const node of nodes) {
    if (node instanceof TmplAstElement) {
      const carried = siblingLabel;
      siblingLabel = node.name === 'label' && !hasControl(node) ? contentLabel(node) : undefined;
      const labelForNode = carried ?? outerLabel;
      const inForm = insideForm || node.name === 'form' || node.inputs.some((i) => i.name === 'formGroup');
      // `<label>Mã <input formControlName="code"/></label>`: chữ của label làm nhãn cho ô nhập bên trong
      const labelOfChildren = node.name === 'label' ? (contentLabel(node) ?? labelForNode) : outerLabel;
      const children = convert(childrenOf(node), lineOffset, inForm, labelOfChildren);
      const element = build(node, children, lineOffset, insideForm, labelForNode);
      if (element) {
        out.push(element);
      } else {
        out.push(...children);
      }
    } else if (!(node instanceof TmplAstText) && !(node instanceof TmplAstBoundText)) {
      // @if / @for / @switch / @defer / ng-template / *ngIf: trong suốt
      out.push(...convert(childrenOf(node), lineOffset, insideForm, outerLabel));
    }
  }
  return out;
}

function build(
  el: TmplAstElement,
  children: TplElement[],
  lineOffset: number,
  insideForm: boolean,
  outerLabel: LabelFact | undefined
): TplElement | null {
  const attrs: Record<string, string> = {};
  for (const a of el.attributes) {
    if (KEPT_ATTRS.has(a.name)) {
      attrs[a.name] = a.value;
    }
  }
  const bindings = new Set<string>();
  for (const a of el.attributes) {
    if (KEPT_BINDINGS.has(a.name)) {
      bindings.add(a.name);
    }
  }
  for (const input of el.inputs) {
    if (KEPT_BINDINGS.has(input.name)) {
      bindings.add(input.name);
    }
  }
  const events: TplEvent[] = [];
  for (const out of el.outputs) {
    const handler = analyzeHandler(out.handler);
    if (handler.calls.length > 0 || handler.writes.length > 0) {
      events.push({ name: out.name, ...handler, line: out.sourceSpan.start.line + 1 + lineOffset });
    }
  }
  const tag = el.name;
  const interesting =
    events.length > 0 ||
    tag === 'form' ||
    bindings.has('formGroup') ||
    bindings.has('routerLink') ||
    (CONTROL_TAGS.test(tag) && (bindings.has('formControlName') || bindings.has('formControl') || bindings.has('ngModel'))) ||
    isSubmitButton(tag, el, insideForm) ||
    isTabTag(tag) ||
    tag.includes('-');
  if (!interesting) {
    return null; // không có gì đáng giữ: con được gộp lên cấp trên
  }
  const isContainer = tag === 'form' || isTabTag(tag) || /-group$|-form-field$/.test(tag);
  const isControl = CONTROL_TAGS.test(tag) || bindings.has('formControlName') || bindings.has('formControl') || bindings.has('ngModel');
  const label = isContainer
    ? attributeLabel(el)
    : isControl
      ? (outerLabel ?? attributeLabel(el))
      : (contentLabel(el) ?? attributeLabel(el));
  return {
    tag,
    line: el.sourceSpan.start.line + 1 + lineOffset,
    endLine: el.sourceSpan.end.line + 1 + lineOffset,
    attrs,
    bindings: [...bindings].sort(),
    events,
    ...(label ? { label } : {}),
    children
  };
}

/**
 * Nút gửi form: `type="submit"` (kể cả component bọc như `<shared-button type="submit">`), hoặc `<button>` không khai báo `type`
 * nằm trong form (mặc định là submit).
 */
function isSubmitButton(tag: string, el: TmplAstElement, insideForm: boolean): boolean {
  const type = el.attributes.find((a) => a.name === 'type')?.value;
  if (type === 'submit') {
    return tag === 'button' || /(^|-)(button|btn)$/.test(tag);
  }
  return tag === 'button' && type === undefined && insideForm;
}

/** Thẻ có chứa ô nhập bên trong (`<label>Mã <input/></label>`). */
function hasControl(node: TmplAstElement): boolean {
  return childrenOf(node).some(
    (c) => c instanceof TmplAstElement && (CONTROL_TAGS.test(c.name) || c.attributes.some((a) => a.name === 'formControlName') || hasControl(c))
  );
}

function isTabTag(tag: string): boolean {
  return /(^|-)tab$|^(mat-tab|nz-tab|p-tabpanel|ngb-tab|tab)$/.test(tag);
}

// ---------------------------------------------------------------------------------------------------------------------
// Nhãn
// ---------------------------------------------------------------------------------------------------------------------

function contentLabel(el: TmplAstElement): LabelFact | undefined {
  const texts: string[] = [];
  let key: LabelFact | undefined;
  const visit = (node: TmplAstNode, depth: number): void => {
    if (node instanceof TmplAstText) {
      const t = node.value.replace(/\s+/g, ' ').trim();
      if (t) {
        texts.push(t);
      }
    } else if (node instanceof TmplAstBoundText) {
      const found = interpolationLabel(node.value);
      if (found) {
        key ??= found;
      }
    } else if (node instanceof TmplAstElement) {
      if (NO_LABEL_TAGS.has(node.name) || (depth > 0 && INTERACTIVE_TAGS.has(node.name))) {
        return;
      }
      childrenOf(node).forEach((c) => visit(c, depth + 1));
    } else {
      childrenOf(node).forEach((c) => visit(c, depth + 1));
    }
  };
  childrenOf(el).forEach((c) => visit(c, 0));
  if (key) {
    return key;
  }
  const text = texts.join(' ').trim();
  return text ? { kind: 'text', value: text } : undefined;
}

/** Khóa / chữ của nhãn ở trạng thái đang xử lý (đang gửi, đang tải…) — không phải tên của nút. */
const BUSY_LABEL = /submitting|loading|saving|processing|sending|pending|busy|waiting|in_progress|inprogress|dang_/i;

function interpolationLabel(value: AST): LabelFact | undefined {
  const ast = value instanceof ASTWithSource ? value.ast : value;
  if (!(ast instanceof Interpolation) || ast.expressions.length !== 1) {
    return undefined;
  }
  return expressionLabel(ast.expressions[0]);
}

function expressionLabel(expr: AST | undefined): LabelFact | undefined {
  if (!expr) {
    return undefined;
  }
  const ast = expr instanceof ASTWithSource ? expr.ast : expr;
  if (ast instanceof Call) {
    const callee = ast.receiver;
    const first = ast.args[0];
    if (
      callee instanceof PropertyRead &&
      isThisLike(callee.receiver, 2) &&
      I18N_FUNCTIONS.has(callee.name) &&
      first instanceof LiteralPrimitive &&
      typeof first.value === 'string'
    ) {
      return { kind: 'key', value: first.value };
    }
  }
  if (ast instanceof Binary && ['+', '||', '??'].includes(ast.operation)) {
    // `t('code') + ' *'`, `t('code') || 'Mã'`: lấy khóa dịch ở một vế, bỏ phần chữ còn lại
    return expressionLabel(ast.left) ?? expressionLabel(ast.right);
  }
  if (ast instanceof Conditional) {
    // `{{ saving ? t('btn_saving') : t('btn_save') }}`: nhãn bình thường của nút là nhánh KHÔNG phải trạng thái "đang xử lý"
    const options = [expressionLabel(ast.trueExp), expressionLabel(ast.falseExp)].filter((o): o is LabelFact => o !== undefined);
    return options.find((o) => !BUSY_LABEL.test(o.value)) ?? options[0];
  }
  if (ast instanceof NonNullAssert) {
    return expressionLabel(ast.expression);
  }
  if (ast instanceof BindingPipe && I18N_PIPES.has(ast.name)) {
    const inner = ast.exp instanceof ASTWithSource ? ast.exp.ast : ast.exp;
    if (inner instanceof LiteralPrimitive && typeof inner.value === 'string') {
      return { kind: 'pipe', value: inner.value };
    }
  }
  return undefined;
}

/** Receiver là `this` ngầm hoặc một đối tượng dịch vụ (`i18n.t('x')`, `translate.instant('x')`). */
function isThisLike(receiver: AST, depth: number): boolean {
  if (receiver instanceof ImplicitReceiver || receiver instanceof ThisReceiver) {
    return true;
  }
  return depth > 0 && (receiver instanceof PropertyRead || receiver instanceof SafePropertyRead) && isThisLike(receiver.receiver, depth - 1);
}

function attributeLabel(el: TmplAstElement): LabelFact | undefined {
  for (const name of ['label', 'aria-label', 'title', 'placeholder', 'header', 'text']) {
    const attr = el.attributes.find((a) => a.name === name);
    if (attr && attr.value.trim()) {
      return { kind: 'text', value: attr.value.trim() };
    }
    const bound = el.inputs.find((i) => i.name === name);
    if (bound) {
      const found = expressionLabel(bound.value);
      if (found) {
        return found;
      }
    }
  }
  return undefined;
}

// ---------------------------------------------------------------------------------------------------------------------
// Handler sự kiện
// ---------------------------------------------------------------------------------------------------------------------

function analyzeHandler(handler: AST): { calls: TplEvent['calls']; writes: string[] } {
  const calls: TplEvent['calls'] = [];
  const writes: string[] = [];
  let order = 0;

  const walk = (node: AST | null | undefined): void => {
    if (!node) {
      return;
    }
    if (node instanceof ASTWithSource) {
      walk(node.ast);
    } else if (node instanceof Chain) {
      node.expressions.forEach(walk);
    } else if (node instanceof Call || node instanceof SafeCall) {
      const callee = node.receiver;
      if (callee instanceof PropertyRead || callee instanceof SafePropertyRead) {
        walk(callee.receiver);
        node.args.forEach(walk);
        const path = receiverPath(callee.receiver);
        if (path) {
          calls.push({ path, method: callee.name, order: order++ });
        }
      } else {
        walk(callee);
        node.args.forEach(walk);
      }
    } else if (node instanceof Binary) {
      if (node.operation === '=' && (node.left instanceof PropertyRead || node.left instanceof SafePropertyRead)) {
        const path = receiverPath(node.left.receiver);
        if (path) {
          writes.push([...path, node.left.name].join('.'));
        }
        walk(node.right);
      } else {
        walk(node.left);
        walk(node.right);
      }
    } else if (node instanceof Conditional) {
      walk(node.condition);
      walk(node.trueExp);
      walk(node.falseExp);
    } else if (node instanceof PrefixNot || node instanceof NonNullAssert) {
      walk(node.expression);
    } else if (node instanceof BindingPipe) {
      walk(node.exp);
      node.args.forEach(walk);
    } else if (node instanceof KeyedRead) {
      walk(node.receiver);
      walk(node.key);
    }
  };
  walk(handler);
  return { calls, writes };
}

/** Receiver của lời gọi trong template: `[]` = chính component; `['facade']` = `facade.x()`; null = không đọc được. */
function receiverPath(receiver: AST): string[] | null {
  if (receiver instanceof ImplicitReceiver || receiver instanceof ThisReceiver) {
    return [];
  }
  if (receiver instanceof PropertyRead || receiver instanceof SafePropertyRead) {
    const base = receiverPath(receiver.receiver);
    return base ? [...base, receiver.name] : null;
  }
  return null;
}
