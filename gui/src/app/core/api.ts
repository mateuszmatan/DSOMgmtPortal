import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import {
  Department,
  DepartmentRequest,
  GlobalSettings,
  GlobalSettingsRequest,
  MonitoringOverview,
  MonitoringStatus,
  Pipeline,
  PipelineMonitoring,
  PipelineRequest,
  PipelineType,
  PortfolioActivity,
  Product,
  ProductMonitoring,
  ProductEvidence,
  ProductRequest,
  ProductSummary,
  ServicePipelines,
} from './models';

function typeParam(pipelineType?: PipelineType): HttpParams | undefined {
  return pipelineType ? new HttpParams().set('pipelineType', pipelineType) : undefined;
}

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

  create(request: ProductRequest, pipelineType?: PipelineType): Observable<Product> {
    return this.http.post<Product>('/api/products', request, { params: typeParam(pipelineType) });
  }

  update(id: number, request: ProductRequest, pipelineType?: PipelineType): Observable<Product> {
    return this.http.put<Product>(`/api/products/${id}`, request, {
      params: typeParam(pipelineType),
    });
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/api/products/${id}`);
  }

  suggestCode(name: string): Observable<string> {
    return this.http
      .get<{ code: string }>('/api/products/code-suggestion', {
        params: new HttpParams().set('name', name),
      })
      .pipe(map((suggestion) => suggestion.code));
  }

  config(id: number): Observable<string> {
    return this.http.get(`/api/products/${id}/config`, { responseType: 'text' });
  }
}

@Injectable({ providedIn: 'root' })
export class DepartmentsApi {
  private readonly http = inject(HttpClient);

  list(): Observable<Department[]> {
    return this.http.get<Department[]>('/api/departments');
  }

  create(name: string): Observable<Department> {
    return this.http.post<Department>('/api/departments', { name });
  }

  rename(id: number, request: DepartmentRequest): Observable<Department> {
    return this.http.put<Department>(`/api/departments/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/api/departments/${id}`);
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

  config(id: number): Observable<string> {
    return this.http.get(`/api/pipelines/${id}/config`, { responseType: 'text' });
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
    return this.http.get<PipelineMonitoring>(`/api/monitoring/pipelines/${id}`, {
      params: { range },
    });
  }

  activity(range: string): Observable<PortfolioActivity> {
    return this.http.get<PortfolioActivity>('/api/monitoring/activity', { params: { range } });
  }
}

@Injectable({ providedIn: 'root' })
export class SettingsApi {
  private readonly http = inject(HttpClient);

  get(): Observable<GlobalSettings> {
    return this.http.get<GlobalSettings>('/api/settings');
  }

  update(request: GlobalSettingsRequest): Observable<GlobalSettings> {
    return this.http.put<GlobalSettings>('/api/settings', request);
  }

  config(): Observable<string> {
    return this.http.get('/api/settings/config', {
      params: { format: 'yaml' },
      responseType: 'text',
    });
  }
}

@Injectable({ providedIn: 'root' })
export class EvidenceApi {
  private readonly http = inject(HttpClient);

  product(productId: number): Observable<ProductEvidence> {
    return this.http.get<ProductEvidence>(`/api/evidence/products/${productId}`);
  }
}
