import type { TplElement } from '../facts.js';

export type ElementType =
  | 'button'
  | 'link'
  | 'input'
  | 'select'
  | 'textarea'
  | 'checkbox'
  | 'radio'
  | 'toggle'
  | 'date'
  | 'table'
  | 'pagination'
  | 'form'
  | 'tab'
  | 'component'
  | 'element';

const SELECT_TAGS = /^(select|mat-select|nz-select|p-dropdown|ng-select)$/;
const DATE_TAGS = /^(mat-datepicker|nz-date-picker|p-calendar|mat-date-range-input)$/;
const CONTROL_TYPES: readonly ElementType[] = ['input', 'select', 'textarea', 'checkbox', 'radio', 'toggle', 'date'];

/** Loại phần tử hiển thị trên cây (overview §2.2.1: button, input, select, table, pagination…). */
export function elementTypeOf(el: TplElement): ElementType {
  const tag = el.tag;
  const type = el.attrs['type'];
  if (tag === 'form') {
    return 'form';
  }
  if (tag === 'button' || tag === 'mat-button' || el.attrs['role'] === 'button') {
    return 'button';
  }
  if (tag === 'a') {
    return 'link';
  }
  if (tag === 'input') {
    if (type === 'checkbox') {
      return 'checkbox';
    }
    if (type === 'radio') {
      return 'radio';
    }
    return type === 'submit' || type === 'button' ? 'button' : 'input';
  }
  if (SELECT_TAGS.test(tag)) {
    return 'select';
  }
  if (tag === 'textarea') {
    return 'textarea';
  }
  if (/checkbox/.test(tag)) {
    return 'checkbox';
  }
  if (/radio/.test(tag)) {
    return 'radio';
  }
  if (/toggle/.test(tag)) {
    return 'toggle';
  }
  if (DATE_TAGS.test(tag)) {
    return 'date';
  }
  if (tag === 'table' || /(^|-)table$/.test(tag)) {
    return 'table';
  }
  if (/paginator|pagination|pager/.test(tag)) {
    return 'pagination';
  }
  if (el.bindings.includes('routerLink')) {
    return 'link';
  }
  return tag.includes('-') ? wrapperType(tag) : 'element';
}

/** Component bọc của thư viện giao diện dự án (`shared-button`, `app-input`, `sw-select`…) theo tên thẻ. */
function wrapperType(tag: string): ElementType {
  const rules: [RegExp, ElementType][] = [
    [/(^|-)(button|btn)$/, 'button'],
    [/(^|-)(textarea)$/, 'textarea'],
    [/(^|-)(select|dropdown|combobox|autocomplete)$/, 'select'],
    [/(^|-)(date|datepicker|date-input|date-picker|date-range)(-[a-z]+)?$/, 'date'],
    [/(^|-)(input|text-field|textfield)$/, 'input'],
    [/(^|-)(checkbox)$/, 'checkbox'],
    [/(^|-)(radio)$/, 'radio'],
    [/(^|-)(switch|toggle)$/, 'toggle'],
    [/(^|-)(table|grid)$/, 'table'],
    [/(^|-)(paginator|pagination|pager)$/, 'pagination']
  ];
  return rules.find(([re]) => re.test(tag))?.[1] ?? 'component';
}

export function isFormTag(el: TplElement): boolean {
  return el.tag === 'form' || el.bindings.includes('formGroup');
}

export function isTabTag(tag: string): boolean {
  return /(^|-)tab$|^(mat-tab|nz-tab|p-tabpanel|ngb-tab|tab)$/.test(tag);
}

/** Phần tử có đáng thành một nút `UI_ELEMENT` không: có sự kiện, là ô nhập của form, là liên kết, hoặc là nút gửi form. */
export function isInteractive(el: TplElement, insideForm: boolean): boolean {
  if ((isFormTag(el) || isTabTag(el.tag)) && el.events.length === 0) {
    return false;
  }
  if (el.events.length > 0 || el.bindings.includes('routerLink')) {
    return true;
  }
  const type = elementTypeOf(el);
  if (CONTROL_TYPES.includes(type)) {
    return el.bindings.some((b) => ['formControlName', 'formControl', 'ngModel'].includes(b));
  }
  return type === 'button' && (el.attrs['type'] === 'submit' || (el.attrs['type'] === undefined && insideForm));
}

/**
 * Mã phần tử ổn định trong một component (dùng làm phần cuối của node key): thuộc tính `id` nếu có, không thì
 * `<thẻ>.<sự kiện>.<hàm xử lý>` (ô nhập: `<thẻ>.<tên control>`). Trùng thì thêm `~2`, `~3`… theo thứ tự xuất hiện trong
 * template. Không dùng thứ tự tuyệt đối nên thêm / bớt phần tử khác không làm đổi mã.
 */
export function assignElementIds(nodes: readonly TplElement[]): Map<TplElement, string> {
  const ids = new Map<TplElement, string>();
  const counts = new Map<string, number>();
  const visit = (list: readonly TplElement[]): void => {
    for (const el of list) {
      const base = baseId(el);
      const n = (counts.get(base) ?? 0) + 1;
      counts.set(base, n);
      ids.set(el, n === 1 ? base : `${base}~${n}`);
      visit(el.children);
    }
  };
  visit(nodes);
  return ids;
}

function baseId(el: TplElement): string {
  const id = el.attrs['id'];
  if (id) {
    return `id.${sanitize(id)}`;
  }
  const event = el.events[0];
  if (event) {
    const handler = event.calls[0]?.method ?? `write.${event.writes[0] ?? 'state'}`;
    return sanitize(`${el.tag}.${event.name}.${handler}`);
  }
  if ((el.tag === 'button' && el.attrs['type'] !== 'button') || el.attrs['type'] === 'submit') {
    return `${el.tag}.submit`;
  }
  const control = el.attrs['formControlName'] ?? el.attrs['name'];
  if (control) {
    return sanitize(`${el.tag}.${control}`);
  }
  const link = el.attrs['routerLink'];
  if (link) {
    return sanitize(`${el.tag}.routerLink.${link}`);
  }
  return sanitize(`${el.tag}.${el.bindings[0] ?? 'element'}`);
}

function sanitize(value: string): string {
  return value.replace(/[\s#/]+/g, '_');
}
