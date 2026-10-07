import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { CheckStatus } from '../core/models';

export const CHECK_LOOK: Record<CheckStatus, { label: string; tone: string }> = {
  PASS: { label: 'Passed', tone: 'success' },
  WARN: { label: 'Warning', tone: 'warning' },
  FAIL: { label: 'Failed', tone: 'danger' },
  BLOCKED: { label: 'Blocked', tone: 'danger' },
  NOT_REQUIRED: { label: 'Not required', tone: 'neutral' },
  SKIP: { label: 'Skipped', tone: 'neutral' },
  NO_DATA: { label: 'Not recorded', tone: 'absent' },
};

@Component({
  selector: 'dso-check-chip',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="chip" [class]="look().tone">{{ look().label }}</span>`,
})
export class CheckChip {
  readonly status = input.required<CheckStatus>();
  protected readonly look = computed(() => CHECK_LOOK[this.status()] ?? CHECK_LOOK.NO_DATA);
}
