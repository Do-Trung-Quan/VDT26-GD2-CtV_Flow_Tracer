/** Luật Linker từ proxy.conf.json (tiền tố, pathRewrite). */
export interface ProxyRule {
  context: string;
  pathRewrite: Record<string, string>;
}

/** Đọc proxy.conf.json (JSON thuần). Không hỗ trợ proxy.conf.js vì không được chạy mã dự án. */
export function parseProxyConfig(json: string): ProxyRule[] {
  const config = JSON.parse(json) as Record<string, { pathRewrite?: Record<string, string> }>;
  return Object.entries(config)
    .map(([context, value]) => ({ context, pathRewrite: value.pathRewrite ?? {} }))
    .sort((a, b) => a.context.localeCompare(b.context));
}
