import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ReportListComponent } from './report-list.component';

@NgModule({
  declarations: [ReportListComponent],
  imports: [RouterModule.forChild([{ path: '', component: ReportListComponent, title: 'Báo cáo' }])]
})
export class ReportsModule {}
