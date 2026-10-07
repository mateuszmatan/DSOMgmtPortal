import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { MatTooltipModule } from '@angular/material/tooltip';

export interface BarSegment {
  swatch: string;
  label: string;
  count: number;
}

export interface BarRow {
  label: string;
  note: string;
  segments: BarSegment[];
}

@Component({
  selector: 'dso-bar-chart',
  imports: [MatTooltipModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @for (row of bars(); track row.label) {
      <div class="row">
        <span class="label">{{ row.label }}</span>
        <div class="track" role="img" [attr.aria-label]="row.summary">
          @for (segment of row.segments; track segment.label) {
            <span
              class="swatch"
              [class]="segment.swatch"
              [style.width.%]="segment.width"
              [matTooltip]="segment.count + ' ' + segment.label"
            ></span>
          }
        </div>
        <span class="note muted">{{ row.note }}</span>
      </div>
    }
  `,
  styles: `
    :host {
      display: grid;
      grid-template-columns: auto minmax(60px, 1fr) auto;
      align-items: center;
      gap: 6px 12px;
      font-size: 12.5px;
    }
    .row {
      display: contents;
    }
    .track {
      display: flex;
      gap: 2px;
      height: 12px;
    }
    .note {
      font-size: 12px;
    }
  `,
})
export class BarChart {
  readonly rows = input.required<BarRow[]>();

  protected readonly bars = computed(() => {
    const rows = this.rows();
    const total = (row: BarRow) => row.segments.reduce((sum, segment) => sum + segment.count, 0);
    const largest = Math.max(1, ...rows.map(total));
    return rows.map((row) => {
      const segments = row.segments.filter((segment) => segment.count > 0);
      return {
        ...row,
        segments: segments.map((segment) => ({
          ...segment,
          width: (segment.count / largest) * 100,
        })),
        summary:
          `${row.label}: ` +
          (segments.map((segment) => `${segment.count} ${segment.label}`).join(', ') || 'none'),
      };
    });
  });
}
