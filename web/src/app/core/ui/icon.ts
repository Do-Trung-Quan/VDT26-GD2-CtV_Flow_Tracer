import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type IconName = 'home' | 'layout' | 'gear' | 'sparkle' | 'upload' | 'search' | 'plus' | 'chev' | 'x' | 'folder' | 'alert' | 'check' | 'trash' | 'edit' | 'server' | 'monitor' | 'info' | 'dots' | 'git' | 'minus' | 'fit' | 'download' | 'code' | 'copy' | 'chevd' | 'image';

/** Icon SVG nét mảnh (stroke 1.8) theo bản vẽ giao diện; không tải gì từ mạng. */
@Component({
  selector: 'app-icon',
  template: `
    <svg class="i" [class.sm]="small()" viewBox="0 0 24 24" aria-hidden="true">
    @switch (name()) {
      @case ('home') {
        <svg:path d="M3 11l9-8 9 8"/><svg:path d="M5 10v10h14V10"/>
      }
      @case ('layout') {
        <svg:rect x="3" y="4" width="18" height="16" rx="2"/><svg:path d="M9 4v16M15 4v16"/>
      }
      @case ('gear') {
        <svg:circle cx="12" cy="12" r="3"/><svg:path d="M12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9l2.1 2.1M17 17l2.1 2.1M19.1 4.9L17 7M7 17l-2.1 2.1"/>
      }
      @case ('sparkle') {
        <svg:path d="M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8z"/>
      }
      @case ('upload') {
        <svg:path d="M12 15V3M7 8l5-5 5 5M4 21h16"/>
      }
      @case ('search') {
        <svg:circle cx="11" cy="11" r="7"/><svg:path d="m20 20-3.5-3.5"/>
      }
      @case ('plus') {
        <svg:path d="M12 5v14M5 12h14"/>
      }
      @case ('chev') {
        <svg:path d="m9 6 6 6-6 6"/>
      }
      @case ('x') {
        <svg:path d="M6 6l12 12M18 6L6 18"/>
      }
      @case ('folder') {
        <svg:path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
      }
      @case ('alert') {
        <svg:path d="M12 3l10 18H2z"/><svg:path d="M12 10v5M12 18h.01"/>
      }
      @case ('check') {
        <svg:path d="m5 12 5 5 9-10"/>
      }
      @case ('trash') {
        <svg:path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"/>
      }
      @case ('edit') {
        <svg:path d="M4 20h4L19 9l-4-4L4 16z"/>
      }
      @case ('server') {
        <svg:rect x="3" y="4" width="18" height="7" rx="2"/><svg:rect x="3" y="13" width="18" height="7" rx="2"/><svg:path d="M7 8h.01M7 17h.01"/>
      }
      @case ('monitor') {
        <svg:rect x="3" y="4" width="18" height="12" rx="2"/><svg:path d="M8 20h8M12 16v4"/>
      }
      @case ('info') {
        <svg:circle cx="12" cy="12" r="9"/><svg:path d="M12 11v5M12 8h.01"/>
      }
      @case ('dots') {
        <svg:path d="M12 6h.01M12 12h.01M12 18h.01"/>
      }
      @case ('minus') {
        <svg:path d="M5 12h14"/>
      }
      @case ('fit') {
        <svg:path d="M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5"/>
      }
      @case ('download') {
        <svg:path d="M12 3v12M7 10l5 5 5-5M4 21h16"/>
      }
      @case ('code') {
        <svg:path d="m8 7-5 5 5 5M16 7l5 5-5 5M14 4l-4 16"/>
      }
      @case ('copy') {
        <svg:rect x="9" y="9" width="11" height="11" rx="2"/><svg:path d="M5 15V6a2 2 0 0 1 2-2h9"/>
      }
      @case ('chevd') {
        <svg:path d="m6 9 6 6 6-6"/>
      }
      @case ('image') {
        <svg:rect x="3" y="4" width="18" height="16" rx="2"/><svg:circle cx="9" cy="10" r="1.6"/><svg:path d="m21 16-5-5-9 9"/>
      }
      @case ('git') {
        <svg:circle cx="6" cy="6" r="2"/><svg:circle cx="6" cy="18" r="2"/><svg:circle cx="18" cy="9" r="2"/><svg:path d="M6 8v8M18 11c0 4-6 3-11 6"/>
      }
    }
    </svg>
  `,
  styles: `:host { display: inline-flex; }`,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class Icon {
  readonly name = input.required<IconName>();
  readonly small = input(false);
}
