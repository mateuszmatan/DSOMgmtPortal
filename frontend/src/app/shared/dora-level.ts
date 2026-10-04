import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { DoraLevel } from '../core/models';

/** DORA performance cluster as a small badge. */
@Component({
  selector: 'dso-dora-level',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `@if (level(); as value) {
    <span class="level" [class]="value.toLowerCase()">{{
      value.charAt(0) + value.slice(1).toLowerCase()
    }}</span>
  }`,
  styles: `
    .level {
      display: inline-block;
      padding: 1px 8px;

      font-size: 11px;
      font-weight: 700;
      letter-spacing: 0.04em;
      text-transform: uppercase;
    }
    .elite {
      background: #e6f4ea;
      color: #137333;
    }
    .high {
      background: #e8f0fe;
      color: #1a5fb4;
    }
    .medium {
      background: #fef3e0;
      color: #b06000;
    }
    .low {
      background: #fce8e6;
      color: #c5221f;
    }
  `,
})
export class DoraLevelBadge {
  readonly level = input<DoraLevel | null>(null);
}
