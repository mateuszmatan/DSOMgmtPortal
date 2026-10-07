import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DoraSummary } from '../core/models';
import { DoraLevelBadge } from '../shared/dora-level';
import { counted, formatDuration } from '../shared/formatting';

const moment = new Intl.DateTimeFormat('en-GB', {
  day: 'numeric',
  month: 'short',
  hour: '2-digit',
  minute: '2-digit',
});

@Component({
  selector: 'dso-dora-tiles',
  imports: [DoraLevelBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @for (tile of tiles(); track tile.title) {
      <div class="card tile">
        <div class="tile-head">
          <span class="tile-title">{{ tile.title }}</span>
          <dso-dora-level [level]="tile.level" />
        </div>
        <div class="tile-value">{{ tile.value }}</div>
        <div class="muted tile-detail">{{ tile.detail }}</div>
        @if (tile.alert) {
          <div class="tile-alert">{{ tile.alert }}</div>
        }
      </div>
    }
  `,
  styles: `
    :host {
      display: grid;
      grid-template-columns: repeat(4, minmax(0, 1fr));
      gap: 8px;
      margin-bottom: 10px;
    }
    .tile {
      padding: 10px 14px;
    }
    .tile-head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
    }
    .tile-title {
      font-size: 12px;
      font-weight: 600;
      color: var(--dso-muted);
    }
    .tile-value {
      margin-top: 2px;
      font-family: var(--dso-serif);
      font-size: 22px;
      font-weight: 500;
      color: var(--dso-navy);
    }
    .tile-detail {
      font-size: 11.5px;
    }
    .tile-alert {
      margin-top: 4px;
      color: var(--dso-danger);
      font-size: 11.5px;
      font-weight: 600;
    }
    @media (max-width: 1100px) {
      :host {
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }
    }
  `,
})
export class DoraTiles {
  readonly dora = input.required<DoraSummary>();

  protected readonly tiles = computed(() => doraTiles(this.dora()));
}

interface DoraTile {
  title: string;
  value: string;
  detail: string;
  level: DoraSummary['deploymentFrequencyLevel'];
  alert?: string;
}

export function doraTiles(dora: DoraSummary): DoraTile[] {
  return [
    {
      title: 'Deployment frequency',
      value: frequency(dora.deploymentsPerWeek),
      detail: `${counted(dora.deployments, 'deployment')} in ${counted(dora.rangeDays, 'day')}`,
      level: dora.deploymentFrequencyLevel,
    },
    {
      title: 'Lead time for changes',
      value: formatDuration(dora.leadTimeMedianSeconds),
      detail: 'Median from commit to deployment',
      level: dora.leadTimeLevel,
    },
    {
      title: 'Change failure rate',
      value:
        dora.changeFailureRatePercent === null
          ? '–'
          : `${dora.changeFailureRatePercent.toFixed(1)}%`,
      detail: `Of ${counted(dora.deployments, 'deployment')} in the range`,
      level: dora.changeFailureRateLevel,
    },
    {
      title: 'Time to restore',
      value: formatDuration(dora.meanTimeToRestoreSeconds),
      detail: `Mean of ${counted(dora.restores, 'recovery', 'recoveries')} from a failed deployment`,
      level: dora.timeToRestoreLevel,
      alert: dora.failingSince
        ? `Failing since ${moment.format(new Date(dora.failingSince))}`
        : undefined,
    },
  ];
}

function frequency(perWeek: number | null): string {
  if (perWeek === null) {
    return '–';
  }
  if (perWeek >= 7) {
    return `${(perWeek / 7).toFixed(1)} / day`;
  }
  if (perWeek >= 1) {
    return `${perWeek.toFixed(1)} / week`;
  }
  return `${((perWeek * 30) / 7).toFixed(1)} / month`;
}
