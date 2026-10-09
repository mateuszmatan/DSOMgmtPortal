import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DailyActivity } from '../core/models';
import { counted } from '../shared/formatting';

const WIDTH = 800;
const HEIGHT = 190;
const PADDING = { left: 34, right: 8, top: 16, bottom: 26 };

const dayFormat = new Intl.DateTimeFormat('en', {
  day: 'numeric',
  month: 'short',
  timeZone: 'UTC',
});

@Component({
  selector: 'dso-activity-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let c = chart();
    <svg
      [attr.viewBox]="'0 0 ' + c.width + ' ' + c.height"
      role="img"
      [attr.aria-label]="c.summary"
    >
      @for (tick of c.yTicks; track tick.value) {
        <line
          class="grid"
          [attr.x1]="c.left"
          [attr.x2]="c.width - c.right"
          [attr.y1]="tick.y"
          [attr.y2]="tick.y"
        />
        <text class="axis" [attr.x]="c.left - 6" [attr.y]="tick.y + 4" text-anchor="end">
          {{ tick.value }}
        </text>
      }
      @for (bar of c.bars; track bar.date) {
        <g>
          <title>{{ bar.title }}</title>
          <rect
            class="hit"
            [attr.x]="bar.slotX"
            [attr.y]="c.top"
            [attr.width]="bar.slotWidth"
            [attr.height]="c.plotHeight"
          />
          @if (bar.successHeight > 0) {
            <rect
              class="success"
              [attr.x]="bar.x"
              [attr.y]="bar.successY"
              [attr.width]="bar.width"
              [attr.height]="bar.successHeight"
            />
          }
          @if (bar.failureHeight > 0) {
            <rect
              class="failure"
              [attr.x]="bar.x"
              [attr.y]="bar.failureY"
              [attr.width]="bar.width"
              [attr.height]="bar.failureHeight"
            />
          }
          @if (bar.deployments > 0) {
            <rect
              class="deployment"
              [attr.x]="bar.x + bar.width / 2 - 3"
              [attr.y]="bar.failureY - 10"
              width="6"
              height="6"
            />
          }
        </g>
      }
      @for (tick of c.xTicks; track tick.label) {
        <text
          class="axis"
          [attr.x]="tick.x"
          [attr.y]="c.height - 6"
          [attr.text-anchor]="tick.anchor"
        >
          {{ tick.label }}
        </text>
      }
    </svg>
    <div class="legend">
      <span><i class="swatch success"></i>Successful runs</span>
      <span><i class="swatch failure"></i>Failed or unstable runs</span>
      <span><i class="swatch deployment"></i>Deployed that day</span>
    </div>
  `,
  styles: `
    :host {
      display: block;
    }
    svg {
      width: 100%;
      height: auto;
      display: block;
    }
    .grid {
      stroke: #eceff4;
      stroke-width: 1;
    }
    .axis {
      fill: #80868b;
      font-size: 11px;
    }
    .hit {
      fill: transparent;
    }
    g:hover .hit {
      fill: rgba(26, 95, 180, 0.06);
    }
    .success {
      fill: var(--dso-run-success);
    }
    .failure {
      fill: var(--dso-run-failure);
    }
    .deployment {
      fill: var(--dso-run-deploy);
    }
    .legend {
      margin-top: 8px;
    }
  `,
})
export class ActivityChart {
  readonly daily = input.required<DailyActivity[]>();

  protected readonly chart = computed(() => {
    const days = this.daily();
    const plotWidth = WIDTH - PADDING.left - PADDING.right;
    const plotHeight = HEIGHT - PADDING.top - PADDING.bottom;
    const max = niceMax(Math.max(1, ...days.map((day) => day.runs)));
    const slot = plotWidth / Math.max(days.length, 1);
    const width = Math.max(1, Math.min(slot * 0.7, 24));
    const y = (value: number) => PADDING.top + plotHeight - (value / max) * plotHeight;

    const bars = days.map((day, index) => {
      const successes = Math.max(0, day.runs - day.failures);
      const slotX = PADDING.left + index * slot;
      return {
        date: day.date,
        slotX,
        slotWidth: slot,
        x: slotX + (slot - width) / 2,
        width,
        successY: y(successes),
        successHeight: (successes / max) * plotHeight,
        failureY: y(day.runs),
        failureHeight: (day.failures / max) * plotHeight,
        deployments: day.deployments,
        title: `${label(day.date)}: ${counted(day.runs, 'run')}, ${day.failures} failed, ${counted(day.deployments, 'deployment')}`,
      };
    });

    const tickIndexes = [
      ...new Set([0, Math.floor((days.length - 1) / 2), days.length - 1]),
    ].filter((i) => i >= 0 && i < days.length);
    const xTicks = tickIndexes.map((index, position) => ({
      label: label(days[index].date),
      x:
        index === 0
          ? PADDING.left
          : index === days.length - 1
            ? WIDTH - PADDING.right
            : bars[index].x + width / 2,
      anchor: position === 0 ? 'start' : index === days.length - 1 ? 'end' : 'middle',
    }));
    const yTicks = [0, max / 2, max].map((value) => ({ value, y: y(value) }));
    const runs = days.reduce((sum, day) => sum + day.runs, 0);

    return {
      width: WIDTH,
      height: HEIGHT,
      left: PADDING.left,
      right: PADDING.right,
      top: PADDING.top,
      plotHeight,
      bars,
      xTicks,
      yTicks,
      summary: `${counted(runs, 'run')} over ${counted(days.length, 'day')}`,
    };
  });
}

function label(date: string): string {
  return dayFormat.format(new Date(`${date}T00:00:00Z`));
}

function niceMax(value: number): number {
  return value <= 2 ? 2 : Math.ceil(value / 2) * 2;
}
