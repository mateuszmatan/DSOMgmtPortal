import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { Pipeline, Product } from '../core/models';
import { CodeDialog } from '../shared/code-dialog';
import { ConfirmDialog } from '../shared/confirm-dialog';
import { globalSettings, pipeline, product, service, servicePipelines } from '../testing/fixtures';
import { KeyHistoryDialog } from './key-history-dialog';
import { PipelineDialog } from './pipeline-dialog';
import { ProductDetail } from './product-detail';
import { RevokeKeyDialog } from './revoke-key-dialog';

describe('ProductDetail', () => {
  let fixture: ComponentFixture<ProductDetail>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ProductDetail],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ProductDetail);
    fixture.componentRef.setInput('id', '1');
  });

  afterEach(() => http.verify());

  const page = () => fixture.nativeElement as HTMLElement;

  async function load(stored: Product = product()) {
    fixture.detectChanges();
    http.expectOne('/api/products/1').flush(stored);
    http.expectOne('/api/products/1/pipelines').flush([servicePipelines()]);
    await fixture.whenStable();
  }

  function withScm(scm: Partial<Product['services'][number]['scm']>): Product {
    const stored = service();
    return product({ services: [{ ...stored, scm: { ...stored.scm, ...scm } }] });
  }

  it('shows the product, its counts and its pipelines without icons', async () => {
    await load();

    expect(page().querySelector('h1')?.textContent).toBe('CertScanner');
    expect(page().querySelector('.breadcrumb')?.textContent).toContain(
      'DevSecOps Product Management',
    );
    expect([...page().querySelectorAll('.stat')].map((stat) => stat.textContent?.trim())).toEqual([
      '1Services',
      '1Pipelines',
      '1Active keys',
      '0Invalidated keys',
    ]);
    expect(page().querySelector('.pipeline-title strong')?.textContent).toBe('Full pipeline');
    expect(page().querySelector('mat-icon')).toBeNull();
  });

  it('links the Bitbucket repository of each service', async () => {
    await load();

    const link = page().querySelector<HTMLAnchorElement>('.repository a')!;
    expect(link.textContent).toBe('https://bitbucket.bbh.com/projects/CERT/repos/gui');
    expect(link.href).toBe('https://bitbucket.bbh.com/projects/CERT/repos/gui');
    expect(link.target).toBe('_blank');
  });

  it('builds the repository link from the Bitbucket fields when the service has no URL', async () => {
    await load(
      withScm({
        repositoryUrl: null,
        apiUrl: 'https://bitbucket.bbh.com',
        projectKey: 'TA',
        repoSlug: 'cert-scanner',
      }),
    );

    expect(page().querySelector('.repository a')?.textContent).toBe(
      'https://bitbucket.bbh.com/projects/TA/repos/cert-scanner',
    );
  });

  it('says when a service has no Bitbucket repository', async () => {
    await load(withScm({ repositoryUrl: null }));

    expect(page().querySelector('.repository a')).toBeNull();
    expect(page().querySelector('.repository .not-recorded')?.textContent).toBe('Not set');
  });

  it('reveals and hides the active key with a text button', async () => {
    await load();
    const toggle = () => page().querySelector<HTMLButtonElement>('.key .text-link')!;

    expect(page().querySelector('.key-value')?.textContent?.trim()).toBe('6f1c2d3e…9abc');
    expect(toggle().textContent?.trim()).toBe('Show');
    expect(toggle().getAttribute('aria-label')).toBe('Show the key of the full pipeline');

    toggle().click();
    await fixture.whenStable();

    expect(page().querySelector('.key-value')?.textContent?.trim()).toBe(
      pipeline().activeKey!.value,
    );
    expect(toggle().textContent?.trim()).toBe('Hide');
  });

  it('shows only the hint of an active key whose value the API did not send', async () => {
    const stored = pipeline();
    fixture.detectChanges();
    http.expectOne('/api/products/1').flush(product());
    http.expectOne('/api/products/1/pipelines').flush([
      servicePipelines({
        pipelines: [{ ...stored, activeKey: { ...stored.activeKey!, value: null } }],
      }),
    ]);
    await fixture.whenStable();

    expect(page().querySelector('.key-value')?.textContent?.trim()).toBe('6f1c2d3e…9abc');
    expect(page().querySelector('.key .text-link')).toBeNull();
  });

  it('shows the problem detail when a config preview fails', async () => {
    await load();
    const config = [...page().querySelectorAll<HTMLButtonElement>('button')].find(
      (button) => button.textContent?.trim() === 'Config',
    )!;

    config.click();
    http
      .expectOne('/api/pipelines/100/config')
      .flush({ detail: 'Pipeline 100 was not found' }, { status: 404, statusText: '' });
    await fixture.whenStable();

    expect(document.querySelector('.snack-error')?.textContent).toContain(
      'Pipeline 100 was not found',
    );
  });

  describe('actions', () => {
    const spyOnOpen = () => vi.spyOn(TestBed.inject(MatDialog), 'open');
    let open: ReturnType<typeof spyOnOpen>;
    const closed = (result: unknown) =>
      ({ afterClosed: () => of(result) }) as unknown as MatDialogRef<unknown>;

    beforeEach(() => {
      open = spyOnOpen().mockReturnValue(closed(undefined));
      vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    });

    const closingWith = (...results: unknown[]) =>
      results.forEach((result) => open.mockReturnValueOnce(closed(result)));
    const opened = (index = 0) => ({
      component: open.mock.calls[index][0],
      data: open.mock.calls[index][1]?.data as Record<string, unknown>,
    });
    const button = (label: string, root: ParentNode = page()) =>
      [...root.querySelectorAll<HTMLButtonElement>('button')].find(
        (element) => element.textContent?.trim() === label,
      )!;
    const snack = () =>
      [...document.querySelectorAll('mat-snack-bar-container')]
        .map((container) => container.textContent)
        .join(' ');

    async function menu(label: string) {
      button('More').click();
      await fixture.whenStable();
      button(label, document).click();
      await fixture.whenStable();
    }

    it('opens the configuration of the product and of a pipeline', async () => {
      await load();

      button('config.yaml').click();
      http.expectOne('/api/products/1/config').flush('projects: {}');
      button('Config').click();
      http.expectOne('/api/pipelines/100/config').flush('projects:\n  gui: {}');

      expect(opened(0)).toMatchObject({
        component: CodeDialog,
        data: { code: 'projects: {}', fileName: 'cert-config.yaml' },
      });
      expect(opened(1).data['fileName']).toBe('cert-gui-full.yaml');
    });

    it('shows the Jenkinsfile with the library of the global settings, or without them', async () => {
      await load();

      await menu('Jenkinsfile');
      http.expectOne('/api/settings').flush(globalSettings());
      await menu('Jenkinsfile');
      http.expectOne('/api/settings').flush(null, { status: 500, statusText: 'Server Error' });

      expect(opened(0)).toMatchObject({ component: CodeDialog, data: { title: 'Jenkinsfile' } });
      expect(opened(0).data['code']).toContain(globalSettings().platform.jenkinsLibrary);
      expect(opened(1).data['fileName']).toBe('Jenkinsfile');
    });

    it('opens the key history', async () => {
      await load();

      await menu('Key history');

      expect(opened()).toMatchObject({ component: KeyHistoryDialog, data: { id: 100 } });
    });

    it('replaces the key once confirmed and reveals the new one', async () => {
      await load();
      closingWith(true);

      await menu('Replace key');
      expect(opened()).toMatchObject({
        component: ConfirmDialog,
        data: { title: 'Replace the key?', danger: true },
      });
      const replaced = pipeline();
      http.expectOne({ method: 'POST', url: '/api/pipelines/100/keys' }).flush({
        ...replaced,
        activeKey: {
          ...replaced.activeKey!,
          id: 501,
          value: 'b'.repeat(64),
          hint: 'bbbbbbbb…bbbb',
        },
      });
      await fixture.whenStable();

      expect(page().querySelector('.key-value')?.textContent?.trim()).toBe('b'.repeat(64));
      expect(snack()).toContain('New key issued');
    });

    it('issues a key to a pipeline without one and reports a failure', async () => {
      fixture.detectChanges();
      http.expectOne('/api/products/1').flush(product());
      http
        .expectOne('/api/products/1/pipelines')
        .flush([servicePipelines({ pipelines: [pipeline({ activeKey: null })] })]);
      await fixture.whenStable();
      closingWith(true);

      await menu('Issue new key');
      expect(opened().data).toMatchObject({ title: 'Issue a new key?', danger: false });
      http
        .expectOne('/api/pipelines/100/keys')
        .flush({ detail: 'Pipeline 100 was not found' }, { status: 404, statusText: 'Not Found' });
      await fixture.whenStable();

      expect(snack()).toContain('Pipeline 100 was not found');
    });

    it('does nothing when a confirmation is cancelled', async () => {
      await load();
      closingWith(false, undefined);

      await menu('Delete pipeline');
      button('Delete').click();
      await fixture.whenStable();

      http.expectNone('/api/pipelines/100');
      http.expectNone('/api/products/1');
    });

    it('invalidates the key with the reason the dialog asks for', async () => {
      await load();
      closingWith(pipeline({ activeKey: null }));

      await menu('Invalidate key');
      await fixture.whenStable();

      expect(opened().component).toBe(RevokeKeyDialog);
      expect(page().querySelector('.revoked-note')).not.toBeNull();
      expect(
        [...page().querySelectorAll('.stat')].map((stat) => stat.textContent?.trim()),
      ).toContain('1Invalidated keys');
      expect(snack()).toContain('Key invalidated');
    });

    it('adds a pipeline in type order and saves the settings of another', async () => {
      await load();
      const sast = pipeline({ id: 101, type: 'SAST' });
      closingWith(sast, { ...sast, description: 'Nightly scan' });

      button('Add pipeline').click();
      await fixture.whenStable();
      expect(opened()).toMatchObject({ component: PipelineDialog });
      expect(
        [...page().querySelectorAll('.pipeline-title strong')].map((title) => title.textContent),
      ).toEqual(['Full pipeline', 'SAST scanning pipeline']);
      expect(snack()).toContain('SAST scanning pipeline added to gui');

      await menu('Settings');
      expect((opened(1).data['pipeline'] as Pipeline).id).toBe(100);
      expect(snack()).toContain('Pipeline settings saved');
    });

    it('deletes a pipeline once confirmed', async () => {
      await load();
      closingWith(true);

      await menu('Delete pipeline');
      expect(opened().data['message']).toContain('full pipeline of gui');
      http.expectOne({ method: 'DELETE', url: '/api/pipelines/100' }).flush(null);
      await fixture.whenStable();

      expect(page().querySelector('.pipeline-title')).toBeNull();
      expect(snack()).toContain('Pipeline deleted');
    });

    it('deletes the product once confirmed and returns to the list', async () => {
      await load();
      closingWith(true, true);

      button('Delete').click();
      expect(opened().data['title']).toBe('Delete CertScanner?');
      http.expectOne({ method: 'DELETE', url: '/api/products/1' }).flush(null);
      await fixture.whenStable();
      expect(TestBed.inject(Router).navigate).toHaveBeenCalledWith(['/products']);

      await menu('Delete pipeline');
      http
        .expectOne('/api/pipelines/100')
        .flush({ detail: 'Pipeline 100 is in use' }, { status: 409, statusText: 'Conflict' });
      await fixture.whenStable();
      expect(snack()).toContain('Pipeline 100 is in use');
    });

    it('copies the key and says so', async () => {
      await load();

      button('Copy').click();
      await fixture.whenStable();

      expect(snack()).toContain('Key copied to the clipboard');
    });
  });
});
