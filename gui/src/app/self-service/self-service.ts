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
import { NgTemplateOutlet } from '@angular/common';
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
  finalize,
  map,
  of,
  switchMap,
  tap,
} from 'rxjs';
import { DepartmentsApi, PipelinesApi, ProductsApi, SettingsApi } from '../core/api';
import { errorMessage, fieldProblems } from '../core/errors';
import { Product } from '../core/models';
import { Notifier } from '../core/notifier';
import { SELF_SERVICE } from '../core/sections';
import { HasUnsavedChanges } from '../core/unsaved-changes';
import { byDepartment } from '../products/departments';
import { jenkinsfile } from '../products/jenkinsfile';
import { filled, max, text } from '../shared/form-controls';
import { errorText } from '../shared/form-errors';
import { ChoiceTiles } from '../shared/choice-tiles';
import {
  NewProduct,
  WizardPipeline,
  WizardService,
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
} from './self-service-model';
import { ServiceDialog, ServiceDialogData } from './service-dialog';

interface Onboarded {
  product: Product;
  starts: ServiceStart[];
}

const STEPS = ['Pipeline', 'Product', 'Services', 'Review', 'Next steps'];

@Component({
  selector: 'dso-self-service',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    NgTemplateOutlet,
    ClipboardModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
    ChoiceTiles,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './self-service.html',
  styleUrl: '../shared/wizard.scss',
})
export class SelfService implements HasUnsavedChanges {
  private readonly productsApi = inject(ProductsApi);
  private readonly pipelinesApi = inject(PipelinesApi);
  private readonly dialog = inject(MatDialog);
  private readonly notifier = inject(Notifier);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly section = SELF_SERVICE;
  protected readonly steps = STEPS;
  protected readonly pipelines = PIPELINES;
  protected readonly modes = PRODUCT_MODES;
  protected readonly errorText = errorText;
  protected readonly preparation = preparation;
  protected readonly jobName = jobName;

  protected readonly step = signal(0);
  protected readonly checked = signal(false);
  protected readonly changed = signal(false);
  protected readonly pipeline = signal<WizardPipeline | null>(null);
  protected readonly pipelineLabel = computed(() => {
    const pipeline = this.pipeline();
    return pipeline ? pipelineLabel(pipeline) : '';
  });
  protected readonly mode = signal<ProductMode>('new');
  protected readonly services = signal<WizardService[]>([]);
  protected readonly unplaced = computed(() =>
    this.pipeline() === 'SAST'
      ? []
      : this.services()
          .filter(
            (service) =>
              service.id === null &&
              (service.target === null ||
                (service.target === 'OPENSHIFT' && !service.openShiftProject)),
          )
          .map((service) => service.name),
  );
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
  protected readonly departmentsError = signal<string | null>(null);
  protected readonly departments = toSignal(
    inject(DepartmentsApi)
      .list()
      .pipe(
        catchError((error) => {
          this.departmentsError.set(errorMessage(error));
          return of([]);
        }),
      ),
    { initialValue: [] },
  );
  protected readonly productGroups = computed(() =>
    byDepartment(this.departments(), this.products()).filter((group) => group.products.length),
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
    departmentId: new FormControl<number | null>(null, Validators.required),
    name: text('', filled, max(200), (control: AbstractControl) => this.nameInUse(control)),
    ownerTeam: text('', max(200)),
    contactEmail: text('', Validators.email, max(320)),
    appScanKeyId: text('', filled, max(200)),
  });
  protected readonly productId = new FormControl<number | null>(null);

  private readonly typedName = toSignal(
    this.productForm.controls.name.valueChanges.pipe(map((name) => name.trim() || null)),
    { initialValue: null },
  );
  protected readonly productName = computed(
    () => this.existing()?.name ?? this.typedName() ?? 'your product',
  );
  private readonly chosenDepartment = toSignal(
    this.productForm.controls.departmentId.valueChanges,
    { initialValue: null },
  );
  protected readonly needsDepartment = computed(() => this.existing()?.departmentId === null);
  protected readonly departmentName = computed(() => {
    const id = this.existing()?.departmentId ?? this.chosenDepartment();
    return this.departments().find((department) => department.id === id)?.name ?? 'not set';
  });

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
        tap(() => this.productError.set(null)),
        switchMap((id) =>
          id === null
            ? of(null)
            : this.productsApi.get(id).pipe(
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

  protected chooseMode(mode: ProductMode): void {
    this.mode.set(mode);
    if (mode !== 'existing') {
      this.productId.setValue(null);
    }
  }

  protected removeService(index: number): void {
    this.services.update((services) => services.filter((_, position) => position !== index));
    this.changed.set(true);
  }

  protected serviceSummary(service: WizardService): string {
    const target = deploysWith(this.pipeline()!, service);
    const parts = [choiceLabel(TOOLS, service.tool)];
    if (target && (this.pipeline() !== 'SAST' || service.id !== null)) {
      parts.push(`runs on ${choiceLabel(TARGETS, target)}`);
    }
    if (target === 'OPENSHIFT' && service.openShiftProject) {
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
        return this.mode() === 'new'
          ? this.productForm.valid
          : this.existing() !== null &&
              (!this.needsDepartment() || this.productForm.controls.departmentId.valid);
      case 2:
        return this.services().length > 0 && this.unplaced().length === 0;
      default:
        return !this.saving();
    }
  }

  protected openService(index: number | null): void {
    const services = this.services();
    this.dialog
      .open<ServiceDialog, ServiceDialogData, WizardService>(ServiceDialog, {
        data: {
          pipeline: this.pipeline()!,
          service: index === null ? null : services[index],
          takenNames: services
            .filter((_, position) => position !== index)
            .map((service) => service.name),
        },
      })
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
          productRequest(
            existing,
            this.services(),
            pipeline,
            this.productForm.controls.departmentId.value,
          ),
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
