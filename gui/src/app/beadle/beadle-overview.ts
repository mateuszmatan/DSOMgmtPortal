import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { BEADLE, ONBOARDING } from '../core/sections';

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
        <h3>{{ onboarding.heading }}</h3>
        <p>
          Set up DevSecOps for your product in five short steps: choose a pipeline, describe the
          product and its services, and get the Jenkinsfile for each service. No DevSecOps knowledge
          needed.
        </p>
        <a mat-flat-button [routerLink]="onboarding.path">Start</a>
      </div>
    </div>
  `,
  styles: `
    .feature {
      max-width: 560px;
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
  protected readonly onboarding = ONBOARDING;
}
