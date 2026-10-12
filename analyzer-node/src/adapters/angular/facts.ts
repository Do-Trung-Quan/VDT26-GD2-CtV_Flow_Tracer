/**
 * Dữ kiện pha 1 của một file Angular. Serialize JSON để cache theo mã băm file ({@link FACTS_VERSION} nằm trong tên
 * cache: đổi cách trích dữ kiện thì tăng số này).
 *
 * Mọi dữ kiện chỉ phản ánh MỘT file (đường dẫn import chưa được giải); việc ghép chéo file làm ở pha 2.
 */
export const FACTS_VERSION = '3';

export type AngularFacts = TsFacts | TemplateFacts | JsonFacts | FederationFacts | OtherFacts;

interface FactsBase {
  path: string;
  hash: string;
  /** Lỗi không phân tích được (cú pháp…) kèm số dòng; pha 2 đổi thành Diagnostic mức ERROR. */
  parseErrors: ParseError[];
}

export interface ParseError {
  message: string;
  line?: number;
}

// ---------------------------------------------------------------------------------------------------------------------
// File TypeScript
// ---------------------------------------------------------------------------------------------------------------------

export interface TsFacts extends FactsBase {
  kind: 'ts';
  imports: ImportFact[];
  /** `export * from '…'` / `export { A as B } from '…'` — để đi xuyên các file barrel. */
  reexports: ReexportFact[];
  classes: ClassFact[];
  /** Mảng route khai báo ở cấp file (`export const routes: Routes = [...]`). */
  routeArrays: RouteArrayFact[];
  /** `provideRouter(x)` / `RouterModule.forRoot(x)` ở cấp file (ngoài @NgModule). */
  routerRefs: RouterRefFact[];
  providers: ProviderFact[];
  constants: ConstantFact[];
  dictionaries: DictionaryFact[];
  /** `new InjectionToken<T>('…')`. */
  tokens: TokenFact[];
  /** Tên class đầu tiên trong `bootstrapApplication(X, …)`. */
  bootstraps: string[];
}

export interface ImportFact {
  local: string;
  /** Tên khai báo ở file nguồn; `default` hoặc `*` với import mặc định / namespace. */
  imported: string;
  from: string;
}

export interface ReexportFact {
  from: string;
  /** `null` = `export *`. */
  names: { local: string; exported: string }[] | null;
}

export interface ClassFact {
  name: string;
  kind: 'class' | 'interface';
  exported: boolean;
  isAbstract: boolean;
  line: number;
  endLine: number;
  decorator: 'Component' | 'Injectable' | 'Directive' | 'Pipe' | 'NgModule' | 'None';
  selector?: string;
  standalone?: boolean;
  templateUrl?: string;
  /** Template viết trong decorator (đã phân tích). */
  inlineTemplate?: { nodes: TplElement[]; parseErrors: ParseError[]; heading?: LabelFact };
  /** `imports: [...]` của @Component hoặc @NgModule — chỉ lấy tên định danh. */
  imports: string[];
  /** `declarations: [...]` của @NgModule. */
  declarations: string[];
  /** `bootstrap: [...]` của @NgModule gốc. */
  bootstrap: string[];
  /** `RouterModule.forChild(x)` / `forRoot(x)` trong `imports` của @NgModule. */
  routerRefs: RouterRefFact[];
  extends?: string;
  implements: string[];
  fields: FieldFact[];
  methods: MethodFact[];
}

export interface FieldFact {
  name: string;
  typeName?: string;
  /** `inject(X)` / `@Inject(X)` — X có thể là class hoặc InjectionToken (pha 2 phân biệt). */
  injectArg?: string;
  isStatic: boolean;
  /** Giá trị khởi tạo dạng chuỗi (nếu là biểu thức chuỗi / hằng). */
  init?: UrlPart[];
  line: number;
}

/** Thành phần của một biểu thức chuỗi (URL, đường dẫn route…). */
export type UrlPart =
  | { t: 'lit'; v: string }
  /** Tham chiếu tên: `['API_BASE']`, `['environment','apiBase']`, `['this','base']`. */
  | { t: 'ref'; path: string[] }
  /** Tham số của hàm đang phân tích. */
  | { t: 'param'; name: string }
  /** Không tính được. */
  | { t: 'unk'; text: string };

export interface MethodFact {
  name: string;
  kind: 'method' | 'constructor' | 'accessor' | 'arrow-property';
  params: { name: string; typeName?: string }[];
  returnTypeName?: string;
  /** Đối số generic đầu tiên của kiểu trả về (`Promise<Foo>` → `Foo`). */
  returnTypeArg?: string;
  isStatic: boolean;
  startLine: number;
  endLine: number;
  calls: CallFact[];
  httpCalls: HttpCallFact[];
  uiActions: UiActionFact[];
}

export interface Guard {
  type: 'IF' | 'ELSE' | 'SWITCH' | 'TERNARY' | 'TRY' | 'CATCH' | 'LOOP';
  text?: string;
  branch?: string;
}

/** Mô tả một lời gọi hàm để lần theo kết quả của nó (receiver của lời gọi nối chuỗi, biến gán từ lời gọi). */
export interface CallRef {
  root: string | null;
  path: string[];
  method: string;
  argCount: number;
  /** Kiểu khai báo của biến / tham số gốc (khi `root` không phải `this`). */
  rootType?: string;
  /** Receiver của lời gọi này lại là kết quả của một lời gọi khác. */
  via?: CallRef;
  /** Kết quả của lời gọi được `await` — dùng kiểu bên trong `Promise<T>`. */
  awaited?: boolean;
}

export interface CallFact {
  /** `this` khi gọi qua `this.…`; tên biến / tham số / import khi gọi qua định danh khác; null khi nối chuỗi (`a().b()`). */
  root: string | null;
  /** Các thuộc tính đi từ gốc tới receiver (`this.facade.repo.save()` → `['facade','repo']`). */
  path: string[];
  method: string;
  argCount: number;
  /** Kiểu khai báo của biến / tham số gốc (`const x = inject(Foo)`, `x: Foo`, `new Foo()`); chỉ khi `root` không phải `this`. */
  rootType?: string;
  /**
   * Receiver là kết quả của một lời gọi: nối chuỗi (`this.a.get().save()`) hoặc biến gán từ lời gọi
   * (`const x = this.a.get(); x.save()`). Kiểu của nó suy ra từ kiểu trả về đã khai báo của hàm được gọi.
   */
  via?: CallRef;
  callOrder: number;
  guard?: Guard;
  line: number;
}

export interface HttpCallFact {
  httpMethod: string;
  url: UrlPart[];
  /** Văn bản gốc của biểu thức URL (rút gọn). */
  urlText: string;
  bodyType?: string;
  callOrder: number;
  guard?: Guard;
  line: number;
}

export interface UiActionFact {
  action: 'OPEN_DIALOG' | 'NAVIGATE';
  /** OPEN_DIALOG: tên class; NAVIGATE: đường dẫn (biến đường dẫn ghi `{tên}`). */
  target?: string;
  /** Kiểu của receiver (`MatDialog`, `Router`…) nếu biết. */
  receiverType?: string;
  callOrder: number;
  line: number;
}

export interface RouteArrayFact {
  name?: string;
  exported: boolean;
  routes: RouteFact[];
  line: number;
}

export interface RouteFact {
  path: string;
  component?: string;
  loadComponent?: LazyRef;
  loadChildren?: LazyRef;
  redirectTo?: string;
  title?: string;
  children?: RouteFact[];
  line: number;
}

/** `() => import('./x').then(m => m.Y)`. */
export interface LazyRef {
  importPath: string;
  exportName?: string;
}

export interface RouterRefFact {
  kind: 'forRoot' | 'forChild' | 'provideRouter';
  /** Tên định danh của mảng route. */
  ref?: string;
  inline?: RouteFact[];
  line: number;
}

export interface ProviderFact {
  provide: string;
  useClass?: string;
  useExisting?: string;
  /** Văn bản của factory (để tìm `inject(X)` / `new X`). */
  useFactory?: string;
  line: number;
}

export interface ConstantFact {
  /** Tên đầy đủ: `API_BASE`, `environment.apiBase`, `Api.BASE` (enum / object literal). */
  name: string;
  parts: UrlPart[];
  line: number;
}

export interface DictionaryFact {
  name: string;
  /** Khóa đã làm phẳng (`a.b`) → chuỗi. */
  entries: Record<string, string>;
}

export interface TokenFact {
  name: string;
  /** Kiểu generic của token (`InjectionToken<CategoryRepository>`). */
  typeName?: string;
  line: number;
}

// ---------------------------------------------------------------------------------------------------------------------
// Template
// ---------------------------------------------------------------------------------------------------------------------

export interface TemplateFacts extends FactsBase {
  kind: 'template';
  nodes: TplElement[];
  /** Tiêu đề của template (h1–h4, `mat-dialog-title`). */
  heading?: LabelFact;
}

export interface TplElement {
  tag: string;
  line: number;
  /** Thuộc tính tĩnh (đã chọn lọc: id, type, label, title, placeholder, aria-label, header, name, routerLink…). */
  attrs: Record<string, string>;
  /** Tên các binding (`formGroup`, `ngModel`, `routerLink`, `formControlName`…). */
  bindings: string[];
  events: TplEvent[];
  /** Nhãn hiển thị rút ra từ nội dung (xem {@link LabelFact}). */
  label?: LabelFact;
  children: TplElement[];
}

export interface TplEvent {
  name: string;
  /** Lời gọi trong handler theo thứ tự thực thi. */
  calls: { path: string[]; method: string; order: number }[];
  /** Thuộc tính bị gán (`showFilter = false`). */
  writes: string[];
  line: number;
}

export interface LabelFact {
  /** `text`: chữ tĩnh · `key`: `t('khóa')` · `pipe`: `'khóa' | translate`. */
  kind: 'text' | 'key' | 'pipe';
  value: string;
}

// ---------------------------------------------------------------------------------------------------------------------
// File khác
// ---------------------------------------------------------------------------------------------------------------------

export interface JsonFacts extends FactsBase {
  kind: 'json';
  role: 'angular' | 'tsconfig' | 'proxy' | 'i18n' | 'package' | 'other';
  /** Nội dung đã đọc (angular.json, tsconfig, proxy, package.json) — chỉ giữ khi nhỏ. */
  content?: unknown;
  /** i18n: khóa đã làm phẳng → chuỗi. */
  entries?: Record<string, string>;
}

export interface FederationFacts extends FactsBase {
  kind: 'federation';
  name?: string;
  /** `exposes: { './Routes': './src/routes.ts' }`. */
  exposes: Record<string, string>;
  remotes: string[];
}

export interface OtherFacts extends FactsBase {
  kind: 'other';
}
