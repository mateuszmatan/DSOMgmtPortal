import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { CheckStatus } from '../core/models';

const LOOK: Record<CheckStatus, { label: string; tone: string }> = {
  PASS: { label: 'Passed', tone: 'success' },
  WARN: { label: 'Warning', tone: 'warning' },
  FAIL: { label: 'Failed', tone: 'danger' },
  BLOCKED: { label: 'Blocked', tone: 'danger' },
  NOT_REQUIRED: { label: 'Not required', tone: 'neutral' },
  SKIP: { label: 'Skipped', tone: 'neutral' },
  NO_DATA: { label: 'Not recorded', tone: 'muted' },
};

@Component({
  selector: 'dso-check-chip',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="chip" [class]="look().tone">{{ look().label }}</span>`,
  styles: `
    .chip {
      display: inline-block;
      padding: 0 6px;
      font-size: 11px;
      font-weight: 600;
      line-height: 18px;
      white-space: nowrap;
    }
    .success {
      background: var(--dso-success-bg);
      color: var(--dso-success);
    }
    .warning {
      background: var(--dso-warning-bg);
      color: var(--dso-warning);
    }
    .danger {
      background: var(--dso-danger-bg);
      color: var(--dso-danger);
    }
    .neutral {
      background: var(--dso-neutral-bg);
      color: var(--dso-neutral);
    }
    .muted {
      background: transparent;
      color: var(--dso-muted);
      box-shadow: inset 0 0 0 1px var(--dso-border);
      font-style: italic;
      font-weight: 500;
    }
  `,
})
export class CheckChip {
  readonly status = input.required<CheckStatus>();
  protected readonly look = computed(() => LOOK[this.status()] ?? LOOK.NO_DATA);
}
