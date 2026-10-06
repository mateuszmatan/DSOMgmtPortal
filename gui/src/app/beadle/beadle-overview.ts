import { ChangeDetectionStrategy, Component } from '@angular/core';
import { BEADLE } from '../core/sections';

@Component({
  selector: 'dso-beadle-overview',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <header class="page-header">
        <div>
          <h1>{{ section.heading }}</h1>
          <p class="page-description">{{ section.description }}</p>
        </div>
      </header>
      <div class="card empty-state">
        <h3>No features yet</h3>
        <p>New features of the portal will appear here.</p>
      </div>
    </div>
  `,
})
export class BeadleOverview {
  protected readonly section = BEADLE;
}
