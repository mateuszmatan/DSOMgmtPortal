import { Routes } from '@angular/router';
import { unsavedChangesGuard } from './core/unsaved-changes';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'products' },
  {
    path: 'products',
    title: 'DevSecOps Product Management',
    loadComponent: () => import('./products/product-list').then((m) => m.ProductList),
  },
  {
    path: 'products/new',
    title: 'Add product',
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('./products/product-editor').then((m) => m.ProductEditor),
  },
  {
    path: 'products/:id',
    title: 'Product',
    loadComponent: () => import('./products/product-detail').then((m) => m.ProductDetail),
  },
  {
    path: 'products/:id/edit',
    title: 'Edit product',
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('./products/product-editor').then((m) => m.ProductEditor),
  },
  {
    path: 'monitoring',
    title: 'DevSecOps Pipeline Monitoring',
    loadComponent: () =>
      import('./monitoring/monitoring-overview').then((m) => m.MonitoringOverview),
  },
  {
    path: 'monitoring/products/:id',
    title: 'Product pipelines',
    loadComponent: () =>
      import('./monitoring/product-monitoring').then((m) => m.ProductMonitoringPage),
  },
  {
    path: 'monitoring/pipelines/:id',
    title: 'Pipeline details',
    loadComponent: () =>
      import('./monitoring/pipeline-monitoring').then((m) => m.PipelineMonitoringPage),
  },
  {
    path: 'evidence',
    title: 'DevSecOps Change Evidence',
    loadComponent: () => import('./evidence/change-evidence').then((m) => m.ChangeEvidencePage),
  },
  {
    path: 'settings',
    title: 'DevSecOps Global Settings',
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('./settings/global-settings').then((m) => m.GlobalSettingsPage),
  },
  { path: '**', redirectTo: 'products' },
];
