import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { inputOf, text } from '../testing/dom';
import { WizardService } from './self-service-model';
import { ServiceDialog, ServiceDialogData } from './service-dialog';

const APP_ID = '7d1f3a52-9c4b-4e8a-b2d6-0f5e1c9a8b31';

describe('ServiceDialog', () => {
  let fixture: ComponentFixture<ServiceDialog>;
  const close = vi.fn();

  async function open(data: Partial<ServiceDialogData> = {}) {
    TestBed.configureTestingModule({
      imports: [ServiceDialog],
      providers: [
        { provide: MatDialogRef, useValue: { close } },
        {
          provide: MAT_DIALOG_DATA,
          useValue: { pipeline: 'FULL', service: null, takenNames: ['gui'], ...data },
        },
      ],
    });
    fixture = TestBed.createComponent(ServiceDialog);
    await fixture.whenStable();
  }

  afterEach(() => close.mockReset());

  const page = () => fixture.nativeElement as HTMLElement;

  async function type(label: string, value: string) {
    const input = inputOf(page(), label);
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  async function submit() {
    page().querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await fixture.whenStable();
  }

  async function choose(label: string) {
    page().querySelector<HTMLButtonElement>(`[role=radio][aria-label="${label}"]`)!.click();
    await fixture.whenStable();
  }

  const errors = () => [...page().querySelectorAll('mat-error, .choice-error')].map(text);

  it('asks about the service first and refuses a taken name and a malformed AppScan ID', async () => {
    await open();

    expect(text(page().querySelector('.page-count'))).toBe('Part 1 of 2 · About the service');

    await type('Service name', 'GUI');
    await type('AppScan application ID', 'not-an-id');
    await submit();

    expect(errors()).toEqual([
      'Another service of this product already has this name',
      'Paste the ID as it is, for example 109f44ac-cc06-4ca0-884e-d944904f7019',
    ]);
    expect(text(page().querySelector('.page-count'))).toContain('Part 1 of 2');
  });

  it('adds an OpenShift service once its build tool, platform and project are chosen', async () => {
    await open();
    await type('Service name', 'gateway');
    await type('AppScan application ID', APP_ID.toUpperCase());
    await submit();

    expect(text(page().querySelector('.page-count'))).toBe('Part 2 of 2 · Build and run');

    await submit();
    expect(errors()).toEqual(['Choose Gradle or Maven', 'Choose where the service runs']);

    await choose('Maven');
    await choose('OpenShift');
    await submit();
    expect(errors()).toEqual(['Required']);
    expect(close).not.toHaveBeenCalled();

    await type('OpenShift project', 'pay-payhub');
    await submit();

    expect(close).toHaveBeenCalledWith({
      id: null,
      name: 'gateway',
      description: '',
      appScanId: APP_ID,
      tool: 'MAVEN',
      target: 'OPENSHIFT',
      openShiftProject: 'pay-payhub',
    } satisfies WizardService);
  });

  it('asks only for the build tool of a static scan and goes back to the first part', async () => {
    await open({ pipeline: 'SAST' });
    await type('Service name', 'scanner');
    await type('AppScan application ID', APP_ID);
    await submit();

    expect(page().querySelectorAll('[role=radiogroup]')).toHaveLength(1);

    page().querySelector<HTMLButtonElement>('mat-dialog-actions button[type=button]')!.click();
    await fixture.whenStable();
    expect(inputOf(page(), 'Service name').value).toBe('scanner');

    await submit();
    await choose('Gradle');
    await submit();

    expect(close).toHaveBeenCalledWith(
      expect.objectContaining({
        name: 'scanner',
        tool: 'GRADLE',
        target: null,
        openShiftProject: '',
      }),
    );
  });

  it('changes a service of the portal in one part and leaves its build and platform alone', async () => {
    const service: WizardService = {
      id: 10,
      name: 'gui',
      description: 'Angular front end',
      appScanId: APP_ID,
      tool: 'MAVEN',
      target: 'OPENSHIFT',
      openShiftProject: '',
    };
    await open({ service, takenNames: ['api'] });

    expect(page().querySelector('.page-count')).toBeNull();
    expect(text(page().querySelector('.note'))).toContain('Built with Maven, runs on OpenShift.');

    await type('What it does', 'Web front end');
    await submit();

    expect(close).toHaveBeenCalledWith({ ...service, description: 'Web front end' });
  });
});
