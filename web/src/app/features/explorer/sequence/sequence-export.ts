import type { Measure } from './sequence-layout';
import type { SequenceTrace } from './sequence-model';

/** Đo chữ bằng canvas của trình duyệt; không có canvas (vd môi trường test) thì ước lượng theo số ký tự. */
export function createMeasure(): Measure {
  let context: CanvasRenderingContext2D | null | undefined;
  return (text, size = 12.5, bold = false) => {
    if (context === undefined) {
      context = typeof document === 'undefined' ? null : tryContext();
    }
    if (!context) {
      return text.length * size * (bold ? 0.58 : 0.54);
    }
    context.font = `${bold ? '700 ' : ''}${size}px "Segoe UI", Arial, sans-serif`;
    return context.measureText(text).width;
  };
}

function tryContext(): CanvasRenderingContext2D | null {
  try {
    return document.createElement('canvas').getContext('2d');
  } catch {
    return null;
  }
}

/** Tên file gợi ý từ tên API: ký tự không phải chữ/số thành dấu gạch dưới. */
export function fileBase(trace: SequenceTrace): string {
  const base = trace.startName.replace(/[^\p{L}\p{N}]+/gu, '_').replace(/^_+|_+$/g, '');
  return base || 'luong';
}

export function downloadBlob(name: string, blob: Blob): void {
  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = name;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(link.href), 1500);
}

/** Chuyển SVG sang PNG (gấp đôi độ phân giải) ngay trên trình duyệt. */
export function svgToPng(svg: string): Promise<Blob> {
  const match = svg.match(/viewBox="0 0 ([\d.]+) ([\d.]+)"/);
  const width = match ? Number(match[1]) : 800;
  const height = match ? Number(match[2]) : 600;
  return new Promise((resolve, reject) => {
    const image = new Image();
    image.onload = () => {
      const canvas = document.createElement('canvas');
      canvas.width = width * 2;
      canvas.height = height * 2;
      const g = canvas.getContext('2d');
      if (!g) {
        reject(new Error('Trình duyệt không tạo được canvas.'));
        return;
      }
      g.fillStyle = '#fff';
      g.fillRect(0, 0, canvas.width, canvas.height);
      g.drawImage(image, 0, 0, canvas.width, canvas.height);
      canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error('Không tạo được ảnh PNG.'))), 'image/png');
    };
    image.onerror = () => reject(new Error('Trình duyệt không xuất được PNG từ SVG này.'));
    image.src = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg);
  });
}
