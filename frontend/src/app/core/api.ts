import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  MonitoringOverview,
  MonitoringStatus,
  Pipeline,
  PipelineMonitoring,
  PipelineRequest,
  Product,
  ProductMonitoring,
  ProductRequest,
  ProductSummary,
  ServicePipelines,
} from './models';

@Injectable({ providedIn: 'root' })
export class ProductsApi {
  private readonly http = inject(HttpClient);

  list(search?: string): Observable<ProductSummary[]> {
    const params = search ? new HttpParams().set('search', search) : undefined;
    return this.http.get<ProductSummary[]>('/api/products', { params });
  }

  get(id: number): Observable<Product> {
    return this.http.get<Product>(`/api/products/${id}`);
  }

  create(request: ProductRequest): Observable<Product> {
    return this.http.post<Product>('/api/products', request);
  }

  update(id: number, request: ProductRequest): Observable<Product> {
    return this.http.put<Product>(`/api/products/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/api/products/${id}`);
  }

  config(id: number): Observable<string> {
    return this.http.get(`/api/products/${id}/config`, { responseType: 'text' });
  }
}

@Injectable({ providedIn: 'root' })
export class PipelinesApi {
  private readonly http = inject(HttpClient);

  listForProduct(productId: number): Observable<ServicePipelines[]> {
    return this.http.get<ServicePipelines[]>(`/api/products/${productId}/pipelines`);
  }

  get(id: number): Observable<Pipeline> {
    return this.http.get<Pipeline>(`/api/pipelines/${id}`);
  }

  create(serviceId: number, request: PipelineRequest): Observable<Pipeline> {
    return this.http.post<Pipeline>(`/api/services/${serviceId}/pipelines`, request);
  }

  update(id: number, request: PipelineRequest): Observable<Pipeline> {
    return this.http.put<Pipeline>(`/api/pipelines/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/api/pipelines/${id}`);
  }

  revokeKey(id: number, reason: string): Observable<Pipeline> {
    return this.http.post<Pipeline>(`/api/pipelines/${id}/keys/revoke`, { reason });
  }

  issueKey(id: number): Observable<Pipeline> {
    return this.http.post<Pipeline>(`/api/pipelines/${id}/keys`, {});
  }

  config(key: string): Observable<string> {
    return this.http.get(`/api/dso/config/${key}`, { responseType: 'text' });
  }
}

@Injectable({ providedIn: 'root' })
export class MonitoringApi {
  private readonly http = inject(HttpClient);

  status(): Observable<MonitoringStatus> {
    return this.http.get<MonitoringStatus>('/api/monitoring/status');
  }

  overview(): Observable<MonitoringOverview> {
    return this.http.get<MonitoringOverview>('/api/monitoring/products');
  }

  product(id: number): Observable<ProductMonitoring> {
    return this.http.get<ProductMonitoring>(`/api/monitoring/products/${id}`);
  }

  pipeline(id: number, range: string): Observable<PipelineMonitoring> {
    return this.http.get<PipelineMonitoring>(`/api/monitoring/pipelines/${id}`, { params: { range } });
  }
}
