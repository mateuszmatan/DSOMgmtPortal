import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { Router, RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, map } from 'rxjs';
import { ProductsApi } from '../core/api';
import { errorMessage } from '../core/errors';
import { ProductSummary } from '../core/models';
import { PRODUCTS } from '../core/sections';
import { RelativeTimePipe } from '../shared/formatting';

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
  private readonly router = inject(Router);

  protected readonly section = PRODUCTS;
  protected readonly columns = ['product', 'ownerTeam', 'services', 'pipelines', 'updatedAt'];
  protected readonly search = new FormControl('', { nonNullable: true });
  private readonly query = toSignal(
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

  protected readonly errorMessage = errorMessage;

  protected open(product: ProductSummary): void {
    this.router.navigate(['/products', product.id]);
  }
}
