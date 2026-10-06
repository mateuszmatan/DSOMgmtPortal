import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { rxResource, takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ClipboardModule } from '@angular/cdk/clipboard';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { RouterLink } from '@angular/router';
import {
  Observable,
  catchError,
  debounceTime,
  distinctUntilChanged,
  filter,
  finalize,
  map,
  of,
  switchMap,
  tap,
} from 'rxjs';
import { PipelinesApi, ProductsApi, SettingsApi } from '../core/api';
import { errorMessage, fieldProblems } from '../core/errors';
import { Product } from '../core/models';
import { Notifier } from '../core/notifier';
import { ONBOARDING } from '../core/sections';
import { HasUnsavedChanges } from '../core/unsaved-changes';
import { jenkinsfile } from '../products/jenkinsfile';
import { errorText } from '../shared/form-errors';
import { ChoiceTiles } from './choice-tiles';
import {
  NewProduct,
  OnboardingPipeline,
  OnboardingService,
  PIPELINES,
  PRODUCT_MODES,
  ProductMode,
  ServiceStart,
  TARGETS,
  TOOLS,
  choiceLabel,
  deploysWith,
  fromService,
  jobName,
  pipelineLabel,
  preparation,
  problemText,
  productRequest,
  servicesToStart,
} from './onboarding-model';
import { OnboardingServiceDialog, ServiceDialogData } from './onboarding-service-dialog';

interface Onboarded {
  product: Product;
  starts: ServiceStart[];
}

export const STEPS = ['Pipeline', 'Product', 'Services', 'Review', 'Next steps'];

@Component({
  selector: 'dso-onboarding',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    ClipboardModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
    ChoiceTiles,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './onboarding.html',
  styleUrl: './onboarding.scss',
})
export class Onboarding implements HasUnsavedChanges {
  private readonly productsApi = inject(ProductsApi);
  private readonly pipelinesApi = inject(PipelinesApi);
  private readonly dialog = inject(MatDialog);
  private readonly notifier = inject(Notifier);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly section = ONBOARDING;
  protected readonly steps = STEPS;
  protected readonly pipelines = PIPELINES;
  protected readonly modes = PRODUCT_MODES;
  protected readonly errorText = errorText;
  protected readonly preparation = preparation;
  protected readonly pipelineLabel = pipelineLabel;
  protected readonly jobName = jobName;

  protected readonly step = signal(0);
  protected readonly checked = signal(false);
  protected readonly changed = signal(false);
  protected readonly pipeline = signal<OnboardingPipeline | null>(null);
  protected readonly mode = signal<ProductMode>('new');
  protected readonly services = signal<OnboardingService[]>([]);
  protected readonly existing = signal<Product | null>(null);
  protected readonly productError = signal<string | null>(null);
  protected readonly code = signal('');
  protected readonly saving = signal(false);
  protected readonly saveError = signal<string | null>(null);
  protected readonly saveProblems = signal<string[]>([]);
  protected readonly result = signal<Onboarded | null>(null);

  protected readonly catalogue = rxResource({ stream: () => this.productsApi.list() });
  protected readonly products = computed(() =>
    this.catalogue.hasValue() ? this.catalogue.value() : [],
  );
  private readonly library = toSignal(
    inject(SettingsApi)
      .get()
      .pipe(
        map((settings) => settings.platform.jenkinsLibrary),
        catchError(() => of(null)),
      ),
    { initialValue: null },
  );

  protected readonly productForm = new FormGroup({
    name: new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.required,
        Validators.maxLength(200),
        (control: AbstractControl) => this.nameInUse(control),
      ],
    }),
    ownerTeam: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(200)] }),
    contactEmail: new FormControl('', {
      nonNullable: true,
      validators: [Validators.email, Validators.maxLength(320)],
    }),
    appScanKeyId: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(200)],
    }),
  });
  protected readonly productId = new FormControl<number | null>(null);

  private readonly typedName = toSignal(
    this.productForm.controls.name.valueChanges.pipe(map((name) => name.trim() || null)),
    { initialValue: null },
  );
  protected readonly productName = computed(
    () => this.existing()?.name ?? this.typedName() ?? 'your product',
  );

  protected readonly nextLabel = computed(() =>
    this.step() === 3 ? 'Save and create the pipelines' : 'Continue',
  );

  constructor() {
    this.productForm.controls.name.valueChanges
      .pipe(
        map((name) => name.trim()),
        debounceTime(300),
        distinctUntilChanged(),
        tap(() => this.code.set('')),
        switchMap((name) => (name ? this.suggestCode(name) : of(''))),
        takeUntilDestroyed(),
      )
      .subscribe((code) => this.code.set(code));
    this.productId.valueChanges
      .pipe(
        filter((id): id is number => id !== null),
        tap(() => this.productError.set(null)),
        switchMap((id) =>
          this.productsApi.get(id).pipe(
            catchError((error) => {
              this.productError.set(errorMessage(error));
              return of(null);
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((product) => this.useProduct(product));
  }

  hasUnsavedChanges(): boolean {
    return !this.result() && (this.changed() || this.productForm.dirty);
  }

  protected canRevisit(index: number): boolean {
    return index < this.step() && !this.result() && !this.saving();
  }

  protected goTo(index: number): void {
    if (this.canRevisit(index)) {
      this.checked.set(false);
      this.step.set(index);
    }
  }

  protected back(): void {
    this.goTo(this.step() - 1);
  }

  protected next(): void {
    this.checked.set(true);
    if (this.step() === 1) {
      this.productForm.controls.name.updateValueAndValidity();
      this.productForm.markAllAsTouched();
    }
    if (!this.ready()) {
      return;
    }
    if (this.step() === 3) {
      this.save();
      return;
    }
    this.checked.set(false);
    this.step.update((step) => step + 1);
  }

  protected chooseMode(mode: ProductMode | null): void {
    this.mode.set(mode ?? 'new');
    if (mode !== 'existing') {
      this.productId.setValue(null, { emitEvent: false });
      this.useProduct(null);
    }
  }

  protected addService(): void {
    this.openService(null);
  }

  protected changeService(index: number): void {
    this.openService(index);
  }

  protected removeService(index: number): void {
    this.services.update((services) => services.filter((_, position) => position !== index));
    this.changed.set(true);
  }

  protected serviceSummary(service: OnboardingService): string {
    const parts = [choiceLabel(TOOLS, service.tool)];
    if (this.pipeline() !== 'SAST' || service.id !== null) {
      parts.push(`runs on ${choiceLabel(TARGETS, deploysWith(this.pipeline()!, service))}`);
    }
    if (service.openShiftProject) {
      parts.push(`project ${service.openShiftProject}`);
    }
    return parts.join(' · ');
  }

  protected jenkinsfileOf(start: ServiceStart): string {
    return start.pipeline ? jenkinsfile([start.pipeline], this.library()) : '';
  }

  protected copied(): void {
    this.notifier.success('Jenkinsfile copied to the clipboard');
  }

  protected restart(): void {
    this.result.set(null);
    this.pipeline.set(null);
    this.chooseMode('new');
    this.productForm.reset();
    this.services.set([]);
    this.changed.set(false);
    this.checked.set(false);
    this.saveError.set(null);
    this.saveProblems.set([]);
    this.step.set(0);
    this.catalogue.reload();
  }

  private ready(): boolean {
    switch (this.step()) {
      case 0:
        return this.pipeline() !== null;
      case 1:
        return this.mode() === 'new' ? this.productForm.valid : this.existing() !== null;
      case 2:
        return this.services().length > 0;
      default:
        return !this.saving();
    }
  }

  private openService(index: number | null): void {
    const services = this.services();
    this.dialog
      .open<OnboardingServiceDialog, ServiceDialogData, OnboardingService>(
        OnboardingServiceDialog,
        {
          data: {
            pipeline: this.pipeline()!,
            service: index === null ? null : services[index],
            takenNames: services
              .filter((_, position) => position !== index)
              .map((service) => service.name),
          },
        },
      )
      .afterClosed()
      .subscribe((service) => {
        if (!service) {
          return;
        }
        this.services.update((list) =>
          index === null
            ? [...list, service]
            : list.map((current, position) => (position === index ? service : current)),
        );
        this.changed.set(true);
      });
  }

  private useProduct(product: Product | null): void {
    this.existing.set(product);
    const added = this.services().filter((service) => service.id === null);
    this.services.set([...(product?.services.map(fromService) ?? []), ...added]);
  }

  private save(): void {
    const pipeline = this.pipeline()!;
    const existing = this.existing();
    this.saving.set(true);
    this.saveError.set(null);
    this.saveProblems.set([]);
    const saved: Observable<Product> = existing
      ? this.productsApi.update(
          existing.id,
          productRequest(existing, this.services(), pipeline),
          pipeline,
        )
      : this.suggestCode(this.productForm.controls.name.value.trim()).pipe(
          switchMap((code) =>
            this.productsApi.create(
              productRequest(this.newProduct(code), this.services(), pipeline),
              pipeline,
            ),
          ),
        );
    saved
      .pipe(
        switchMap((product) =>
          this.pipelinesApi.listForProduct(product.id).pipe(
            catchError(() => of([])),
            map((services) => ({ product, starts: servicesToStart(services, pipeline) })),
          ),
        ),
        finalize(() => this.saving.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (onboarded) => {
          this.result.set(onboarded);
          this.checked.set(false);
          this.step.set(4);
        },
        error: (error) => {
          this.saveError.set(errorMessage(error));
          this.saveProblems.set(
            fieldProblems(error).map((problem) => problemText(problem, this.services())),
          );
        },
      });
  }

  private newProduct(code: string): NewProduct {
    const value = this.productForm.getRawValue();
    return { ...value, code };
  }

  private suggestCode(name: string): Observable<string> {
    return this.productsApi.suggestCode(name).pipe(catchError(() => of('')));
  }

  private nameInUse(control: AbstractControl): ValidationErrors | null {
    const name = String(control.value ?? '')
      .trim()
      .toLowerCase();
    const taken = this.products().some((product) => product.name.toLowerCase() === name);
    return taken
      ? { rule: 'This product is already in the portal: choose "A product in the portal" above' }
      : null;
  }
}
