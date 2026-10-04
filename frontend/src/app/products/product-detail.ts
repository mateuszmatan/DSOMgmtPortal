import { ClipboardModule } from '@angular/cdk/clipboard';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Router, RouterLink } from '@angular/router';
import { filter, switchMap } from 'rxjs';
import { PipelinesApi, ProductsApi } from '../core/api';
import { errorMessage } from '../core/errors';
import { PIPELINE_TYPES, Pipeline, PipelineType, Product, ServicePipelines } from '../core/models';
import { Notifier } from '../core/notifier';
import { CodeDialog, CodeDialogData } from '../shared/code-dialog';
import { ConfirmDialog, ConfirmDialogData } from '../shared/confirm-dialog';
import { MaskKeyPipe, RelativeTimePipe } from '../shared/formatting';
import { KeyHistoryDialog } from './key-history-dialog';
import { PipelineDialog, PipelineDialogData } from './pipeline-dialog';
import { RevokeKeyDialog } from './revoke-key-dialog';

/** Name of the shared library in the Jenkins configuration, as the DevSecOps Jenkinsfile template loads it. */
const JENKINS_LIBRARY = 'DevSecOpsJenkinsLibrary';

const TYPE_ICONS: Record<PipelineType, string> = {
  FULL: 'all_inclusive',
  SECURITY: 'security',
  EXTENDED: 'rocket_launch',
  SAST: 'policy',
};

/** One product: its services, each with its pipelines and their keys. */
@Component({
  selector: 'dso-product-detail',
  imports: [
    ClipboardModule,
    RouterLink,
    MatButtonModule,
    MatDividerModule,
    MatIconModule,
    MatMenuModule,
    MatProgressBarModule,
    MatTooltipModule,
    MaskKeyPipe,
    RelativeTimePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './product-detail.html',
  styleUrl: './product-detail.scss',
})
export class ProductDetail {
  /** The product id, from the route. */
  readonly id = input.required<string>();

  private readonly products = inject(ProductsApi);
  private readonly pipelines = inject(PipelinesApi);
  private readonly dialog = inject(MatDialog);
  private readonly notifier = inject(Notifier);
  private readonly router = inject(Router);

  private readonly productId = computed(() => Number(this.id()));
  protected readonly product = rxResource({
    params: () => this.productId(),
    stream: ({ params }) => this.products.get(params),
  });
  protected readonly services = rxResource({
    params: () => this.productId(),
    stream: ({ params }) => this.pipelines.listForProduct(params),
  });

  protected readonly stats = computed(() => {
    if (!this.services.hasValue()) {
      return null;
    }
    const pipelines = this.services.value().flatMap((service) => service.pipelines);
    const active = pipelines.filter((pipeline) => pipeline.activeKey !== null).length;
    return {
      services: this.services.value().length,
      pipelines: pipelines.length,
      active,
      invalidated: pipelines.length - active,
    };
  });

  /** Pipelines whose key is shown in full. */
  protected readonly revealed = signal<ReadonlySet<number>>(new Set());
  protected readonly typeIcons = TYPE_ICONS;
  protected readonly errorMessage = errorMessage;

  protected typeLabel(type: PipelineType): string {
    return PIPELINE_TYPES.find((option) => option.value === type)?.label ?? type;
  }

  protected canAddPipeline(service: ServicePipelines): boolean {
    return service.pipelines.length < PIPELINE_TYPES.length;
  }

  protected toggleKey(pipeline: Pipeline): void {
    const revealed = new Set(this.revealed());
    if (!revealed.delete(pipeline.id)) {
      revealed.add(pipeline.id);
    }
    this.revealed.set(revealed);
  }

  protected copied(): void {
    this.notifier.success('Key copied to the clipboard');
  }

  protected showProductConfig(product: Product): void {
    this.products.config(product.id).subscribe({
      next: (code) =>
        this.openCode({
          title: `config.yaml of ${product.name}`,
          subtitle:
            'Every service of the product in the format of the DevSecOps library, with the BBH defaults filled in.',
          code,
          fileName: `${product.code.toLowerCase()}-config.yaml`,
        }),
      error: (error) => this.notifier.error(error),
    });
  }

  protected showPipelineConfig(pipeline: Pipeline): void {
    this.pipelines.config(pipeline.id).subscribe({
      next: (code) =>
        this.openCode({
          title: `Configuration of the ${pipeline.serviceName} ${pipeline.type.toLowerCase()} pipeline`,
          subtitle:
            "What the DevSecOps library receives for this pipeline's key. Showing it here does not count as a use of the key.",
          code,
          fileName: `${pipeline.productCode.toLowerCase()}-${pipeline.serviceName}-${pipeline.type.toLowerCase()}.yaml`,
        }),
      error: (error) => this.notifier.error(error),
    });
  }

  protected showJenkinsfile(pipeline: Pipeline): void {
    const key = pipeline.activeKey?.value ?? '<issue a new key first>';
    this.openCode({
      title: 'Jenkinsfile',
      subtitle:
        'Once the DevSecOps library reads its configuration from the portal, this is the whole Jenkinsfile of the ' +
        'service: everything else comes from the portal by the key.',
      code: `@Library('${JENKINS_LIBRARY}') _\n\n${pipeline.entryPoint}(pipelineKey: '${key}')\n`,
      fileName: 'Jenkinsfile',
    });
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

  protected issueKey(pipeline: Pipeline): void {
    const rotating = pipeline.activeKey !== null;
    this.confirm({
      title: rotating ? 'Replace the key?' : 'Issue a new key?',
      message: rotating
        ? 'The current key is invalidated and a new one is issued. Update the Jenkinsfile with the new key, ' +
          'or the pipeline stops at its next start.'
        : 'The pipeline works again once its Jenkinsfile passes the new key.',
      confirmLabel: rotating ? 'Replace key' : 'Issue key',
      danger: rotating,
    })
      .pipe(switchMap(() => this.pipelines.issueKey(pipeline.id)))
      .subscribe({
        next: (updated) => {
          this.replacePipeline(updated);
          this.revealed.set(new Set([...this.revealed(), updated.id]));
          this.notifier.success('New key issued');
        },
        error: (error) => this.notifier.error(error),
      });
  }

  protected showKeyHistory(pipeline: Pipeline): void {
    this.dialog.open(KeyHistoryDialog, { data: pipeline, maxWidth: '95vw' });
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
    const pipelines = this.stats()?.pipelines ?? 0;
    this.confirm({
      title: `Delete ${product.name}?`,
      message:
        `The product, its ${product.services.length} services and ${pipelines} pipelines with their keys are deleted. ` +
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

  /** Puts a pipeline the API returned in place of its old version, or adds it to its service. */
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
