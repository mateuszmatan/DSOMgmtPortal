import { ClipboardModule } from '@angular/cdk/clipboard';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Router, RouterLink } from '@angular/router';
import { Observable, catchError, filter, finalize, of, switchMap, tap } from 'rxjs';
import { DepartmentsApi, PipelinesApi, ProductsApi, SettingsApi } from '../core/api';
import { errorMessage } from '../core/errors';
import {
  PIPELINE_TYPES,
  Pipeline,
  PipelineType,
  Product,
  ServicePipelines,
  pipelineTypeLabel,
} from '../core/models';
import { Notifier } from '../core/notifier';
import { bitbucketRepositoryUrl } from '../shared/bitbucket';
import { CodeDialog, CodeDialogData } from '../shared/code-dialog';
import { ConfirmDialog, ConfirmDialogData } from '../shared/confirm-dialog';
import { TARGET_LABELS, TOOL_LABELS } from '../shared/fields';
import { RelativeTimePipe, counted } from '../shared/formatting';
import { GeneratedKeys } from './generated-keys';
import { jenkinsfile } from './jenkinsfile';
import { KeyHistoryDialog } from './key-history-dialog';
import { PipelineDialog, PipelineDialogData } from './pipeline-dialog';
import { RevokeKeyDialog } from './revoke-key-dialog';

@Component({
  selector: 'dso-product-detail',
  imports: [
    ClipboardModule,
    RouterLink,
    MatButtonModule,
    MatDividerModule,
    MatMenuModule,
    MatProgressBarModule,
    MatTooltipModule,
    RelativeTimePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './product-detail.html',
  styleUrl: './product-detail.scss',
})
export class ProductDetail {
  readonly id = input.required<string>();

  private readonly products = inject(ProductsApi);
  private readonly pipelines = inject(PipelinesApi);
  private readonly settings = inject(SettingsApi);
  private readonly dialog = inject(MatDialog);
  private readonly notifier = inject(Notifier);
  private readonly router = inject(Router);
  private readonly generatedKeys = inject(GeneratedKeys);

  private readonly productId = computed(() => Number(this.id()));
  protected readonly product = rxResource({
    params: () => this.productId(),
    stream: ({ params }) => this.products.get(params),
  });
  private readonly departments = toSignal(
    inject(DepartmentsApi)
      .list()
      .pipe(catchError(() => of([]))),
    { initialValue: [] },
  );
  protected readonly departmentName = computed(() => {
    const id = this.product.hasValue() ? this.product.value().departmentId : null;
    return this.departments().find((department) => department.id === id)?.name;
  });
  protected readonly services = rxResource({
    params: () => this.productId(),
    stream: ({ params }) => {
      const newServices = this.generatedKeys.take(params);
      return this.pipelines
        .listForProduct(params)
        .pipe(tap((services) => this.showGeneratedKeys(services, newServices)));
    },
  });

  protected readonly stats = computed(() => {
    if (!this.services.hasValue()) {
      return null;
    }
    const pipelines = this.services.value().flatMap((service) => service.pipelines);
    const active = pipelines.filter((pipeline) => pipeline.activeKey !== null).length;
    const invalidated = pipelines.length - active;
    return [
      { label: 'Services', value: this.services.value().length, tone: '' },
      { label: 'Pipelines', value: pipelines.length, tone: '' },
      { label: 'Active keys', value: active, tone: 'success' },
      { label: 'Invalidated keys', value: invalidated, tone: invalidated ? 'danger' : '' },
    ];
  });

  private readonly repositories = computed(
    () =>
      new Map(
        (this.product.hasValue() ? this.product.value().services : []).map((service) => [
          service.id,
          bitbucketRepositoryUrl(service.scm),
        ]),
      ),
  );

  protected readonly revealed = signal<ReadonlySet<number>>(new Set());
  protected readonly regenerating = signal<ReadonlySet<number>>(new Set());
  protected readonly generated = signal<readonly string[]>([]);
  protected readonly generatedNotice = computed(() => {
    const names = this.generated();
    return names.length === 1
      ? `Pipeline key generated for the new service ${names[0]}.`
      : `Pipeline keys generated for ${names.length} new services: ${names.join(', ')}.`;
  });
  protected readonly errorMessage = errorMessage;

  protected readonly typeLabel = pipelineTypeLabel;
  protected readonly toolLabels = TOOL_LABELS;
  protected readonly targetLabels = TARGET_LABELS;

  protected typeName(type: PipelineType): string {
    const label = this.typeLabel(type);
    return /^[A-Z]{2}/.test(label) ? label : label.charAt(0).toLowerCase() + label.slice(1);
  }

  protected repositoryOf(service: ServicePipelines): string | null {
    return this.repositories().get(service.serviceId) ?? null;
  }

  protected canAddPipeline(service: ServicePipelines): boolean {
    return service.pipelines.length < PIPELINE_TYPES.length;
  }

  protected toggleKey(pipeline: Pipeline): void {
    this.revealed.update((ids) => withId(ids, pipeline.id, !ids.has(pipeline.id)));
  }

  protected dismissGenerated(): void {
    this.generated.set([]);
  }

  protected copied(): void {
    this.notifier.success('Key copied to the clipboard');
  }

  protected showProductConfig(product: Product): void {
    this.showCode(this.products.config(product.id), (code) => ({
      title: `config.yaml of ${product.name}`,
      subtitle:
        'Every service of the product in the format of the DevSecOps library, with the BBH defaults filled in.',
      code,
      fileName: `${product.code.toLowerCase()}-config.yaml`,
    }));
  }

  protected showPipelineConfig(pipeline: Pipeline): void {
    this.showCode(this.pipelines.config(pipeline.id), (code) => ({
      title: `Configuration of the ${pipeline.serviceName} ${pipeline.type.toLowerCase()} pipeline`,
      subtitle:
        "What the DevSecOps library receives for this pipeline's key. Showing it here does not count as a use of the key.",
      code,
      fileName: `${pipeline.productCode.toLowerCase()}-${pipeline.serviceName}-${pipeline.type.toLowerCase()}.yaml`,
    }));
  }

  protected sameTypeElsewhere(pipeline: Pipeline): Pipeline[] {
    return (this.services.value() ?? [])
      .flatMap((service) => service.pipelines)
      .filter(
        (other) =>
          other.type === pipeline.type &&
          other.serviceId !== pipeline.serviceId &&
          other.activeKey &&
          (pipeline.type !== 'EXTENDED' ||
            other.securityPipelineJob === pipeline.securityPipelineJob),
      );
  }

  protected showJenkinsfile(pipeline: Pipeline, together = false): void {
    const pipelines = together ? [pipeline, ...this.sameTypeElsewhere(pipeline)] : [pipeline];
    const subtitle = together
      ? `One run builds ${pipelines.map((p) => p.serviceName).join(', ')}; the first key is the ` +
        'primary service. Everything else comes from the portal by the keys.'
      : 'Once the DevSecOps library reads its configuration from the portal, this is the whole Jenkinsfile of the ' +
        'service: everything else comes from the portal by the key.';
    this.settings
      .get()
      .pipe(catchError(() => of(null)))
      .subscribe((settings) =>
        this.openCode({
          title: together ? 'Jenkinsfile for several services' : 'Jenkinsfile',
          subtitle,
          code: jenkinsfile(pipelines, settings?.platform.jenkinsLibrary),
          fileName: 'Jenkinsfile',
        }),
      );
  }

  protected addPipeline(service: ServicePipelines): void {
    this.openPipelineDialog(
      { service },
      (pipeline) => `${this.typeLabel(pipeline.type)} pipeline added to ${pipeline.serviceName}`,
    );
  }

  protected editPipeline(service: ServicePipelines, pipeline: Pipeline): void {
    this.openPipelineDialog({ service, pipeline }, () => 'Pipeline settings saved');
  }

  protected revokeKey(pipeline: Pipeline): void {
    this.dialog
      .open<RevokeKeyDialog, Pipeline, Pipeline>(RevokeKeyDialog, { data: pipeline })
      .afterClosed()
      .pipe(filter((updated): updated is Pipeline => !!updated))
      .subscribe((updated) => {
        this.replacePipeline(updated);
        this.notifier.success('Key invalidated: the pipeline stops at its next start');
      });
  }

  protected replaceKey(pipeline: Pipeline): void {
    this.confirm({
      title: 'Replace the key?',
      message:
        'The current key is invalidated and a new one is issued. Update the Jenkinsfile with the new key, ' +
        'or the pipeline stops at its next start.',
      confirmLabel: 'Replace key',
      danger: true,
    })
      .pipe(switchMap(() => this.pipelines.issueKey(pipeline.id)))
      .subscribe({
        next: (updated) => {
          this.keyIssued(updated);
          this.notifier.success('New key issued');
        },
        error: (error) => this.notifier.error(error),
      });
  }

  protected regenerateKey(pipeline: Pipeline): void {
    this.regenerating.update((ids) => withId(ids, pipeline.id));
    this.pipelines
      .issueKey(pipeline.id)
      .pipe(finalize(() => this.regenerating.update((ids) => withId(ids, pipeline.id, false))))
      .subscribe({
        next: (updated) => {
          this.keyIssued(updated);
          this.notifier.success(
            `${this.typeLabel(updated.type)} pipeline of ${updated.serviceName} has a new key: ` +
              'pass it in the Jenkinsfile',
          );
        },
        error: (error) => this.notifier.error(error),
      });
  }

  protected showKeyHistory(pipeline: Pipeline): void {
    this.dialog
      .open(KeyHistoryDialog, { data: pipeline, maxWidth: '95vw' })
      .componentInstance.keyIssued.subscribe((updated) => this.keyIssued(updated));
  }

  protected deletePipeline(pipeline: Pipeline): void {
    this.confirm({
      title: 'Delete the pipeline?',
      message:
        `The ${pipeline.type.toLowerCase()} pipeline of ${pipeline.serviceName} and its key history are deleted. ` +
        'Jenkins jobs using its key stop working.',
      confirmLabel: 'Delete pipeline',
      danger: true,
    })
      .pipe(switchMap(() => this.pipelines.delete(pipeline.id)))
      .subscribe({
        next: () => {
          this.services.update((services) =>
            services?.map((service) => ({
              ...service,
              pipelines: service.pipelines.filter((p) => p.id !== pipeline.id),
            })),
          );
          this.notifier.success('Pipeline deleted');
        },
        error: (error) => this.notifier.error(error),
      });
  }

  protected deleteProduct(product: Product): void {
    const pipelines = this.services.hasValue()
      ? this.services.value().flatMap((service) => service.pipelines).length
      : 0;
    this.confirm({
      title: `Delete ${product.name}?`,
      message:
        `The product, its ${counted(product.services.length, 'service')} and ${counted(pipelines, 'pipeline')} with their keys are deleted. ` +
        'Jenkins jobs using those keys stop working. This cannot be undone.',
      confirmLabel: 'Delete product',
      danger: true,
    })
      .pipe(switchMap(() => this.products.delete(product.id)))
      .subscribe({
        next: () => {
          this.notifier.success(`${product.name} deleted`);
          this.router.navigate(['/products']);
        },
        error: (error) => this.notifier.error(error),
      });
  }

  private openPipelineDialog(
    data: PipelineDialogData,
    message: (pipeline: Pipeline) => string,
  ): void {
    this.dialog
      .open<PipelineDialog, PipelineDialogData, Pipeline>(PipelineDialog, { data })
      .afterClosed()
      .pipe(filter((pipeline): pipeline is Pipeline => !!pipeline))
      .subscribe((pipeline) => {
        this.replacePipeline(pipeline);
        this.notifier.success(message(pipeline));
      });
  }

  private keyIssued(pipeline: Pipeline): void {
    this.replacePipeline(pipeline);
    this.revealed.update((ids) => withId(ids, pipeline.id));
  }

  private showGeneratedKeys(services: ServicePipelines[], newServices: readonly string[]): void {
    const names = new Set(newServices);
    const generated = services.filter(
      (service) =>
        names.has(service.serviceName) &&
        service.pipelines.some((pipeline) => pipeline.activeKey !== null),
    );
    this.generated.set(generated.map((service) => service.serviceName));
    this.revealed.set(
      new Set([
        ...this.revealed(),
        ...generated.flatMap((service) =>
          service.pipelines
            .filter((pipeline) => pipeline.activeKey?.value)
            .map((pipeline) => pipeline.id),
        ),
      ]),
    );
  }

  private replacePipeline(pipeline: Pipeline): void {
    this.services.update((services) =>
      services?.map((service) => {
        if (service.serviceId !== pipeline.serviceId) {
          return service;
        }
        const exists = service.pipelines.some((p) => p.id === pipeline.id);
        const pipelines = exists
          ? service.pipelines.map((p) => (p.id === pipeline.id ? pipeline : p))
          : [...service.pipelines, pipeline].sort(
              (a, b) =>
                PIPELINE_TYPES.findIndex((t) => t.value === a.type) -
                PIPELINE_TYPES.findIndex((t) => t.value === b.type),
            );
        return { ...service, pipelines };
      }),
    );
  }

  private showCode(source: Observable<string>, data: (code: string) => CodeDialogData): void {
    source.subscribe({
      next: (code) => this.openCode(data(code)),
      error: (error) => this.notifier.error(error),
    });
  }

  private openCode(data: CodeDialogData): void {
    this.dialog.open(CodeDialog, { data, width: '760px', maxWidth: '95vw' });
  }

  private confirm(data: ConfirmDialogData) {
    return this.dialog
      .open<ConfirmDialog, ConfirmDialogData, boolean>(ConfirmDialog, { data })
      .afterClosed()
      .pipe(filter((confirmed) => confirmed === true));
  }
}

function withId(ids: ReadonlySet<number>, id: number, member = true): ReadonlySet<number> {
  const next = new Set(ids);
  if (member) {
    next.add(id);
  } else {
    next.delete(id);
  }
  return next;
}
