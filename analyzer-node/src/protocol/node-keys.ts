/**
 * Quy tắc node key phía sidecar — PHẢI khớp engine/flowtracer-ir/.../NodeKeys.java.
 * ĐỔI QUY TẮC = BREAKING CHANGE (mất liên kết chốt tay, nhóm chức năng, cache AI).
 */
export const NodeKeys = {
  ngElement: (app: string, component: string, elementId: string): string => `ng:element:${app}/${component}#${elementId}`,
  ngHttpCall: (app: string, className: string, method: string, index: number): string =>
    `ng:http:${app}/${className}#${method}:${index}`,
  ngScreen: (app: string, routePath: string): string => `ng:screen:${app}${routePath.startsWith('/') ? '' : '/'}${routePath}`,
  ngMethod: (app: string, className: string, method: string): string => `ng:method:${app}/${className}#${method}`,
  ngApp: (app: string): string => `ng:app:${app}`
};
