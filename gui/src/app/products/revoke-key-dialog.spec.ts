import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { pipeline } from '../testing/fixtures';
import { RevokeKeyDialog } from './revoke-key-dialog';

describe('RevokeKeyDialog', () => {
  let fixture: ComponentFixture<RevokeKeyDialog>;
  let http: HttpTestingController;
  const close = vi.fn();

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [RevokeKeyDialog],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MAT_DIALOG_DATA, useValue: pipeline() },
        { provide: MatDialogRef, useValue: { close } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(RevokeKeyDialog);
    await fixture.whenStable();
  });

  afterEach(() => {
    http.verify();
    close.mockReset();
  });

  const page = () => fixture.nativeElement as HTMLElement;
  const reason = () => page().querySelector<HTMLTextAreaElement>('textarea')!;

  async function submit(text: string) {
    reason().value = text;
    reason().dispatchEvent(new Event('input'));
    page().querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await fixture.whenStable();
  }

  it('names the pipeline and asks for a reason first', async () => {
    expect(page().querySelector('.banner')?.textContent).toContain('full pipeline of');
    expect(page().querySelector('.banner strong')?.textContent).toBe('gui');

    await submit('');

    http.expectNone('/api/pipelines/100/keys/revoke');
    expect(page().querySelector('mat-error')?.textContent).toBe('Required');
  });

  it('invalidates the key with the trimmed reason and closes with the updated pipeline', async () => {
    await submit('  Leaked in a build log ');

    const request = http.expectOne({ method: 'POST', url: '/api/pipelines/100/keys/revoke' });
    expect(request.request.body).toEqual({ reason: 'Leaked in a build log' });
    const updated = pipeline({ activeKey: null });
    request.flush(updated);

    expect(close).toHaveBeenCalledWith(updated);
  });

  it('shows why the key could not be invalidated', async () => {
    await submit('Service retired');

    http
      .expectOne('/api/pipelines/100/keys/revoke')
      .flush({ detail: 'The key is already invalidated' }, { status: 409, statusText: 'Conflict' });
    await fixture.whenStable();

    expect(page().querySelector('[role=alert]')?.textContent).toBe(
      'The key is already invalidated',
    );
    expect(close).not.toHaveBeenCalled();
  });
});
