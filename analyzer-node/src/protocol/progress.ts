import type { ProgressEvent } from './generated/progress-event.js';

/** In một sự kiện tiến độ ra stderr dạng NDJSON (một dòng một JSON). */
export function reportProgress(event: ProgressEvent): void {
  process.stderr.write(JSON.stringify(event) + '\n');
}
