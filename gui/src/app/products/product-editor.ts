import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  Injector,
  OnInit,
  afterNextRender,
  computed,
  inject,
  input,
  signal,
  viewChildren,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Router, RouterLink } from '@angular/router';
import { Observable, catchError, finalize, forkJoin, of } from 'rxjs';
import { PipelinesApi, ProductsApi, SettingsApi } from '../core/api';
import { errorMessage, fieldProblems } from '../core/errors';
import { BuildTool, DeployTarget, FieldProblem, GlobalSettings, Product } from '../core/models';
import { Notifier } from '../core/notifier';
import { HasUnsavedChanges } from '../core/unsaved-changes';
import { ConfirmDialog, ConfirmDialogData } from '../shared/confirm-dialog';
import { revalidateAll } from '../shared/form-controls';
import { errorText } from '../shared/form-errors';
import {
  ServiceForm,
  applyFieldProblems,
  createProductForm,
  createServiceForm,
  duplicateService,
  firstServiceWithProblem,
  patchProduct,
  toProductRequest,
} from './product-form-model';
import { ServiceFields } from './service-fields';

@Component({
  selector: 'dso-product-editor',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatExpansionModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    ServiceFields,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './product-editor.html',
  styleUrl: './product-editor.scss',
})
export class ProductEditor implements OnInit, HasUnsavedChanges {
  readonly id = input<string>();

  private readonly products = inject(ProductsApi);
  private readonly pipelines = inject(PipelinesApi);
  private readonly settingsApi = inject(SettingsApi);
  private readonly router = inject(Router);
  private readonly notifier = inject(Notifier);
  private readonly dialog = inject(MatDialog);
  private readonly injector = inject(Injector);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly form = createProductForm();
  protected readonly product = signal<Product | null>(null);
  protected readonly settings = signal<GlobalSettings | null>(null);
  protected readonly loading = signal(false);
  protected readonly loadError = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly saveError = signal<string | null>(null);
  protected readonly unmatchedProblems = signal<FieldProblem[]>([]);
  protected readonly submitted = signal(false);
  protected readonly expanded = signal<number | null>(null);

  private readonly formEvent = toSignal(this.form.events);
  protected readonly services = computed<ServiceForm[]>(() => {
    this.formEvent();
    return [...this.form.controls.services.controls];
  });

  private readonly pipelineCounts = signal(new Map<number, number>());
  private readonly serviceFields = viewChildren(ServiceFields);
  private saved = false;

  protected readonly errorText = errorText;
  protected readonly toolLabels: Record<BuildTool, string> = {
    GRADLE: 'Gradle',
    MAVEN: 'Maven',
    FLUTTER: 'Flutter',
  };
  protected readonly targetLabels: Record<DeployTarget, string> = {
    VM: 'Virtual machine',
    OPENSHIFT: 'OpenShift',
  };

  ngOnInit(): void {
    const id = this.id();
    this.loading.set(true);
    if (id === undefined) {
      this.loadSettings()
        .pipe(
          finalize(() => this.loading.set(false)),
          takeUntilDestroyed(this.destroyRef),
        )
        .subscribe((settings) => {
          this.settings.set(settings);
          this.addService();
          this.form.markAsPristine();
        });
      return;
    }
    forkJoin({
      product: this.products.get(Number(id)),
      pipelines: this.pipelines.listForProduct(Number(id)),
      settings: this.loadSettings(),
    })
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: ({ product, pipelines, settings }) => {
          patchProduct(this.form, product);
          this.form.markAsPristine();
          this.product.set(product);
          this.settings.set(settings);
          this.pipelineCounts.set(
            new Map(pipelines.map((service) => [service.serviceId, service.pipelines.length])),
          );
          this.expanded.set(product.services.length === 1 ? 0 : null);
        },
        error: (error) => this.loadError.set(errorMessage(error)),
      });
  }

  hasUnsavedChanges(): boolean {
    return !this.saved && this.form.dirty;
  }

  protected pipelinesOf(service: ServiceForm): number {
    const id = service.controls.id.value;
    return id === null ? 0 : (this.pipelineCounts().get(id) ?? 0);
  }

  protected showsErrors(service: ServiceForm): boolean {
    return service.invalid && (service.touched || this.submitted());
  }

  protected addService(): void {
    this.form.controls.services.push(
      createServiceForm(undefined, this.settings()?.serviceDefaults),
    );
    this.expanded.set(this.form.controls.services.length - 1);
    this.form.markAsDirty();
  }

  protected duplicate(index: number): void {
    this.form.controls.services.insert(
      index + 1,
      duplicateService(this.form.controls.services.at(index)),
    );
    this.expanded.set(index + 1);
    this.form.markAsDirty();
  }

  protected move(index: number, offset: -1 | 1): void {
    const services = this.form.controls.services;
    const target = index + offset;
    const service = services.at(index);
    services.removeAt(index, { emitEvent: false });
    services.insert(target, service);
    if (this.expanded() === index) {
      this.expanded.set(target);
    } else if (this.expanded() === target) {
      this.expanded.set(index);
    }
    this.form.markAsDirty();
  }

  protected remove(index: number): void {
    const service = this.form.controls.services.at(index);
    if (service.controls.id.value === null) {
      this.removeAt(index);
      return;
    }
    const pipelines = this.pipelinesOf(service);
    const data: ConfirmDialogData = {
      title: `Remove ${service.controls.name.value}?`,
      message: pipelines
        ? `Saving the product deletes the service's ${pipelines === 1 ? 'pipeline' : `${pipelines} pipelines`} and keys. ` +
          'Jenkins jobs using those keys stop working.'
        : 'The service is removed when you save the product.',
      confirmLabel: 'Remove',
      danger: true,
    };
    this.dialog
      .open<ConfirmDialog, ConfirmDialogData, boolean>(ConfirmDialog, { data })
      .afterClosed()
      .subscribe((confirmed) => confirmed && this.removeAt(index));
  }

  protected panelToggled(index: number, open: boolean): void {
    if (open) {
      this.expanded.set(index);
    } else if (this.expanded() === index) {
      this.expanded.set(null);
    }
  }

  protected save(): void {
    this.submitted.set(true);
    this.saveError.set(null);
    this.unmatchedProblems.set([]);
    revalidateAll(this.form);
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      this.saveError.set('Some fields need your attention.');
      this.revealProblem(
        this.form.controls.services.controls.findIndex((service) => service.invalid),
      );
      return;
    }
    const stored = this.product();
    const request = toProductRequest(this.form, stored?.version ?? null);
    this.saving.set(true);
    (stored ? this.products.update(stored.id, request) : this.products.create(request))
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (product) => {
          this.saved = true;
          this.notifier.success(
            stored ? `${product.name} saved` : `${product.name} added to DevSecOps`,
          );
          this.router.navigate(['/products', product.id]);
        },
        error: (error) => this.showSaveError(error),
      });
  }

  protected cancel(): void {
    const stored = this.product();
    this.router.navigate(stored ? ['/products', stored.id] : ['/products']);
  }

  private showSaveError(error: unknown): void {
    const problems = fieldProblems(error);
    if (problems.length === 0) {
      this.saveError.set(errorMessage(error));
      return;
    }
    this.unmatchedProblems.set(applyFieldProblems(this.form, problems));
    this.saveError.set('The portal did not accept some values. They are marked below.');
    this.revealProblem(firstServiceWithProblem(problems));
  }

  private revealProblem(serviceIndex: number | null): void {
    const service =
      serviceIndex !== null && serviceIndex >= 0
        ? this.form.controls.services.at(serviceIndex)
        : null;
    if (service) {
      this.expanded.set(serviceIndex);
      this.serviceFields()
        .find((fields) => fields.form() === service)
        ?.revealFirstProblem();
    }
    afterNextRender(
      () => {
        const field = document.querySelector(
          '.product-fields .mat-form-field-invalid, .mat-expanded .mat-form-field-invalid',
        );
        field?.scrollIntoView({ behavior: 'smooth', block: 'center' });
      },
      { injector: this.injector },
    );
  }

  private loadSettings(): Observable<GlobalSettings | null> {
    return this.settingsApi.get().pipe(catchError(() => of(null)));
  }

  private removeAt(index: number): void {
    this.form.controls.services.removeAt(index);
    const expanded = this.expanded();
    if (expanded === index) {
      this.expanded.set(null);
    } else if (expanded !== null && expanded > index) {
      this.expanded.set(expanded - 1);
    }
    this.form.markAsDirty();
  }
}
