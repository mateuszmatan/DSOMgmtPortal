import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { PipelineKey } from '../core/models';
import { pipeline } from '../testing/fixtures';
import { KeyHistoryDialog } from './key-history-dialog';

describe('KeyHistoryDialog', () => {
  let fixture: ComponentFixture<KeyHistoryDialog>;
  let http: HttpTestingController;

  const active = pipeline().activeKey!;
  const revoked: PipelineKey = {
    id: 999,
    value: null,
    hint: '1a2b3c4d…eeff',
    status: 'REVOKED',
    issuedAt: '2026-09-01T08:00:00Z',
    revokedAt: '2026-10-01T09:30:00Z',
    revokeReason: 'Leaked in a build log',
    lastUsedAt: '2026-09-30T10:00:00Z',
  };

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
  });

  afterEach(() => http.verify());

  const page = () => fixture.nativeElement as HTMLElement;
  const cells = (column: string) =>
    [...page().querySelectorAll(`td.mat-column-${column}`)].map((cell) => cell.textContent?.trim());

  it('lists every key by its hint and never shows a key value', async () => {
    fixture.detectChanges();
    http.expectOne('/api/pipelines/100').flush(pipeline({ keys: [active, revoked] }));
    await fixture.whenStable();

    expect(page().querySelector('.intro')?.textContent).toContain('Full pipeline of');
    expect(cells('key')).toEqual(['6f1c2d3e…9abc', '1a2b3c4d…eeff']);
    expect(page().textContent).not.toContain(active.value!);
    expect(cells('status')).toEqual(['Active', 'Invalidated']);
    expect(cells('lastUsedAt')[0]).toBe('Never');
    expect(cells('revoked')[0]).toBe('–');
    expect(cells('revoked')[1]).toContain('Leaked in a build log');
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
    fixture.detectChanges();
    http.expectOne('/api/pipelines/100').flush(pipeline({ keys: null }));
    await fixture.whenStable();

    expect(cells('key')).toEqual([]);
  });
});
