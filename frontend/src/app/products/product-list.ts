import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { Router, RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, map } from 'rxjs';
import { DepartmentsApi, ProductsApi } from '../core/api';
import { errorMessage } from '../core/errors';
import { ProductSummary } from '../core/models';
import { ADMIN_PRODUCTS, adminProduct } from '../core/sections';
import { RelativeTimePipe, counted } from '../shared/formatting';
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

  protected readonly newProduct = `${ADMIN_PRODUCTS.path}/new`;
  protected readonly adminProduct = adminProduct;
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

  protected readonly errorMessage = errorMessage;
  protected readonly tally = tally;

  protected open(product: ProductSummary): void {
    this.router.navigate(adminProduct(product.id));
  }
}
