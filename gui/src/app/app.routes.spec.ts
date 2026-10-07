import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { MatIconRegistry } from '@angular/material/icon';
import { Title } from '@angular/platform-browser';
import {
  ActivatedRouteSnapshot,
  Route,
  Router,
  RouterStateSnapshot,
  Routes,
  TitleStrategy,
  provideRouter,
} from '@angular/router';
import { Observable, firstValueFrom, of } from 'rxjs';
import { appConfig } from './app.config';
import { routes } from './app.routes';
import { AdminPage } from './admin/admin-page';
import { DepartmentsAdmin } from './admin/departments-admin';
import { BeadleOverview } from './beadle/beadle-overview';
import { BeadleProduct } from './beadle/beadle-product';
import { BeadleProducts } from './beadle/beadle-products';
import { ChangeDetail } from './changes/change-detail';
import { ChangeWizard } from './changes/change-wizard';
import { ProductionChanges } from './changes/production-changes';
import { BEADLE_ADMINISTRATION, DEVSECOPS_ADMIN } from './core/sections';
import { PortalTitleStrategy } from './core/title-strategy';
import { HasUnsavedChanges, unsavedChangesGuard } from './core/unsaved-changes';
import { ChangeEvidencePage } from './evidence/change-evidence';
import { MonitoringOverview } from './monitoring/monitoring-overview';
import { PipelineMonitoringPage } from './monitoring/pipeline-monitoring';
import { ProductMonitoringPage } from './monitoring/product-monitoring';
import { ProductDetail } from './products/product-detail';
import { ProductEditor } from './products/product-editor';
import { ProductList } from './products/product-list';
import { SelfService } from './self-service/self-service';
import { GlobalSettingsPage } from './settings/global-settings';
import { ConfirmDialog } from './shared/confirm-dialog';

@Component({ template: '' })
class Blank {}

describe('routes', () => {
  const flattened = (list: Routes, parent = ''): [string, Route][] =>
    list.flatMap((route) => {
      const path = [parent, route.path].filter(Boolean).join('/');
      return [[path, route] as [string, Route], ...flattened(route.children ?? [], path)];
    });

  it('loads the page of every section lazily', async () => {
    const pages = await Promise.all(
      flattened(routes)
        .filter(([, route]) => route.loadComponent)
        .map(async ([path, route]) => [path, await route.loadComponent!()]),
    );

    expect(Object.fromEntries(pages)).toEqual({
      'self-service': SelfService,
      monitoring: MonitoringOverview,
      'monitoring/products/:id': ProductMonitoringPage,
      'monitoring/pipelines/:id': PipelineMonitoringPage,
      evidence: ChangeEvidencePage,
      'admin/products/new': ProductEditor,
      'admin/products/:id': ProductDetail,
      'admin/products/:id/edit': ProductEditor,
      admin: AdminPage,
      'admin/departments': DepartmentsAdmin,
      'admin/products': ProductList,
      'admin/settings': GlobalSettingsPage,
      beadle: BeadleOverview,
      'beadle/changes': ProductionChanges,
      'beadle/changes/new': ChangeWizard,
      'beadle/changes/:id': ChangeDetail,
      'beadle/admin/products/:id': BeadleProduct,
      'beadle/admin': AdminPage,
      'beadle/admin/departments': DepartmentsAdmin,
      'beadle/admin/products': BeadleProducts,
    });
  });

  it('shows the pipeline counts only on the departments of DevSecOps Admin', () => {
    const data = Object.fromEntries(
      flattened(routes)
        .filter(([, route]) => route.loadComponent)
        .map(([path, route]) => [path, route.data ?? null]),
    );

    expect(data['admin/departments']).toEqual({ pipelines: true });
    expect(data['beadle/admin/departments']).toBeNull();
    expect(data['admin']).toEqual({ area: DEVSECOPS_ADMIN });
    expect(data['beadle/admin']).toEqual({ area: BEADLE_ADMINISTRATION });
  });

  it('guards the editors against leaving with unsaved changes', () => {
    const guarded = flattened(routes)
      .filter(([, route]) => route.canDeactivate)
      .map(([path]) => path);
    expect(guarded).toEqual([
      'self-service',
      'admin/products/new',
      'admin/products/:id/edit',
      'admin/settings',
      'beadle/changes/new',
      'beadle/admin/products/:id',
    ]);
  });
});

describe('PortalTitleStrategy', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'titled', title: 'Edit product', component: Blank },
          { path: 'untitled', component: Blank },
        ]),
        { provide: TitleStrategy, useClass: PortalTitleStrategy },
      ],
    });
  });

  it('puts the page title before the portal name', async () => {
    const router = TestBed.inject(Router);

    await router.navigateByUrl('/titled');
    expect(TestBed.inject(Title).getTitle()).toBe('Edit product · BBH DevSecOps Management Portal');

    await router.navigateByUrl('/untitled');
    expect(TestBed.inject(Title).getTitle()).toBe('BBH DevSecOps Management Portal');
  });
});

describe('unsavedChangesGuard', () => {
  const page = (unsaved: boolean): HasUnsavedChanges => ({ hasUnsavedChanges: () => unsaved });

  function guard(unsaved: boolean) {
    return TestBed.runInInjectionContext(() =>
      unsavedChangesGuard(
        page(unsaved),
        {} as ActivatedRouteSnapshot,
        {} as RouterStateSnapshot,
        {} as RouterStateSnapshot,
      ),
    );
  }

  it('asks before discarding changes and stays unless the user confirms', async () => {
    const open = vi
      .spyOn(TestBed.inject(MatDialog), 'open')
      .mockReturnValueOnce({ afterClosed: () => of(true) } as unknown as MatDialogRef<unknown>)
      .mockReturnValueOnce({
        afterClosed: () => of(undefined),
      } as unknown as MatDialogRef<unknown>);

    expect(await firstValueFrom(guard(true) as Observable<boolean>)).toBe(true);
    expect(await firstValueFrom(guard(true) as Observable<boolean>)).toBe(false);
    expect(open).toHaveBeenCalledWith(ConfirmDialog, {
      data: expect.objectContaining({ title: 'Discard your changes?', danger: true }),
    });
  });
});

describe('appConfig', () => {
  it('sets the portal title strategy and the outlined icon font', () => {
    TestBed.configureTestingModule({ providers: appConfig.providers });

    expect(TestBed.inject(TitleStrategy)).toBeInstanceOf(PortalTitleStrategy);
    expect(TestBed.inject(MatIconRegistry).getDefaultFontSetClass()).toEqual([
      'material-icons-outlined',
    ]);
  });
});
