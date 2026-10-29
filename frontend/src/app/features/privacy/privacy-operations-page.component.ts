import { ChangeDetectionStrategy, Component } from '@angular/core';
import { PrivacyRightsPageComponent } from './privacy-rights-page.component';

@Component({
  selector: 'app-privacy-operations-page', standalone: true,
  imports: [PrivacyRightsPageComponent], changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<app-privacy-rights-page [operational]="true" />`,
})
export class PrivacyOperationsPageComponent {}
