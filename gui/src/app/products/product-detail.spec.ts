import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { Pipeline, Product } from '../core/models';
import { CodeDialog } from '../shared/code-dialog';
import { ConfirmDialog } from '../shared/confirm-dialog';
import {
  anotherService,
  globalSettings,
  pipeline,
  product,
  revokedKey,
  service,
  servicePipelines,
} from '../testing/fixtures';
import { GeneratedKeys } from './generated-keys';
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

  async function load(stored: Product = product(), services = [servicePipelines()]) {
    fixture.detectChanges();
    http.expectOne('/api/products/1').flush(stored);
    http.expectOne('/api/products/1/pipelines').flush(services);
    await fixture.whenStable();
  }

  const invalidated = () =>
    servicePipelines({ pipelines: [pipeline({ activeKey: null, keys: [revokedKey()] })] });
  const regenerated = () =>
    pipeline({
      activeKey: {
        ...pipeline().activeKey!,
        id: 1001,
        value: 'c'.repeat(36),
        hint: 'cccccccc…cccc',
      },
    });
  const stats = () => [...page().querySelectorAll('.stat')].map((stat) => stat.textContent?.trim());
  const textButton = (label: string) =>
    [...page().querySelectorAll<HTMLButtonElement>('button.text-link')].find(
      (element) => element.textContent?.trim() === label,
    );
  const snackText = () =>
    [...document.querySelectorAll('mat-snack-bar-container')]
      .map((container) => container.textContent)
      .join(' ');

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

  describe('new services', () => {
    const twoServices = () => [
      servicePipelines(),
      servicePipelines({
        serviceId: 11,
        serviceName: 'api',
        pipelines: [pipeline({ id: 110, serviceId: 11, serviceName: 'api' })],
      }),
    ];

    it('announces the key generated for the new service and shows it', async () => {
      TestBed.inject(GeneratedKeys).record(1, ['gui']);
      await load();

      const notice = page().querySelector('.generated');
      expect(notice?.getAttribute('role')).toBe('status');
      expect(notice?.textContent).toContain('Pipeline key generated for the new service gui.');
      expect(page().querySelector('.service-title .tag.new')?.textContent).toBe('New');
      expect(page().querySelector('.key-value')?.textContent?.trim()).toBe(
        pipeline().activeKey!.value,
      );
    });

    it('counts the services that got a key and leaves the others alone', async () => {
      TestBed.inject(GeneratedKeys).record(1, ['gui', 'api', 'deleted-meanwhile']);
      await load(product({ services: [service(), anotherService()] }), twoServices());

      expect(page().querySelector('.generated')?.textContent).toContain(
        'Pipeline keys generated for 2 new services: gui, api.',
      );
      expect(page().querySelectorAll('.tag.new').length).toBe(2);
    });

    it('says nothing about a new service the API gave no active key', async () => {
      TestBed.inject(GeneratedKeys).record(1, ['gui']);
      await load(product(), [invalidated()]);

      expect(page().querySelector('.generated')).toBeNull();
      expect(page().querySelector('.tag.new')).toBeNull();
    });

    it('hides the notice when dismissed and does not show it again', async () => {
      TestBed.inject(GeneratedKeys).record(1, ['gui']);
      await load();

      textButton('Dismiss')!.click();
      await fixture.whenStable();

      expect(page().querySelector('.generated')).toBeNull();
      expect(TestBed.inject(GeneratedKeys).take(1)).toEqual([]);
    });

    it('shows no notice for a product opened from the list', async () => {
      await load();

      expect(page().querySelector('.generated')).toBeNull();
      expect(page().querySelector('.key-value')?.textContent?.trim()).toBe('6f1c2d3e…9abc');
    });
  });

  describe('invalidated keys', () => {
    it('offers a visible Regenerate key text button next to the key status', async () => {
      await load(product(), [invalidated()]);

      const regenerate = textButton('Regenerate key')!;
      expect(regenerate.previousElementSibling?.textContent).toBe('Key invalidated');
      expect(regenerate.getAttribute('aria-label')).toBe('Regenerate key of the full pipeline');
      expect(page().querySelector('.revoked-note')?.textContent).toContain(
        'until its key is regenerated',
      );
      expect(page().querySelector('mat-icon')).toBeNull();
      expect(stats()).toContain('1Invalidated keys');
    });

    it('keeps the acronym of a SAST scanning pipeline in the names of its buttons', async () => {
      await load(product(), [
        servicePipelines({ pipelines: [pipeline({ type: 'SAST', activeKey: null })] }),
      ]);

      expect(textButton('Regenerate key')?.getAttribute('aria-label')).toBe(
        'Regenerate key of the SAST scanning pipeline',
      );
      expect(
        page().querySelector('[aria-label="More actions of the SAST scanning pipeline"]'),
      ).not.toBeNull();
    });

    it('regenerates the key without asking and shows the new one', async () => {
      await load(product(), [invalidated()]);
      const open = vi.spyOn(TestBed.inject(MatDialog), 'open');

      textButton('Regenerate key')!.click();
      await fixture.whenStable();
      expect(textButton('Regenerating…')?.disabled).toBe(true);
      const request = http.expectOne({ method: 'POST', url: '/api/pipelines/100/keys' });
      expect(request.request.body).toEqual({});
      request.flush(regenerated());
      await fixture.whenStable();

      expect(open).not.toHaveBeenCalled();
      expect(page().querySelector('.key-value')?.textContent?.trim()).toBe('c'.repeat(36));
      expect(page().querySelector('.key-state')?.textContent).toBe('Key active');
      expect(textButton('Regenerate key')).toBeUndefined();
      expect(stats()).toEqual(['1Services', '1Pipelines', '1Active keys', '0Invalidated keys']);
      expect(snackText()).toContain(
        'Full pipeline of gui has a new key: pass it in the Jenkinsfile',
      );
    });

    it('reports a failed regeneration and lets it be tried again', async () => {
      await load(product(), [invalidated()]);

      textButton('Regenerate key')!.click();
      http
        .expectOne('/api/pipelines/100/keys')
        .flush(
          { title: 'Conflict', detail: 'Pipeline 100 was changed meanwhile' },
          { status: 409, statusText: 'Conflict' },
        );
      await fixture.whenStable();

      expect(snackText()).toContain('Pipeline 100 was changed meanwhile');
      expect(textButton('Regenerate key')?.disabled).toBe(false);
      expect(page().querySelector('.key-state')?.textContent).toBe('Key invalidated');
    });

    it('regenerates the key from the More menu too', async () => {
      await load(product(), [invalidated()]);

      page()
        .querySelector<HTMLButtonElement>('[aria-label="More actions of the full pipeline"]')!
        .click();
      await fixture.whenStable();
      [...document.querySelectorAll<HTMLButtonElement>('.mat-mdc-menu-item')]
        .find((item) => item.textContent?.trim() === 'Regenerate key')!
        .click();
      http.expectOne({ method: 'POST', url: '/api/pipelines/100/keys' }).flush(regenerated());
      await fixture.whenStable();

      expect(page().querySelector('.key-value')?.textContent?.trim()).toBe('c'.repeat(36));
    });

    it('shows a key regenerated in the key history on the page', async () => {
      await load(product(), [invalidated()]);

      page()
        .querySelector<HTMLButtonElement>('[aria-label="More actions of the full pipeline"]')!
        .click();
      await fixture.whenStable();
      [...document.querySelectorAll<HTMLButtonElement>('.mat-mdc-menu-item')]
        .find((item) => item.textContent?.trim() === 'Key history')!
        .click();
      TestBed.tick();
      http
        .expectOne('/api/pipelines/100')
        .flush(pipeline({ activeKey: null, keys: [revokedKey()] }));
      await fixture.whenStable();
      const dialog = document.querySelector<HTMLElement>('dso-key-history-dialog')!;
      [...dialog.querySelectorAll<HTMLButtonElement>('button')]
        .find((element) => element.textContent?.trim() === 'Regenerate key')!
        .click();
      http
        .expectOne({ method: 'POST', url: '/api/pipelines/100/keys' })
        .flush({ ...regenerated(), keys: [regenerated().activeKey!, revokedKey()] });
      await fixture.whenStable();

      expect(dialog.querySelector('.key-status .key-value')?.textContent).toBe('c'.repeat(36));
      expect(page().querySelector('.key-value')?.textContent?.trim()).toBe('c'.repeat(36));
      expect(stats()).toContain('1Active keys');
    });
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
      open.mockReturnValueOnce({
        afterClosed: () => of(undefined),
        componentInstance: { keyIssued: { subscribe: vi.fn() } },
      } as unknown as MatDialogRef<unknown>);

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

    it('reports a key that could not be replaced', async () => {
      await load();
      closingWith(true);

      await menu('Replace key');
      http
        .expectOne('/api/pipelines/100/keys')
        .flush({ detail: 'Pipeline 100 was not found' }, { status: 404, statusText: 'Not Found' });
      await fixture.whenStable();

      expect(snack()).toContain('Pipeline 100 was not found');
      expect(page().querySelector('.key-value')?.textContent?.trim()).toBe('6f1c2d3e…9abc');
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
