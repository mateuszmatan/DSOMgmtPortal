import { ClipboardModule } from '@angular/cdk/clipboard';
import { CdkMenu, CdkMenuItem, CdkMenuTrigger } from '@angular/cdk/menu';
import { ConnectedPosition } from '@angular/cdk/overlay';
import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { catchError, finalize, map, of } from 'rxjs';
import { MonitoringApi, PipelinesApi, SettingsApi } from '../core/api';
import { errorMessage } from '../core/errors';
import { Pipeline, PipelineRun, pipelineTypeLabel } from '../core/models';
import { PIPELINES, adminProduct } from '../core/sections';
import { MetricsBanner } from '../monitoring/metrics-banner';
import { jenkinsfile } from '../products/jenkinsfile';
import { BuildLink } from '../shared/build-link';
import { RelativeTimePipe, formatDuration } from '../shared/formatting';
import { RUN_LOOK, StatusChip } from '../shared/status-chip';
import { GRID, GridColumn } from '../ui/grid';
import { DsoLoading } from '../ui/loading';
import { PipelineActions } from './pipeline-actions';

const RECENT_RUNS = 5;

const MENU_BEFORE: ConnectedPosition[] = [
  { originX: 'end', originY: 'bottom', overlayX: 'end', overlayY: 'top' },
  { originX: 'end', originY: 'top', overlayX: 'end', overlayY: 'bottom' },
];

@Component({
  selector: 'dso-pipeline-page',
  imports: [
    ClipboardModule,
    CdkMenu,
    CdkMenuItem,
    CdkMenuTrigger,
    DatePipe,
    RouterLink,
    GRID,
    DsoLoading,
    BuildLink,
    MetricsBanner,
    RelativeTimePipe,
    StatusChip,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <nav class="breadcrumb" aria-label="Breadcrumb">
        <a [routerLink]="section.path">{{ section.heading }}</a>
        <span class="sep" aria-hidden="true">/</span>
        @if (pipeline.hasValue()) {
          @let p = pipeline.value();
          <span>{{ p.productName }}</span>
          <span class="sep" aria-hidden="true">/</span>
          <span>{{ p.serviceName }} · {{ typeLabel(p.type) }}</span>
        } @else {
          <span>Pipeline</span>
        }
      </nav>

      @if (pipeline.isLoading() && !pipeline.hasValue()) {
        <dso-loading />
      }

      @if (pipeline.error(); as error) {
        <div class="banner">{{ errorMessage(error) }}</div>
        <a class="btn btn-outline-primary" [routerLink]="section.path">Back to pipelines</a>
      } @else if (pipeline.hasValue()) {
        @let p = pipeline.value();
        <header class="page-header">
          <div>
            <div class="title row-wrap">
              <h1>
                <span class="mono">{{ p.serviceName }}</span> · {{ typeLabel(p.type) }} pipeline
              </h1>
              @if (health(); as h) {
                <dso-status-chip [status]="h.status" />
              }
            </div>
            <p>
              {{ p.productName }} <span class="mono muted">{{ p.productCode }}</span> ·
              <span class="mono">{{ p.entryPoint }}</span>
            </p>
          </div>
          <div class="actions">
            @if (p.jenkinsJobUrl; as job) {
              <a class="btn btn-outline-primary" [href]="job" target="_blank" rel="noopener"
                >Jenkins</a
              >
            }
            <a class="btn btn-outline-primary" [routerLink]="['/monitoring/pipelines', p.id]"
              >Metrics</a
            >
            <a class="btn btn-outline-primary" [routerLink]="productLink(p.productId)">Product</a>
            <button
              type="button"
              class="btn btn-outline-primary"
              [cdkMenuTriggerFor]="more"
              [cdkMenuPosition]="menuBefore"
            >
              More
            </button>
            <ng-template #more>
              <div cdkMenu class="dropdown-menu dso-menu">
                <button cdkMenuItem class="dropdown-item" (click)="actions.showConfig(p)">
                  config.yaml
                </button>
                <button cdkMenuItem class="dropdown-item" (click)="keyHistory(p)">
                  Key history
                </button>
                <hr class="dropdown-divider" />
                @if (p.activeKey) {
                  <button cdkMenuItem class="dropdown-item" (click)="replaceKey(p)">
                    Replace key
                  </button>
                  <button cdkMenuItem class="dropdown-item danger" (click)="revokeKey(p)">
                    Invalidate key
                  </button>
                }
                <button cdkMenuItem class="dropdown-item danger" (click)="deletePipeline(p)">
                  Delete pipeline
                </button>
              </div>
            </ng-template>
            <button type="button" class="btn btn-primary" (click)="edit(p)">Edit</button>
          </div>
        </header>

        @if (!p.activeKey) {
          <div class="banner danger" role="status">
            <span class="banner-text"
              >The key is invalidated, so the pipeline is refused its configuration and stops at its
              next start.</span
            >
            <button
              type="button"
              class="btn btn-primary"
              [disabled]="regenerating()"
              (click)="regenerateKey(p)"
            >
              {{ regenerating() ? 'Regenerating…' : 'Regenerate key' }}
            </button>
          </div>
        }

        <div class="columns">
          <section class="card panel">
            <header class="card-header"><h2>Key</h2></header>
            @if (p.activeKey; as key) {
              <div class="key row-wrap">
                <span class="mono key-value">{{
                  revealed() && key.value ? key.value : key.hint
                }}</span>
                @if (key.value; as value) {
                  <button type="button" class="text-link" (click)="revealed.set(!revealed())">
                    {{ revealed() ? 'Hide' : 'Show' }}
                  </button>
                  <button
                    type="button"
                    class="text-link"
                    [cdkCopyToClipboard]="value"
                    (cdkCopyToClipboardCopied)="actions.copied()"
                  >
                    Copy
                  </button>
                }
              </div>
              <dl class="pairs">
                <div>
                  <dt>Issued</dt>
                  <dd>{{ key.issuedAt | relative }}</dd>
                </div>
                <div>
                  <dt>Last fetched over REST</dt>
                  <dd>{{ key.lastUsedAt ? (key.lastUsedAt | relative) : 'Never' }}</dd>
                </div>
                <div>
                  <dt>Earlier keys</dt>
                  <dd>{{ (p.keys?.length ?? 1) - 1 }}</dd>
                </div>
              </dl>
            } @else {
              <p class="muted">No active key.</p>
            }
          </section>

          <section class="card panel">
            <header class="card-header"><h2>Settings</h2></header>
            <dl class="pairs">
              <div>
                <dt>Agents</dt>
                <dd class="mono">{{ p.agentLabels.join(', ') }}</dd>
              </div>
              <div>
                <dt>Jenkins job</dt>
                <dd class="mono">{{ p.jenkinsJob ?? 'Not set' }}</dd>
              </div>
              @if (p.extendedPipelineJob) {
                <div>
                  <dt>Extended pipeline</dt>
                  <dd class="mono">{{ p.extendedPipelineJob }}</dd>
                </div>
              }
              @if (p.securityPipelineJob) {
                <div>
                  <dt>Security pipeline</dt>
                  <dd class="mono">{{ p.securityPipelineJob }}</dd>
                </div>
              }
              <div>
                <dt>Metrics tags</dt>
                <dd class="mono">{{ p.influxProjectTag }} · {{ p.influxEnv }}</dd>
              </div>
              @if (p.description) {
                <div>
                  <dt>Description</dt>
                  <dd>{{ p.description }}</dd>
                </div>
              }
              <div>
                <dt>Changed</dt>
                <dd>{{ p.updatedAt | relative }}</dd>
              </div>
            </dl>
          </section>
        </div>

        <section class="card">
          <header class="card-header">
            <h2>Jenkinsfile</h2>
            <button
              type="button"
              class="btn btn-link"
              [cdkCopyToClipboard]="jenkinsfileText()"
              (cdkCopyToClipboardCopied)="actions.copied()"
            >
              Copy
            </button>
          </header>
          <pre class="code-block">{{ jenkinsfileText() }}</pre>
        </section>

        <section class="card">
          <header class="card-header">
            <h2>Recent runs</h2>
            <a class="text-link" [routerLink]="['/monitoring/pipelines', p.id]"
              >DORA metrics and every run</a
            >
          </header>
          @if (monitoring.hasValue()) {
            <dso-metrics-banner [metricsError]="monitoring.value().metricsError" />
          }
          @if (runs().length === 0) {
            <div class="empty-state small-empty">
              <p>No run reported in the last 30 days.</p>
            </div>
          } @else {
            <dso-grid label="Recent runs" [rows]="runs()" [columns]="runColumns" [rowId]="runId">
              <ng-template dsoCell="time" let-run>
                {{ run.time | date: 'd MMM, HH:mm' }}
              </ng-template>
              <ng-template dsoCell="result" let-run>
                <dso-status-chip [status]="run.result" />
              </ng-template>
              <ng-template dsoCell="build" let-run><dso-build-link [run]="run" /></ng-template>
            </dso-grid>
          }
        </section>
      }
    </div>
  `,
  styles: `
    .title {
      gap: 6px 10px;
    }

    .columns {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
      gap: 12px;
      margin-bottom: 12px;
    }

    .card {
      margin-bottom: 12px;
      padding: 10px 14px;
    }

    .columns .card {
      margin-bottom: 0;
    }

    .card-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
    }

    .key {
      gap: 4px 12px;
      margin: 8px 0;
    }

    .key-value {
      font-size: 13px;
      overflow-wrap: anywhere;
    }

    .banner-text {
      flex: 1;
    }

    .pairs dd {
      overflow-wrap: anywhere;
    }
  `,
})
export class PipelinePage {
  readonly id = input.required<string>();

  private readonly api = inject(PipelinesApi);
  private readonly monitoringApi = inject(MonitoringApi);
  private readonly router = inject(Router);
  protected readonly actions = inject(PipelineActions);

  protected readonly section = PIPELINES;
  protected readonly productLink = adminProduct;
  protected readonly typeLabel = pipelineTypeLabel;
  protected readonly errorMessage = errorMessage;
  protected readonly menuBefore = MENU_BEFORE;
  protected readonly runId = (run: PipelineRun) => `${run.job} ${run.build} ${run.time}`;
  protected readonly runColumns: GridColumn<PipelineRun>[] = [
    { key: 'time', header: 'Finished', value: (run) => run.time },
    { key: 'result', header: 'Result', value: (run) => RUN_LOOK[run.result].label },
    { key: 'build', header: 'Build', value: (run) => run.build ?? '' },
    { key: 'branch', header: 'Branch', value: (run) => run.branch ?? '–', cellClass: 'mono' },
    {
      key: 'duration',
      header: 'Duration',
      value: (run) => formatDuration(run.durationSeconds),
      sortValue: (run) => run.durationSeconds ?? 0,
    },
  ];

  private readonly pipelineId = computed(() => Number(this.id()));
  protected readonly pipeline = rxResource({
    params: () => this.pipelineId(),
    stream: ({ params }) => this.api.get(params),
  });
  protected readonly monitoring = rxResource({
    params: () => this.pipelineId(),
    stream: ({ params }) => this.monitoringApi.pipeline(params, '30d'),
  });
  private readonly library = toSignal(
    inject(SettingsApi)
      .get()
      .pipe(
        map((settings) => settings.platform.jenkinsLibrary),
        catchError(() => of(null)),
      ),
    { initialValue: null },
  );

  protected readonly health = computed(() =>
    this.monitoring.hasValue() ? this.monitoring.value() : null,
  );
  protected readonly runs = computed(() => this.health()?.recentRuns.slice(0, RECENT_RUNS) ?? []);
  protected readonly jenkinsfileText = computed(() =>
    this.pipeline.hasValue() ? jenkinsfile([this.pipeline.value()], this.library()) : '',
  );
  protected readonly revealed = signal(false);
  protected readonly regenerating = signal(false);

  protected edit(pipeline: Pipeline): void {
    this.actions.edit(pipeline).subscribe((updated) => this.pipeline.set(updated));
  }

  protected keyHistory(pipeline: Pipeline): void {
    this.actions.showKeyHistory(pipeline).subscribe((updated) => this.keyIssued(updated));
  }

  protected replaceKey(pipeline: Pipeline): void {
    this.actions.replaceKey(pipeline).subscribe((updated) => this.keyIssued(updated));
  }

  protected revokeKey(pipeline: Pipeline): void {
    this.actions.revokeKey(pipeline).subscribe((updated) => this.pipeline.set(updated));
  }

  protected regenerateKey(pipeline: Pipeline): void {
    this.regenerating.set(true);
    this.actions
      .regenerateKey(pipeline)
      .pipe(finalize(() => this.regenerating.set(false)))
      .subscribe((updated) => this.keyIssued(updated));
  }

  protected deletePipeline(pipeline: Pipeline): void {
    this.actions.deletePipeline(pipeline).subscribe(() => this.router.navigate([PIPELINES.path]));
  }

  private keyIssued(pipeline: Pipeline): void {
    this.pipeline.set(pipeline);
    this.revealed.set(true);
  }
}
