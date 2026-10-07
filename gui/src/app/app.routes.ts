import { Routes } from '@angular/router';
import {
  ADMIN,
  ADMIN_DEPARTMENTS,
  ADMIN_PRODUCTS,
  ADMIN_SETTINGS,
  BEADLE,
  BEADLE_ADMIN,
  BEADLE_ADMINISTRATION,
  BEADLE_DEPARTMENTS,
  BEADLE_PRODUCTS,
  CHANGES,
  DEVSECOPS_ADMIN,
  EVIDENCE,
  MONITORING,
  SELF_SERVICE,
} from './core/sections';
import { unsavedChangesGuard } from './core/unsaved-changes';

const adminPage = () => import('./admin/admin-page').then((m) => m.AdminPage);
const departmentsAdmin = () => import('./admin/departments-admin').then((m) => m.DepartmentsAdmin);

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'monitoring' },
  {
    path: 'self-service',
    title: SELF_SERVICE.heading,
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('./self-service/self-service').then((m) => m.SelfService),
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
    path: 'admin/products/new',
    title: 'Add product',
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('./products/product-editor').then((m) => m.ProductEditor),
  },
  {
    path: 'admin/products/:id',
    title: 'Product',
    loadComponent: () => import('./products/product-detail').then((m) => m.ProductDetail),
  },
  {
    path: 'admin/products/:id/edit',
    title: 'Edit product',
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('./products/product-editor').then((m) => m.ProductEditor),
  },
  {
    path: 'admin',
    loadComponent: adminPage,
    data: { area: DEVSECOPS_ADMIN },
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'products' },
      {
        path: 'departments',
        title: `${ADMIN_DEPARTMENTS.label} · ${ADMIN.heading}`,
        data: { pipelines: true },
        loadComponent: departmentsAdmin,
      },
      {
        path: 'products',
        title: `${ADMIN_PRODUCTS.label} · ${ADMIN.heading}`,
        loadComponent: () => import('./products/product-list').then((m) => m.ProductList),
      },
      {
        path: 'settings',
        title: `${ADMIN_SETTINGS.label} · ${ADMIN.heading}`,
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () => import('./settings/global-settings').then((m) => m.GlobalSettingsPage),
      },
    ],
  },
  {
    path: 'beadle',
    title: BEADLE.heading,
    loadComponent: () => import('./beadle/beadle-overview').then((m) => m.BeadleOverview),
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
  {
    path: 'beadle/admin/products/:id',
    title: 'ServiceNow defaults',
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('./beadle/beadle-product').then((m) => m.BeadleProduct),
  },
  {
    path: 'beadle/admin',
    loadComponent: adminPage,
    data: { area: BEADLE_ADMINISTRATION },
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'products' },
      {
        path: 'departments',
        title: `${BEADLE_DEPARTMENTS.label} · ${BEADLE_ADMIN.heading}`,
        loadComponent: departmentsAdmin,
      },
      {
        path: 'products',
        title: `${BEADLE_PRODUCTS.label} · ${BEADLE_ADMIN.heading}`,
        loadComponent: () => import('./beadle/beadle-products').then((m) => m.BeadleProducts),
      },
    ],
  },
  { path: '**', redirectTo: 'monitoring' },
];
