import { Routes } from '@angular/router';
import { BEADLE, CHANGES, EVIDENCE, MONITORING, ONBOARDING, PRODUCTS, SETTINGS } from './core/sections';
import { unsavedChangesGuard } from './core/unsaved-changes';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'products' },
  {
    path: 'products',
    title: PRODUCTS.heading,
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
    path: 'products/:id/change',
    title: 'ServiceNow change template',
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () =>
      import('./changes/change-profile-editor').then((m) => m.ChangeProfileEditor),
  },
  {
    path: 'products/:id/edit',
    title: 'Edit product',
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('./products/product-editor').then((m) => m.ProductEditor),
  },
  {
    path: 'monitoring',
    title: MONITORING.heading,
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
    title: EVIDENCE.heading,
    loadComponent: () => import('./evidence/change-evidence').then((m) => m.ChangeEvidencePage),
  },
  {
    path: 'settings',
    title: SETTINGS.heading,
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('./settings/global-settings').then((m) => m.GlobalSettingsPage),
  },
  {
    path: 'beadle',
    title: BEADLE.heading,
    loadComponent: () => import('./beadle/beadle-overview').then((m) => m.BeadleOverview),
  },
  {
    path: 'beadle/onboarding',
    title: ONBOARDING.heading,
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('./beadle/onboarding').then((m) => m.Onboarding),
  },
  {
    path: 'beadle/changes',
    title: CHANGES.heading,
    loadComponent: () => import('./changes/production-changes').then((m) => m.ProductionChanges),
  },
  {
    path: 'beadle/changes/new',
    title: 'Raise a production change',
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('./changes/change-wizard').then((m) => m.ChangeWizard),
  },
  {
    path: 'beadle/changes/:id',
    title: 'Production change',
    loadComponent: () => import('./changes/change-detail').then((m) => m.ChangeDetail),
  },
  { path: '**', redirectTo: 'products' },
];
