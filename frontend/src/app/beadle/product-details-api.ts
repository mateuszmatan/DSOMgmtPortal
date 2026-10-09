import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface ProductDetails {
  id: number;
  code: string;
  name: string;
  ownerTeam: string | null;
  contactEmail: string | null;
  departmentId: number | null;
  version: number;
}

export type ProductDetailsRequest = Omit<ProductDetails, 'id' | 'code'>;

@Injectable({ providedIn: 'root' })
export class ProductDetailsApi {
  private readonly http = inject(HttpClient);

  get(id: number): Observable<ProductDetails> {
    return this.http.get<ProductDetails>(`/api/products/${id}/details`);
  }

  update(id: number, request: ProductDetailsRequest): Observable<ProductDetails> {
    return this.http.put<ProductDetails>(`/api/products/${id}/details`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/api/products/${id}/details`);
  }
}
