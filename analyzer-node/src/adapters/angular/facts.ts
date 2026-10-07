/** Dữ kiện pha 1 của một file Angular (.ts / .html / file i18n). Serialize JSON để cache. */
export interface AngularFileFacts {
  path: string;
  hash: string;
  kind: 'ts' | 'template' | 'i18n' | 'config' | 'other';
  classes: ClassFact[];
  routes: RouteFact[];
  providers: ProviderFact[];
  calls: CallFact[];
  httpCalls: HttpCallFact[];
  uiActions: UiActionFact[];
  constants: ConstantFact[];
  templateElements: TemplateElementFact[];
  i18n: Record<string, string>;
  parseErrors: string[];
}

export interface ClassFact {
  name: string;
  decorator: 'Component' | 'Injectable' | 'Directive' | 'Pipe' | 'None';
  selector?: string;
  templatePath?: string;
  injected: { name: string; typeOrToken: string }[];
  methods: { name: string; startLine: number; endLine: number }[];
  line: number;
}

export interface RouteFact {
  path: string;
  component?: string;
  loadComponent?: string;
  loadChildren?: string;
  remote?: string;
  title?: string;
  line: number;
}

export interface ProviderFact {
  provide: string;
  useClass?: string;
  useExisting?: string;
  useFactory?: string;
  scope: 'component' | 'route' | 'feature' | 'root';
  line: number;
}

export interface Guard {
  type: 'IF' | 'ELSE' | 'SWITCH' | 'TERNARY' | 'TRY' | 'CATCH' | 'LOOP';
  text?: string;
  branch?: string;
}

export interface CallFact {
  callerClass: string;
  callerMethod: string;
  receiver: string;
  method: string;
  callOrder: number;
  guard?: Guard;
  line: number;
}

export interface HttpCallFact {
  callerClass: string;
  callerMethod: string;
  httpMethod: string;
  urlExpr: string;
  bodyType?: string;
  callOrder: number;
  line: number;
}

export interface UiActionFact {
  callerClass: string;
  callerMethod: string;
  action: 'OPEN_DIALOG' | 'NAVIGATE' | 'SWITCH_TAB' | 'RESET_FORM';
  target?: string;
  line: number;
}

export interface ConstantFact {
  name: string;
  value: string;
  exported: boolean;
  line: number;
}

export interface TemplateElementFact {
  id: string;
  tag: string;
  event: string;
  handler: string;
  label?: string;
  i18nKey?: string;
  insideForm?: string;
  insideTab?: string;
  line: number;
}
