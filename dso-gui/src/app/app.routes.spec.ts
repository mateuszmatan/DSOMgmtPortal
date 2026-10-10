import { DEFAULT_DIALOG_CONFIG, Dialog, DialogRef } from '@angular/cdk/dialog';
import { ApplicationInitStatus, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
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
import { SvgIconRegistryService } from 'angular-svg-icon';
import { Observable, firstValueFrom, of } from 'rxjs';
import { appConfig } from './app.config';
import { routes } from './app.routes';
import { AdminPage } from '@common/admin/admin-page';
import { DepartmentsAdmin } from '@common/admin/departments-admin';
import { BeadleProduct } from '../../../beadle-gui/src/app/beadle/beadle-product';
import { BeadleProducts } from '../../../beadle-gui/src/app/beadle/beadle-products';
import { ChangeDetail } from '../../../beadle-gui/src/app/changes/change-detail';
import { ChangeEdit } from '../../../beadle-gui/src/app/changes/change-edit';
import { ChangeWizard } from '../../../beadle-gui/src/app/changes/change-wizard';
import { ChangesList } from '../../../beadle-gui/src/app/changes/changes-list';
import { BEADLE_ADMINISTRATION, DEVSECOPS_ADMIN } from './core/sections';
import { PortalTitleStrategy } from '@common/core/title-strategy';
import { HasUnsavedChanges, unsavedChangesGuard } from '@common/core/unsaved-changes';
import { ChangeEvidencePage } from './evidence/change-evidence';
import { MonitoringOverview } from './monitoring/monitoring-overview';
import { PipelineMonitoringPage } from './monitoring/pipeline-monitoring';
import { ProductMonitoringPage } from './monitoring/product-monitoring';
import { ProductDetail } from './products/product-detail';
import { ProductEditor } from './products/product-editor';
import { PipelineList } from './pipelines/pipeline-list';
import { PipelinePage } from './pipelines/pipeline-page';
import { ProductList } from './products/product-list';
import { SelfService } from './self-service/self-service';
import { GlobalSettingsPage } from './settings/global-settings';
import { ServiceTemplatePage } from './settings/service-template-page';
import { ConfirmDialog } from '@common/shared/confirm-dialog';

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
      pipelines: PipelineList,
      'pipelines/:id': PipelinePage,
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
      'admin/template': ServiceTemplatePage,
      'admin/settings': GlobalSettingsPage,
      'beadle/changes': ChangesList,
      'beadle/new-change': ChangeWizard,
      'beadle/changes/:id': ChangeDetail,
      'beadle/changes/:id/edit': ChangeEdit,
      'beadle/changes/:id/secure-coding': ChangeSecureCoding,
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
      'admin/template',
      'admin/settings',
      'beadle/new-change',
      'beadle/changes/:id/edit',
      'beadle/changes/:id/secure-coding',
      'beadle/admin/products/:id',
    ]);
  });
});

describe('addresses of the former pages', () => {
  it.each([
    ['/products', '/admin/products'],
    ['/products/new', '/admin/products/new'],
    ['/products/5/edit', '/admin/products/5/edit'],
    ['/products/5/change', '/beadle/admin/products/5'],
    ['/settings', '/admin/settings'],
    ['/beadle/onboarding', '/self-service'],
    ['/beadle', '/beadle/changes'],
    ['/beadle/changes/new', '/beadle/new-change'],
  ])('send %s to %s', async (former, current) => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          ...routes.filter((route) => route.redirectTo && route.path !== '**'),
          { path: '**', component: Blank },
        ]),
      ],
    });
    const router = TestBed.inject(Router);

    await router.navigateByUrl(former);

    expect(router.url).toBe(current);
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
      .spyOn(TestBed.inject(Dialog), 'open')
      .mockReturnValueOnce({ closed: of(true) } as unknown as DialogRef<unknown>)
      .mockReturnValueOnce({ closed: of(undefined) } as unknown as DialogRef<unknown>);

    expect(await firstValueFrom(guard(true) as Observable<boolean>)).toBe(true);
    expect(await firstValueFrom(guard(true) as Observable<boolean>)).toBe(false);
    expect(open).toHaveBeenCalledWith(ConfirmDialog, {
      data: expect.objectContaining({ title: 'Discard your changes?', danger: true }),
    });
  });
});

describe('appConfig', () => {
  it('sets the portal title strategy, square dialogs and registers the portal icons', async () => {
    TestBed.configureTestingModule({ providers: appConfig.providers });
    await TestBed.inject(ApplicationInitStatus).donePromise;

    expect(TestBed.inject(TitleStrategy)).toBeInstanceOf(PortalTitleStrategy);
    expect(TestBed.inject(DEFAULT_DIALOG_CONFIG)).toEqual(
      expect.objectContaining({ panelClass: 'dso-dialog' }),
    );
    const svg = await firstValueFrom(
      TestBed.inject(SvgIconRegistryService).getSvgByName('search')!,
    );
    expect(svg?.tagName.toLowerCase()).toBe('svg');
  });
});
