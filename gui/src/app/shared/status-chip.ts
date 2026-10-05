import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RunResult } from '../core/models';

const LOOK: Record<RunResult, { label: string; tone: string }> = {
  SUCCESS: { label: 'Success', tone: 'success' },
  UNSTABLE: { label: 'Unstable', tone: 'warning' },
  FAILURE: { label: 'Failed', tone: 'danger' },
  ABORTED: { label: 'Aborted', tone: 'neutral' },
  NOT_BUILT: { label: 'Not built', tone: 'neutral' },
  NO_DATA: { label: 'No runs yet', tone: 'neutral' },
  DISABLED: { label: 'Key invalidated', tone: 'outline' },
};

@Component({
  selector: 'dso-status-chip',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="chip run" [class]="look().tone">{{ label() ?? look().label }}</span>`,
})
export class StatusChip {
  readonly status = input.required<RunResult>();
  readonly label = input<string>();
  protected readonly look = computed(() => LOOK[this.status()] ?? LOOK.NO_DATA);
}
