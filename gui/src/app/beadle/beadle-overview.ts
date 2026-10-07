import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { BEADLE, BEADLE_ADMIN, CHANGES } from '../core/sections';

@Component({
  selector: 'dso-beadle-overview',
  imports: [RouterLink, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <header class="page-header">
        <div>
          <h1>{{ section.heading }}</h1>
          <p class="page-description">{{ section.description }}</p>
        </div>
      </header>
      <div class="card feature">
        <h3>{{ changes.heading }}</h3>
        <p>
          Raise a ServiceNow change for a production release: choose the department, the product and
          its services, the Jira epics and stories and the change window. The portal writes the
          change and one change task per service.
        </p>
        <a mat-flat-button [routerLink]="changes.path">Open</a>
      </div>
      <div class="card feature">
        <h3>{{ admin.heading }}</h3>
        <p>
          Keep the departments, products and services and the defaults of their ServiceNow changes,
          which every production change starts from.
        </p>
        <a mat-flat-button [routerLink]="admin.path">Open</a>
      </div>
    </div>
  `,
  styles: `
    .feature {
      max-width: 560px;
      margin-bottom: 12px;
      padding: 14px 16px;

      h3 {
        margin: 0 0 4px;
        font-size: 16px;
      }

      p {
        margin: 0 0 12px;
        color: var(--dso-muted);
      }
    }
  `,
})
export class BeadleOverview {
  protected readonly section = BEADLE;
  protected readonly admin = BEADLE_ADMIN;
  protected readonly changes = CHANGES;
}
