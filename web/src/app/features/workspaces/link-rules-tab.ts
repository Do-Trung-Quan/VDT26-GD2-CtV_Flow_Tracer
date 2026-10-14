import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { toApiError } from '../../core/api/api-error';
import type { LinkRuleDto, LinkRuleType, LinkRulesDto } from '../../core/api/api-types';
import { LinkingApi } from '../../core/api/linking.api';
import { Icon } from '../../core/ui/icon';
import { Modal } from '../../core/ui/modal';
import { SelectMenu, SelectOption } from '../../core/ui/select-menu';
import { ToastService } from '../../core/ui/toast.service';
import { RULE_TYPES, RuleItem, needsReplacement, patternError, ruleTypeLabel, withRule, withoutRule } from './link-settings-model';

/** Luật đang sửa trong hộp thoại; `index` null là thêm mới. */
interface Editing {
  index: number | null;
}

/**
 * Cài đặt › Luật liên kết (API E6–E7): luật tool tự phát hiện (chỉ xem) và luật thủ công (thêm / sửa / xóa). Lưu xong Engine
 * nối lại ngay và báo "Đã nối lại N liên kết".
 */
@Component({
  selector: 'app-link-rules-tab',
  imports: [Icon, Modal, SelectMenu],
  template: `
    <div class="row bar">
      <div class="sp1"></div>
      <button class="btn pri" type="button" [disabled]="busy() || !rules()" (click)="startEdit(null)"><app-icon name="plus" [small]="true" />Thêm luật thủ công</button>
    </div>
    @if (loading()) {
      <div class="card pad"><div class="skel"></div><div class="skel" style="width: 80%"></div></div>
    } @else if (error()) {
      <div class="errb">{{ error() }}</div>
    } @else {
      <div class="card tblw">
        <table class="tbl">
          <thead><tr><th>Nguồn</th><th>Loại</th><th>Mẫu</th><th>Thay bằng</th><th></th></tr></thead>
          <tbody>
            @for (r of detected(); track $index) {
              <tr>
                <td><span class="pill p-gray">Tự phát hiện</span></td>
                <td>{{ typeLabel(r.type) }}</td>
                <td class="mono">{{ r.pattern }}@if (r.description) {<div class="hint">{{ r.description }}</div>}</td>
                <td class="mono">{{ shown(r) }}</td>
                <td class="actions"><span class="hint">chỉ xem</span></td>
              </tr>
            }
            @for (r of manual(); track $index; let i = $index) {
              <tr>
                <td><span class="pill p-purple">Thủ công</span></td>
                <td>{{ typeLabel(r.type) }}</td>
                <td class="mono">{{ r.pattern }}@if (r.description) {<div class="hint">{{ r.description }}</div>}</td>
                <td class="mono">{{ shown(r) }}</td>
                <td class="actions">
                  <button class="ibtn" type="button" aria-label="Sửa luật" [disabled]="busy()" (click)="startEdit(i)"><app-icon name="edit" [small]="true" /></button>
                  <button class="ibtn" type="button" aria-label="Xóa luật" [disabled]="busy()" (click)="remove(i)"><app-icon name="trash" [small]="true" /></button>
                </td>
              </tr>
            }
            @if (!detected().length && !manual().length) {
              <tr><td colspan="5"><div class="empty">Chưa có luật nào. Quét dự án để tool tự phát hiện luật proxy / context-path, hoặc thêm luật thủ công.</div></td></tr>
            }
          </tbody>
        </table>
      </div>
    }

    @if (editing(); as ed) {
      <app-modal [title]="ed.index === null ? 'Thêm luật liên kết' : 'Sửa luật liên kết'" (closed)="editing.set(null)">
        <div class="fld">
          <span class="lb" id="ruleTypeLb">Loại</span>
          <app-select-menu class="block" ariaLabel="Loại luật liên kết" [options]="typeOptions" [value]="type()" (changed)="setType($event)" />
          <div class="hint">{{ typeInfo().help }}</div>
        </div>
        <div class="fld">
          <label class="lb" for="rulePattern">Mẫu {{ type() === 'CONTEXT_PATH' ? '(tiền tố của API Backend)' : type() === 'QUERY_SINK' ? '(Lớp#hàm)' : '(xuất hiện trong mã Frontend)' }} *</label>
          <input id="rulePattern" class="in mono" [class.bad]="!!fieldError()" [placeholder]="typeInfo().patternHint" [value]="pattern()"
            (input)="pattern.set(val($event)); fieldError.set(null)" />
          @if (fieldError()) {
            <div class="err-text">{{ fieldError() }}</div>
          }
        </div>
        @if (hasReplacement()) {
          <div class="fld">
            <label class="lb" for="ruleRepl">Thay bằng (đường dẫn API thật)</label>
            <input id="ruleRepl" class="in mono" [placeholder]="typeInfo().replacementHint ?? ''" [value]="replacement()" (input)="replacement.set(val($event))" />
          </div>
        }
        <div class="fld">
          <label class="lb" for="ruleDesc">Mô tả</label>
          <input id="ruleDesc" class="in" placeholder="Vì sao cần luật này (tùy chọn)" [value]="description()" (input)="description.set(val($event))" />
        </div>
        <div class="hint">Sau khi lưu, tool nối lại các liên kết Frontend ↔ Backend.</div>
        <ng-container footer>
          <button class="btn" type="button" (click)="editing.set(null)">Hủy</button>
          <button class="btn pri" type="button" [disabled]="busy()" (click)="save(ed)">Lưu &amp; nối lại</button>
        </ng-container>
      </app-modal>
    }
  `,
  styles: `
    .bar { margin-bottom: 12px; }
    .pad { padding: 16px; }
    .tblw { overflow: hidden; }
    .actions { white-space: nowrap; text-align: right; }
    td .hint { font-family: var(--font); margin-top: 2px; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LinkRulesTab {
  private readonly api = inject(LinkingApi);
  private readonly toast = inject(ToastService);

  readonly workspaceId = input.required<string>();

  protected readonly types = RULE_TYPES;
  protected readonly typeOptions: SelectOption[] = RULE_TYPES.map((t) => ({ value: t.type ?? '', label: t.label ?? '' }));
  protected readonly rules = signal<LinkRulesDto | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly detected = computed(() => this.rules()?.detected ?? []);
  protected readonly manual = computed(() => this.rules()?.manual ?? []);

  protected readonly editing = signal<Editing | null>(null);
  protected readonly type = signal<LinkRuleType>('PREFIX');
  protected readonly pattern = signal('');
  protected readonly replacement = signal('');
  protected readonly description = signal('');
  protected readonly fieldError = signal<string | null>(null);
  protected readonly typeInfo = computed(() => RULE_TYPES.find((t) => t.type === this.type()) ?? RULE_TYPES[0]);
  protected readonly hasReplacement = computed(() => needsReplacement(this.type()));

  constructor() {
    effect(() => {
      const id = this.workspaceId();
      untracked(() => void this.load(id));
    });
  }

  protected val(e: Event): string {
    return (e.target as HTMLInputElement).value;
  }

  protected typeLabel(type: LinkRuleType | undefined): string {
    return ruleTypeLabel(type);
  }

  protected shown(r: LinkRuleDto): string {
    if (r.type && !needsReplacement(r.type)) {
      return '—';
    }
    return r.replacement || '(bỏ phần khớp)';
  }

  private async load(id: string): Promise<void> {
    this.loading.set(true);
    try {
      this.rules.set(await this.api.rules(id));
      this.error.set(null);
    } catch (e) {
      this.error.set(toApiError(e).message);
    } finally {
      this.loading.set(false);
    }
  }

  protected setType(value: string): void {
    this.type.set(value as LinkRuleType);
  }

  protected startEdit(index: number | null): void {
    const r = index === null ? null : this.manual()[index];
    this.type.set(r?.type ?? 'PREFIX');
    this.pattern.set(r?.pattern ?? '');
    this.replacement.set(r?.replacement ?? '');
    this.description.set(r?.description ?? '');
    this.fieldError.set(null);
    this.editing.set({ index });
  }

  protected async save(ed: Editing): Promise<void> {
    const pattern = this.pattern().trim();
    if (!pattern) {
      this.fieldError.set('Cần nhập mẫu của luật.');
      return;
    }
    const item: RuleItem = {
      type: this.type(),
      pattern,
      ...(this.hasReplacement() && this.replacement().trim() ? { replacement: this.replacement().trim() } : {}),
      ...(this.description().trim() ? { description: this.description().trim() } : {})
    };
    const items = withRule(this.manual(), ed.index, item);
    const index = ed.index ?? items.length - 1;
    await this.submit(items, () => this.editing.set(null), (errors) => {
      this.fieldError.set(patternError(errors, index));
      return this.fieldError() !== null;
    });
  }

  protected async remove(index: number): Promise<void> {
    await this.submit(withoutRule(this.manual(), index), () => undefined);
  }

  /** Gửi danh sách luật thủ công mới (E7); Engine nối lại ngay và trả thông điệp "Đã nối lại N liên kết". */
  private async submit(manual: RuleItem[], onSaved: () => void, onFieldError?: (errors: Record<string, unknown>) => boolean): Promise<void> {
    this.busy.set(true);
    try {
      const saved = await this.api.saveRules(this.workspaceId(), { manual });
      this.rules.set(saved.rules);
      onSaved();
      this.toast.show(saved.message, 'ok');
    } catch (e) {
      const err = toApiError(e);
      if (!(err.code === 'VALIDATION_FAILED' && onFieldError?.(err.errors))) {
        this.toast.show(err.message, 'err');
      }
    } finally {
      this.busy.set(false);
    }
  }
}
