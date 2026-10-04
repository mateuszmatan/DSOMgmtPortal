import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { CheckStatus } from '../core/models';

const LOOK: Record<CheckStatus, { label: string; icon: string; tone: string }> = {
  PASS: { label: 'Passed', icon: 'check_circle', tone: 'success' },
  WARN: { label: 'Warning', icon: 'error', tone: 'warning' },
  FAIL: { label: 'Failed', icon: 'cancel', tone: 'danger' },
  BLOCKED: { label: 'Blocked', icon: 'block', tone: 'danger' },
  NOT_REQUIRED: { label: 'Not required', icon: 'remove_circle_outline', tone: 'neutral' },
  SKIP: { label: 'Skipped', icon: 'redo', tone: 'neutral' },
  NO_DATA: { label: 'Not recorded', icon: 'help_outline', tone: 'muted' },
};

@Component({
  selector: 'dso-check-chip',
  imports: [MatIconModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="chip" [class]="look().tone"
    ><mat-icon>{{ look().icon }}</mat-icon
    >{{ look().label }}</span
  >`,
  styles: `
    .chip {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      padding: 1px 8px 1px 5px;
      font-size: 11.5px;
      font-weight: 600;
      line-height: 20px;
      white-space: nowrap;
    }
    mat-icon {
      font-size: 15px;
      width: 15px;
      height: 15px;
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
