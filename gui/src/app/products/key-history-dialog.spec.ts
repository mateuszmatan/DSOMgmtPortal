import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { Pipeline, PipelineKey } from '../core/models';
import { pipeline, revokedKey } from '../testing/fixtures';
import { KeyHistoryDialog } from './key-history-dialog';

describe('KeyHistoryDialog', () => {
  let fixture: ComponentFixture<KeyHistoryDialog>;
  let http: HttpTestingController;
  let issued: Pipeline[];

  const active = pipeline().activeKey!;
  const revoked = revokedKey();
  const fresh: PipelineKey = {
    ...active,
    id: 1001,
    value: 'd'.repeat(36),
    hint: 'dddddddd…dddd',
    issuedAt: '2026-10-05T09:00:00Z',
  };
  const invalidated = () => pipeline({ activeKey: null, keys: [revoked] });

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [KeyHistoryDialog],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MAT_DIALOG_DATA, useValue: pipeline() },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(KeyHistoryDialog);
    issued = [];
    fixture.componentInstance.keyIssued.subscribe((updated) => issued.push(updated));
  });

  afterEach(() => http.verify());

  const page = () => fixture.nativeElement as HTMLElement;
  const cells = (column: string) =>
    [...page().querySelectorAll(`td.mat-column-${column}`)].map((cell) => cell.textContent?.trim());
  const button = (label: string) =>
    [...page().querySelectorAll<HTMLButtonElement>('button')].find(
      (element) => element.textContent?.trim() === label,
    );

  async function open(loaded: Pipeline) {
    fixture.detectChanges();
    http.expectOne('/api/pipelines/100').flush(loaded);
    await fixture.whenStable();
  }

  it('lists every key by its hint and never shows a key value', async () => {
    await open(pipeline({ keys: [active, revoked] }));

    expect(page().querySelector('.intro')?.textContent).toContain('Full pipeline of');
    expect(cells('key')).toEqual(['6f1c2d3e…9abc', '1a2b3c4d…eeff']);
    expect(page().textContent).not.toContain(active.value!);
    expect(cells('status')).toEqual(['Active', 'Invalidated']);
    expect(cells('lastUsedAt')[0]).toBe('Never');
    expect(cells('revoked')[0]).toBe('–');
    expect(cells('revoked')[1]).toContain('Leaked in a build log');
    expect(button('Regenerate key')).toBeUndefined();
  });

  it('shows why the history could not be read', async () => {
    fixture.detectChanges();
    http
      .expectOne('/api/pipelines/100')
      .flush({ detail: 'Pipeline 100 was not found' }, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();

    expect(page().querySelector('.banner')?.textContent).toBe('Pipeline 100 was not found');
    expect(page().querySelector('table')).toBeNull();
  });

  it('shows an empty table when the pipeline has no key history', async () => {
    await open(pipeline({ keys: null }));

    expect(cells('key')).toEqual([]);
  });

  it('offers to regenerate the key of a pipeline whose key was invalidated', async () => {
    await open(invalidated());

    expect(page().querySelector('.key-status')?.textContent).toContain('Key invalidated');
    expect(button('Regenerate key')).toBeDefined();
    expect(page().querySelector('mat-icon')).toBeNull();
  });

  it('regenerates the key, shows the new one and keeps the old key in the history', async () => {
    await open(invalidated());

    button('Regenerate key')!.click();
    await fixture.whenStable();
    expect(button('Regenerating…')?.disabled).toBe(true);
    const updated = pipeline({ activeKey: fresh, keys: [fresh, revoked] });
    http.expectOne({ method: 'POST', url: '/api/pipelines/100/keys' }).flush(updated);
    await fixture.whenStable();

    expect(page().querySelector('.key-status .key-value')?.textContent).toBe('d'.repeat(36));
    expect(cells('key')).toEqual(['dddddddd…dddd', '1a2b3c4d…eeff']);
    expect(cells('status')).toEqual(['Active', 'Invalidated']);
    expect(button('Regenerate key')).toBeUndefined();
    expect(issued).toEqual([updated]);
  });

  it('reads the history again when the new key comes without it', async () => {
    await open(invalidated());

    button('Regenerate key')!.click();
    http
      .expectOne({ method: 'POST', url: '/api/pipelines/100/keys' })
      .flush(pipeline({ activeKey: fresh, keys: null }));
    TestBed.tick();
    http
      .expectOne('/api/pipelines/100')
      .flush(pipeline({ activeKey: fresh, keys: [fresh, revoked] }));
    await fixture.whenStable();

    expect(cells('key')).toEqual(['dddddddd…dddd', '1a2b3c4d…eeff']);
    expect(page().querySelector('.key-status .key-value')?.textContent).toBe('d'.repeat(36));
  });

  it('shows why the key could not be regenerated and lets it be tried again', async () => {
    await open(invalidated());

    button('Regenerate key')!.click();
    http
      .expectOne('/api/pipelines/100/keys')
      .flush(
        { title: 'Not Found', detail: 'Pipeline 100 was not found' },
        { status: 404, statusText: 'Not Found' },
      );
    await fixture.whenStable();

    expect(page().querySelector('.banner')?.textContent).toBe('Pipeline 100 was not found');
    expect(button('Regenerate key')?.disabled).toBe(false);
    expect(issued).toEqual([]);
  });

  it('copies the new key and says so', async () => {
    await open(invalidated());
    button('Regenerate key')!.click();
    http
      .expectOne('/api/pipelines/100/keys')
      .flush(pipeline({ activeKey: fresh, keys: [fresh, revoked] }));
    await fixture.whenStable();

    button('Copy')!.click();
    await fixture.whenStable();

    expect(document.querySelector('mat-snack-bar-container')?.textContent).toContain(
      'Key copied to the clipboard',
    );
  });
});
