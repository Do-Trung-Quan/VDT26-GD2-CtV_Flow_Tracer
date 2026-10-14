import { Component } from '@angular/core';

@Component({ selector: 'app-report-list', template: '<h2>Báo cáo</h2><button id="btn-print" (click)="print()">In</button>' })
export class ReportListComponent {
  print(): void {
    window.print();
  }
}
