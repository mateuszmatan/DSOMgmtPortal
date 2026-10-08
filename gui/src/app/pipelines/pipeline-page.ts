import { ClipboardModule } from '@angular/cdk/clipboard';
import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { Router, RouterLink } from '@angular/router';
import { catchError, finalize, map, of } from 'rxjs';
import { MonitoringApi, PipelinesApi, SettingsApi } from '../core/api';
import { errorMessage } from '../core/errors';
import { Pipeline, pipelineTypeLabel } from '../core/models';
import { PIPELINES, adminProduct } from '../core/sections';
import { MetricsBanner } from '../monitoring/metrics-banner';
import { jenkinsfile } from '../products/jenkinsfile';
import { BuildLink } from '../shared/build-link';
import { DurationPipe, RelativeTimePipe } from '../shared/formatting';
import { StatusChip } from '../shared/status-chip';
import { PipelineActions } from './pipeline-actions';

const RECENT_RUNS = 5;

@Component({
  selector: 'dso-pipeline-page',
  imports: [
    ClipboardModule,
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatDividerModule,
    MatMenuModule,
    MatProgressBarModule,
    MatTableModule,
    BuildLink,
    DurationPipe,
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
        <mat-progress-bar mode="indeterminate" />
      }

      @if (pipeline.error(); as error) {
        <div class="banner">{{ errorMessage(error) }}</div>
        <a mat-stroked-button [routerLink]="section.path">Back to pipelines</a>
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
              <a mat-stroked-button [href]="job" target="_blank" rel="noopener">Jenkins</a>
            }
            <a mat-stroked-button [routerLink]="['/monitoring/pipelines', p.id]">Metrics</a>
            <a mat-stroked-button [routerLink]="productLink(p.productId)">Product</a>
            <button mat-stroked-button type="button" [matMenuTriggerFor]="more">More</button>
            <mat-menu #more="matMenu" xPosition="before">
              <button mat-menu-item (click)="actions.showConfig(p)">config.yaml</button>
              <button mat-menu-item (click)="keyHistory(p)">Key history</button>
              <mat-divider />
              @if (p.activeKey) {
                <button mat-menu-item (click)="replaceKey(p)">Replace key</button>
                <button mat-menu-item class="danger" (click)="revokeKey(p)">Invalidate key</button>
              }
              <button mat-menu-item class="danger" (click)="deletePipeline(p)">
                Delete pipeline
              </button>
            </mat-menu>
            <button mat-flat-button type="button" (click)="edit(p)">Edit</button>
          </div>
        </header>

        @if (!p.activeKey) {
          <div class="banner danger" role="status">
            <span class="banner-text"
              >The key is invalidated, so the pipeline is refused its configuration and stops at its
              next start.</span
            >
            <button
              mat-flat-button
              type="button"
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
              mat-button
              type="button"
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
            <div class="table-scroll">
              <table mat-table [dataSource]="runs()">
                <ng-container matColumnDef="time">
                  <th mat-header-cell *matHeaderCellDef>Finished</th>
                  <td mat-cell *matCellDef="let run">{{ run.time | date: 'd MMM, HH:mm' }}</td>
                </ng-container>
                <ng-container matColumnDef="result">
                  <th mat-header-cell *matHeaderCellDef>Result</th>
                  <td mat-cell *matCellDef="let run">
                    <dso-status-chip [status]="run.result" />
                  </td>
                </ng-container>
                <ng-container matColumnDef="build">
                  <th mat-header-cell *matHeaderCellDef>Build</th>
                  <td mat-cell *matCellDef="let run"><dso-build-link [run]="run" /></td>
                </ng-container>
                <ng-container matColumnDef="branch">
                  <th mat-header-cell *matHeaderCellDef>Branch</th>
                  <td mat-cell *matCellDef="let run" class="mono">{{ run.branch ?? '–' }}</td>
                </ng-container>
                <ng-container matColumnDef="duration">
                  <th mat-header-cell *matHeaderCellDef>Duration</th>
                  <td mat-cell *matCellDef="let run">{{ run.durationSeconds | duration }}</td>
                </ng-container>
                <tr mat-header-row *matHeaderRowDef="runColumns"></tr>
                <tr mat-row *matRowDef="let row; columns: runColumns"></tr>
              </table>
            </div>
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
  protected readonly runColumns = ['time', 'result', 'build', 'branch', 'duration'];

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
