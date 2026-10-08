import { Routes } from '@angular/router';
import {
  ADMIN,
  ADMIN_DEPARTMENTS,
  ADMIN_PRODUCTS,
  ADMIN_SETTINGS,
  ADMIN_TEMPLATE,
  BEADLE_ADMIN,
  BEADLE_ADMINISTRATION,
  BEADLE_DEPARTMENTS,
  BEADLE_PRODUCTS,
  CHANGES,
  DEVSECOPS_ADMIN,
  EVIDENCE,
  MONITORING,
  NEW_CHANGE,
  PIPELINES,
  SELF_SERVICE,
} from './core/sections';
import { unsavedChangesGuard } from './core/unsaved-changes';

const adminPage = () => import('./admin/admin-page').then((m) => m.AdminPage);
const departmentsAdmin = () => import('./admin/departments-admin').then((m) => m.DepartmentsAdmin);

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'monitoring' },
  {
    path: 'pipelines',
    title: PIPELINES.heading,
    loadComponent: () => import('./pipelines/pipeline-list').then((m) => m.PipelineList),
  },
  {
    path: 'pipelines/:id',
    title: 'Pipeline',
    loadComponent: () => import('./pipelines/pipeline-page').then((m) => m.PipelinePage),
  },
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
        path: 'template',
        title: `${ADMIN_TEMPLATE.label} · ${ADMIN.heading}`,
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () =>
          import('./settings/service-template-page').then((m) => m.ServiceTemplatePage),
      },
      {
        path: 'settings',
        title: `${ADMIN_SETTINGS.label} · ${ADMIN.heading}`,
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () => import('./settings/global-settings').then((m) => m.GlobalSettingsPage),
      },
    ],
  },
  { path: 'beadle', pathMatch: 'full', redirectTo: 'beadle/changes' },
  {
    path: 'beadle/changes',
    title: CHANGES.heading,
    loadComponent: () => import('./changes/changes-list').then((m) => m.ChangesList),
  },
  { path: 'beadle/changes/new', redirectTo: 'beadle/new-change' },
  {
    path: 'beadle/new-change',
    title: NEW_CHANGE.heading,
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('./changes/change-wizard').then((m) => m.ChangeWizard),
  },
  {
    path: 'beadle/changes/:id',
    title: 'ProTech change',
    loadComponent: () => import('./changes/change-detail').then((m) => m.ChangeDetail),
  },
  {
    path: 'beadle/changes/:id/edit',
    title: 'Edit ProTech change',
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('./changes/change-edit').then((m) => m.ChangeEdit),
  },
  {
    path: 'beadle/admin/products/:id',
    title: `Product · ${BEADLE_ADMIN.heading}`,
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
  { path: 'products/:id/change', redirectTo: 'beadle/admin/products/:id' },
  { path: 'products', redirectTo: 'admin/products' },
  { path: 'settings', redirectTo: 'admin/settings' },
  { path: 'beadle/onboarding', redirectTo: 'self-service' },
  { path: '**', redirectTo: 'monitoring' },
];
