import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { Router, RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, filter, map, switchMap } from 'rxjs';
import { DepartmentsApi, ProductsApi } from '../core/api';
import { errorMessage } from '../core/errors';
import { Department, ProductSummary } from '../core/models';
import { Notifier } from '../core/notifier';
import { PRODUCTS } from '../core/sections';
import { BarChart, BarRow } from '../shared/bar-chart';
import { ConfirmDialog, ConfirmDialogData } from '../shared/confirm-dialog';
import { RelativeTimePipe, counted } from '../shared/formatting';
import { DepartmentDialog } from './department-dialog';
import { byDepartment, tally } from './departments';

@Component({
  selector: 'dso-product-list',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    MatTableModule,
    BarChart,
    RelativeTimePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './product-list.html',
  styleUrl: './product-list.scss',
})
export class ProductList {
  private readonly api = inject(ProductsApi);
  private readonly departmentsApi = inject(DepartmentsApi);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly notifier = inject(Notifier);

  protected readonly section = PRODUCTS;
  protected readonly columns = ['product', 'ownerTeam', 'services', 'pipelines', 'updatedAt'];
  protected readonly search = new FormControl('', { nonNullable: true });
  protected readonly query = toSignal(
    this.search.valueChanges.pipe(
      debounceTime(250),
      map((value) => value.trim()),
      distinctUntilChanged(),
    ),
    { initialValue: '' },
  );

  protected readonly products = rxResource({
    params: () => this.query(),
    stream: ({ params }) => this.api.list(params),
  });
  protected readonly departments = rxResource({ stream: () => this.departmentsApi.list() });

  protected readonly groups = computed(() =>
    this.products.hasValue() && this.departments.hasValue()
      ? byDepartment(this.departments.value(), this.products.value()).filter(
          (group) => group.products.length || !this.query(),
        )
      : null,
  );
  protected readonly empty = computed(() =>
    (this.groups() ?? []).every((group) => group.products.length === 0),
  );
  protected readonly total = computed(() => {
    const groups = this.groups() ?? [];
    const placed = groups.filter((group) => group.department);
    const products = placed.reduce((sum, group) => sum + group.products.length, 0);
    const unassigned = groups.find((group) => !group.department)?.products.length ?? 0;
    const total = `${counted(products, 'product')} in ${counted(placed.length, 'department')}`;
    return unassigned ? `${total} · ${unassigned} not in a department` : total;
  });

  protected readonly chart = computed<BarRow[]>(() =>
    this.departments.hasValue()
      ? this.departments.value().map((department) => ({
          label: department.name,
          note: `${counted(department.pipelineCount, 'pipeline')} · ${counted(department.productCount, 'product')}`,
          segments: [
            {
              swatch: 'active',
              label: 'active',
              count: department.activePipelineCount,
            },
            {
              swatch: 'disabled',
              label: 'invalidated',
              count: department.pipelineCount - department.activePipelineCount,
            },
          ],
        }))
      : [],
  );

  protected readonly errorMessage = errorMessage;
  protected readonly tally = tally;

  protected open(product: ProductSummary): void {
    this.router.navigate(['/products', product.id]);
  }

  protected addDepartment(): void {
    this.editDepartment(null, (saved) => `${saved.name} added`);
  }

  protected renameDepartment(department: Department): void {
    this.editDepartment(department, (saved) => `${department.name} renamed to ${saved.name}`);
  }

  protected deleteHint(department: Department): string | null {
    return department.productCount
      ? `${department.name} still has ${counted(department.productCount, 'product')}. ` +
          'Move them to another department first.'
      : null;
  }

  protected deleteDepartment(department: Department): void {
    this.dialog
      .open<ConfirmDialog, ConfirmDialogData, boolean>(ConfirmDialog, {
        data: {
          title: `Delete ${department.name}?`,
          message: 'The department has no products. It is removed from the portal.',
          confirmLabel: 'Delete department',
          danger: true,
        },
      })
      .afterClosed()
      .pipe(
        filter((confirmed) => confirmed === true),
        switchMap(() => this.departmentsApi.delete(department.id)),
      )
      .subscribe({
        next: () => {
          this.notifier.success(`${department.name} deleted`);
          this.departments.reload();
        },
        error: (error) => this.notifier.error(error),
      });
  }

  private editDepartment(
    department: Department | null,
    message: (saved: Department) => string,
  ): void {
    this.dialog
      .open<DepartmentDialog, Department | null, Department>(DepartmentDialog, { data: department })
      .afterClosed()
      .pipe(filter((saved): saved is Department => !!saved))
      .subscribe((saved) => {
        this.notifier.success(message(saved));
        this.departments.reload();
      });
  }
}
