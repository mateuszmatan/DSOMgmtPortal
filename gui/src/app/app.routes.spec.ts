import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { MatIconRegistry } from '@angular/material/icon';
import { Title } from '@angular/platform-browser';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  TitleStrategy,
  provideRouter,
} from '@angular/router';
import { Observable, firstValueFrom, of } from 'rxjs';
import { appConfig } from './app.config';
import { routes } from './app.routes';
import { BeadleOverview } from './beadle/beadle-overview';
import { Onboarding } from './beadle/onboarding';
import { PortalTitleStrategy } from './core/title-strategy';
import { HasUnsavedChanges, unsavedChangesGuard } from './core/unsaved-changes';
import { ChangeEvidencePage } from './evidence/change-evidence';
import { MonitoringOverview } from './monitoring/monitoring-overview';
import { PipelineMonitoringPage } from './monitoring/pipeline-monitoring';
import { ProductMonitoringPage } from './monitoring/product-monitoring';
import { ProductDetail } from './products/product-detail';
import { ProductEditor } from './products/product-editor';
import { ProductList } from './products/product-list';
import { GlobalSettingsPage } from './settings/global-settings';
import { ConfirmDialog } from './shared/confirm-dialog';

@Component({ template: '' })
class Blank {}

describe('routes', () => {
  it('loads the page of every section lazily', async () => {
    const pages = await Promise.all(
      routes
        .filter((route) => route.loadComponent)
        .map(async (route) => [route.path, await route.loadComponent!()]),
    );

    expect(Object.fromEntries(pages)).toEqual({
      products: ProductList,
      'products/new': ProductEditor,
      'products/:id': ProductDetail,
      'products/:id/edit': ProductEditor,
      monitoring: MonitoringOverview,
      'monitoring/products/:id': ProductMonitoringPage,
      'monitoring/pipelines/:id': PipelineMonitoringPage,
      evidence: ChangeEvidencePage,
      settings: GlobalSettingsPage,
      beadle: BeadleOverview,
      'beadle/onboarding': Onboarding,
    });
  });

  it('guards the editors against leaving with unsaved changes', () => {
    const guarded = routes.filter((route) => route.canDeactivate).map((route) => route.path);
    expect(guarded).toEqual(['products/new', 'products/:id/edit', 'settings', 'beadle/onboarding']);
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
