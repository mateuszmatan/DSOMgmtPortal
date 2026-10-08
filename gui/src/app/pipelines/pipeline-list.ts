import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { Router, RouterLink } from '@angular/router';
import { map } from 'rxjs';
import { MyDepartment } from '../beadle/my-department';
import { DepartmentsApi, PipelinesApi } from '../core/api';
import { errorMessage } from '../core/errors';
import {
  PIPELINE_TYPES,
  Pipeline,
  PipelineHealth,
  PipelineType,
  RunResult,
  pipelineTypeLabel,
} from '../core/models';
import { PIPELINES, SELF_SERVICE, pipelinePage } from '../core/sections';
import { MetricsBanner } from '../monitoring/metrics-banner';
import { text } from '../shared/form-controls';
import { RelativeTimePipe, counted } from '../shared/formatting';
import { RUN_LOOK, StatusChip } from '../shared/status-chip';
import { PipelineActions } from './pipeline-actions';

export type KeyFilter = 'ALL' | 'ACTIVE' | 'INVALIDATED';

export interface PipelineFilters {
  service: string;
  product: string;
  type: PipelineType | 'ALL';
  job: string;
  key: KeyFilter;
  status: RunResult | 'ALL';
}

export const KEY_FILTERS: { value: KeyFilter; label: string }[] = [
  { value: 'ALL', label: 'All' },
  { value: 'ACTIVE', label: 'Active' },
  { value: 'INVALIDATED', label: 'Invalidated' },
];

export const TYPE_FILTERS: { value: PipelineType | 'ALL'; label: string }[] = [
  { value: 'ALL', label: 'All' },
  ...PIPELINE_TYPES.map(({ value, label }) => ({ value, label })),
];

export const STATUS_FILTERS: { value: RunResult | 'ALL'; label: string }[] = [
  { value: 'ALL', label: 'All' },
  ...(Object.keys(RUN_LOOK) as RunResult[]).map((value) => ({
    value,
    label: RUN_LOOK[value].label,
  })),
];

const contains = (value: string | null, typed: string) =>
  (value ?? '').toLowerCase().includes(typed.trim().toLowerCase());

const SORT_KEYS: Record<string, (row: PipelineHealth) => string | number> = {
  service: (row) => row.pipeline.serviceName,
  product: (row) => row.pipeline.productName,
  type: (row) => PIPELINE_TYPES.findIndex((type) => type.value === row.pipeline.type),
  job: (row) => row.pipeline.jenkinsJob ?? '',
  key: (row) => (row.pipeline.activeKey ? 0 : 1),
  status: (row) => Object.keys(RUN_LOOK).indexOf(row.status),
  lastRun: (row) => Date.parse(row.lastRun?.time ?? '') || 0,
};

export function matches(row: PipelineHealth, filters: PipelineFilters): boolean {
  const { pipeline } = row;
  const active = pipeline.activeKey !== null;
  return (
    contains(pipeline.serviceName, filters.service) &&
    (contains(pipeline.productName, filters.product) ||
      contains(pipeline.productCode, filters.product)) &&
    (filters.type === 'ALL' || pipeline.type === filters.type) &&
    contains(pipeline.jenkinsJob, filters.job) &&
    (filters.key === 'ALL' || (filters.key === 'ACTIVE') === active) &&
    (filters.status === 'ALL' || row.status === filters.status)
  );
}

export function sorted(rows: readonly PipelineHealth[], sort: Sort): PipelineHealth[] {
  const key = SORT_KEYS[sort.active];
  if (!key || !sort.direction) {
    return [...rows];
  }
  const sign = sort.direction === 'asc' ? 1 : -1;
  return [...rows].sort((a, b) => {
    const [x, y] = [key(a), key(b)];
    return (
      sign *
      (typeof x === 'number' && typeof y === 'number'
        ? x - y
        : String(x).localeCompare(String(y), 'en', { numeric: true }))
    );
  });
}

@Component({
  selector: 'dso-pipeline-list',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
    MatSortModule,
    MatTableModule,
    MetricsBanner,
    RelativeTimePipe,
    StatusChip,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <header class="page-header">
        <div>
          <h1>{{ section.heading }}</h1>
          <p class="page-description">{{ section.description }}</p>
        </div>
        <div class="actions">
          <a mat-flat-button [routerLink]="selfService.path">New pipeline</a>
        </div>
      </header>
      <section class="card toolbar">
        <mat-form-field class="department" subscriptSizing="dynamic">
          <mat-label>Your department</mat-label>
          <mat-select [formControl]="department">
            @for (department of departmentList(); track department.id) {
              <mat-option [value]="department.id">{{ department.name }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <span class="spacer"></span>
        @if (all().length) {
          <span class="muted shown">{{ shown() }}</span>
        }
      </section>
      @if (departments.error(); as error) {
        <div class="banner">The departments could not be loaded: {{ errorMessage(error) }}</div>
      }
      @if (departmentId() === null) {
        <section class="card empty-state">
          <h3>Choose your department to see its pipelines.</h3>
          <p>The portal remembers your department in this browser.</p>
        </section>
      } @else {
        @if (pipelines.isLoading()) {
          <mat-progress-bar mode="indeterminate" />
        }
        @if (pipelines.error(); as error) {
          <div class="banner">{{ errorMessage(error) }}</div>
        }
        @if (pipelines.hasValue()) {
          <dso-metrics-banner [metricsError]="pipelines.value().metricsError" />
          @if (all().length) {
            <section class="card table-scroll">
              <table
                mat-table
                class="pipelines"
                [dataSource]="rows()"
                matSort
                [matSortActive]="sort().active"
                [matSortDirection]="sort().direction"
                (matSortChange)="sort.set($event)"
              >
                <ng-container matColumnDef="service">
                  <th mat-header-cell *matHeaderCellDef mat-sort-header>Service</th>
                  <td mat-cell *matCellDef="let row">
                    <a
                      class="mono quiet-link"
                      [routerLink]="pipelineLink(row.pipeline.id)"
                      (click)="$event.stopPropagation()"
                      >{{ row.pipeline.serviceName }}</a
                    >
                  </td>
                </ng-container>
                <ng-container matColumnDef="product">
                  <th mat-header-cell *matHeaderCellDef mat-sort-header>Product</th>
                  <td mat-cell *matCellDef="let row">
                    {{ row.pipeline.productName }}
                    <span class="muted mono">{{ row.pipeline.productCode }}</span>
                  </td>
                </ng-container>
                <ng-container matColumnDef="type">
                  <th mat-header-cell *matHeaderCellDef mat-sort-header>Type</th>
                  <td mat-cell *matCellDef="let row">{{ typeLabel(row.pipeline.type) }}</td>
                </ng-container>
                <ng-container matColumnDef="job">
                  <th mat-header-cell *matHeaderCellDef mat-sort-header>Jenkins job</th>
                  <td mat-cell *matCellDef="let row" class="mono">
                    {{ row.pipeline.jenkinsJob ?? 'Not set' }}
                  </td>
                </ng-container>
                <ng-container matColumnDef="key">
                  <th mat-header-cell *matHeaderCellDef mat-sort-header>Key</th>
                  <td mat-cell *matCellDef="let row">
                    @if (row.pipeline.activeKey; as key) {
                      <span class="mono">{{ key.hint }}</span>
                    } @else {
                      <span class="chip danger">Invalidated</span>
                    }
                  </td>
                </ng-container>
                <ng-container matColumnDef="status">
                  <th mat-header-cell *matHeaderCellDef mat-sort-header>Last run</th>
                  <td mat-cell *matCellDef="let row">
                    <dso-status-chip [status]="row.status" />
                  </td>
                </ng-container>
                <ng-container matColumnDef="lastRun">
                  <th mat-header-cell *matHeaderCellDef mat-sort-header>Ran</th>
                  <td mat-cell *matCellDef="let row">
                    {{ row.lastRun ? (row.lastRun.time | relative) : '' }}
                  </td>
                </ng-container>
                <ng-container matColumnDef="actions">
                  <th mat-header-cell *matHeaderCellDef></th>
                  <td mat-cell *matCellDef="let row" class="actions">
                    <button
                      mat-button
                      type="button"
                      [attr.aria-label]="'Edit ' + label(row.pipeline)"
                      (click)="edit(row.pipeline); $event.stopPropagation()"
                    >
                      Edit
                    </button>
                  </td>
                </ng-container>
                @for (filter of textFilters; track filter.key) {
                  <ng-container [matColumnDef]="filter.key + '-filter'">
                    <th mat-header-cell *matHeaderCellDef>
                      <mat-form-field subscriptSizing="dynamic">
                        <input
                          matInput
                          autocomplete="off"
                          placeholder="Filter"
                          [attr.aria-label]="'Filter by ' + filter.label"
                          [formControl]="filters.controls[filter.key]"
                        />
                      </mat-form-field>
                    </th>
                  </ng-container>
                }
                @for (filter of selectFilters; track filter.key) {
                  <ng-container [matColumnDef]="filter.key + '-filter'">
                    <th mat-header-cell *matHeaderCellDef>
                      <mat-form-field subscriptSizing="dynamic">
                        <mat-select
                          [aria-label]="'Filter by ' + filter.label"
                          [formControl]="filters.controls[filter.key]"
                        >
                          @for (option of filter.options; track option.value) {
                            <mat-option [value]="option.value">{{ option.label }}</mat-option>
                          }
                        </mat-select>
                      </mat-form-field>
                    </th>
                  </ng-container>
                }
                <ng-container matColumnDef="rest-filter">
                  <th mat-header-cell *matHeaderCellDef colspan="2"></th>
                </ng-container>
                <tr mat-header-row *matHeaderRowDef="columns"></tr>
                <tr mat-header-row *matHeaderRowDef="filterColumns" class="filters"></tr>
                <tr
                  mat-row
                  *matRowDef="let row; columns: columns"
                  class="clickable"
                  [class.revoked]="!row.pipeline.activeKey"
                  (click)="open(row.pipeline)"
                ></tr>
                <tr class="mat-mdc-row" *matNoDataRow>
                  <td class="mat-mdc-cell no-match" [attr.colspan]="columns.length">
                    No pipeline matches the filters.
                  </td>
                </tr>
              </table>
            </section>
          } @else {
            <div class="card empty-state">
              <h3>No DevSecOps pipeline in {{ departmentName() }} yet</h3>
              <p>
                Choose the pipeline, the product and its services. The portal gives each service its
                pipeline, its key and its Jenkinsfile.
              </p>
              <a mat-flat-button [routerLink]="selfService.path">New pipeline</a>
            </div>
          }
        }
      }
    </div>
  `,
  styles: `
    .toolbar {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 8px 16px;
      margin-bottom: 12px;
      padding: 10px 12px;
    }

    .department {
      width: 280px;
      max-width: 100%;
    }

    .shown {
      font-size: 12px;
    }

    .pipelines {
      width: 100%;

      td.mat-mdc-cell {
        padding-top: 4px;
        padding-bottom: 4px;
        font-size: 12.5px;
      }
    }

    .filters .mat-mdc-header-cell {
      padding-top: 2px;
      padding-bottom: 6px;
      font-weight: 400;
      letter-spacing: 0;
      text-transform: none;
      vertical-align: top;

      mat-form-field {
        width: 100%;
        min-width: 72px;
      }
    }

    .mat-column-service,
    .mat-column-type,
    .mat-column-key,
    .mat-column-lastRun {
      white-space: nowrap;
    }

    .mat-column-job {
      min-width: 200px;
      overflow-wrap: anywhere;
    }

    .revoked td {
      color: var(--dso-muted);
    }

    td.actions {
      width: 60px;
      text-align: right;
    }

    .no-match {
      padding: 12px;
      color: var(--dso-muted);
    }
  `,
})
export class PipelineList {
  private readonly api = inject(PipelinesApi);
  private readonly departmentsApi = inject(DepartmentsApi);
  private readonly actions = inject(PipelineActions);
  private readonly router = inject(Router);
  private readonly myDepartment = inject(MyDepartment);

  protected readonly section = PIPELINES;
  protected readonly selfService = SELF_SERVICE;
  protected readonly pipelineLink = pipelinePage;
  protected readonly typeLabel = pipelineTypeLabel;
  protected readonly errorMessage = errorMessage;
  protected readonly textFilters: {
    key: 'service' | 'product' | 'job';
    label: string;
  }[] = [
    { key: 'service', label: 'service' },
    { key: 'product', label: 'product' },
    { key: 'job', label: 'Jenkins job' },
  ];
  protected readonly selectFilters: {
    key: 'type' | 'key' | 'status';
    label: string;
    options: { value: string; label: string }[];
  }[] = [
    { key: 'type', label: 'type', options: TYPE_FILTERS },
    { key: 'key', label: 'key', options: KEY_FILTERS },
    { key: 'status', label: 'last run', options: STATUS_FILTERS },
  ];
  protected readonly columns = [
    'service',
    'product',
    'type',
    'job',
    'key',
    'status',
    'lastRun',
    'actions',
  ];
  protected readonly filterColumns = [
    'service-filter',
    'product-filter',
    'type-filter',
    'job-filter',
    'key-filter',
    'status-filter',
    'rest-filter',
  ];

  protected readonly departmentId = this.myDepartment.departmentId;
  protected readonly department = new FormControl(this.departmentId());
  protected readonly departments = rxResource({ stream: () => this.departmentsApi.list() });
  protected readonly departmentList = computed(() =>
    this.departments.hasValue() ? this.departments.value() : [],
  );
  protected readonly departmentName = computed(
    () =>
      this.departmentList().find((department) => department.id === this.departmentId())?.name ??
      'your department',
  );

  protected readonly pipelines = rxResource({
    params: () => this.departmentId() ?? undefined,
    stream: ({ params }) => this.api.listForDepartment(params),
  });

  protected readonly filters = new FormGroup({
    service: text(''),
    product: text(''),
    type: new FormControl<PipelineType | 'ALL'>('ALL', { nonNullable: true }),
    job: text(''),
    key: new FormControl<KeyFilter>('ALL', { nonNullable: true }),
    status: new FormControl<RunResult | 'ALL'>('ALL', { nonNullable: true }),
  });
  private readonly filterValue = toSignal(
    this.filters.valueChanges.pipe(map(() => this.filters.getRawValue())),
    { initialValue: this.filters.getRawValue() },
  );
  protected readonly sort = signal<Sort>({ active: '', direction: '' });

  protected readonly all = computed(() =>
    this.pipelines.hasValue() ? this.pipelines.value().pipelines : [],
  );
  protected readonly rows = computed(() =>
    sorted(
      this.all().filter((row) => matches(row, this.filterValue())),
      this.sort(),
    ),
  );
  protected readonly shown = computed(
    () => `${this.rows().length} of ${counted(this.all().length, 'pipeline')}`,
  );

  constructor() {
    this.department.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((id) => this.myDepartment.choose(id));
  }

  protected label(pipeline: Pipeline): string {
    return `the ${pipelineTypeLabel(pipeline.type)} pipeline of ${pipeline.serviceName}`;
  }

  protected open(pipeline: Pipeline): void {
    this.router.navigate(pipelinePage(pipeline.id));
  }

  protected edit(pipeline: Pipeline): void {
    this.actions.edit(pipeline).subscribe((updated) =>
      this.pipelines.update(
        (listed) =>
          listed && {
            ...listed,
            pipelines: listed.pipelines.map((row) =>
              row.pipeline.id === updated.id
                ? { ...row, pipeline: { ...updated, activeKey: row.pipeline.activeKey } }
                : row,
            ),
          },
      ),
    );
  }
}
