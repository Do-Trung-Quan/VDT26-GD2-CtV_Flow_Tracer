import type { InputFile } from '../../adapter.js';
import type { AngularFileFacts } from '../facts.js';

/**
 * PHA 1: chọn extractor theo loại file (.ts dùng ts.createSourceFile, template dùng parseTemplate của @angular/compiler,
 * file i18n đọc khóa → chuỗi). TODO: class-decl, route, provider, method-call, http-call, ui-action, constant,
 * template, i18n extractor.
 */
export async function extractFile(file: InputFile): Promise<AngularFileFacts> {
  void file;
  throw new Error('TODO: chưa triển khai');
}
