import { ComponentFixture, TestBed } from '@angular/core/testing';
import { GlobalSettings, TestJob } from '../core/models';
import { applyFieldProblems } from '../shared/form-controls';
import { globalSettings, service } from '../testing/fixtures';
import { ServiceForm, createServiceForm } from './product-form-model';
import { TestJobsFields } from './test-jobs-fields';

describe('TestJobsFields', () => {
  let fixture: ComponentFixture<TestJobsFields>;
  let form: ServiceForm;

  const job = (overrides: Partial<TestJob>): TestJob => ({
    stage: 'SMOKE',
    name: null,
    type: 'LOCAL',
    job: 'CERT/gui-smoke',
    timeoutMinutes: null,
    parameters: null,
    remoteJenkins: null,
    remoteJenkinsUrl: null,
    credentialsId: null,
    ...overrides,
  });

  async function render(jobs: TestJob[], defaults: GlobalSettings | null = globalSettings()) {
    TestBed.configureTestingModule({ imports: [TestJobsFields] });
    form = createServiceForm(service({ testJobs: jobs }));
    fixture = TestBed.createComponent(TestJobsFields);
    fixture.componentRef.setInput('form', form);
    fixture.componentRef.setInput('defaults', defaults);
    await fixture.whenStable();
  }

  const page = () => fixture.nativeElement as HTMLElement;
  const sections = () => [...page().querySelectorAll<HTMLElement>('.list-section')];
  const names = () =>
    form.controls.testJobs.controls.map(
      (control) => control.controls.name.value || control.controls.job.value,
    );
  const button = (root: ParentNode, label: string) =>
    [...root.querySelectorAll<HTMLButtonElement>('button')].find(
      (element) => element.textContent?.trim() === label,
    )!;

  it('takes the parameters as one NAME=value per line in a text area', async () => {
    await render([job({ stage: 'REGRESSION', parameters: 'ENV=rd\nSUITE=critical' })]);

    const textarea = page().querySelector<HTMLTextAreaElement>(
      'textarea[formControlName=parameters]',
    )!;
    expect(textarea.value).toBe('ENV=rd\nSUITE=critical');
    expect(textarea.closest('mat-form-field')?.textContent).toContain(
      'parameters · One NAME=value per line',
    );

    textarea.value = 'ENV=rd\nSUITE critical';
    textarea.dispatchEvent(new Event('input'));
    textarea.dispatchEvent(new Event('blur'));
    await fixture.whenStable();

    expect(textarea.closest('mat-form-field')?.textContent).toContain(
      'Write each parameter as NAME=value: SUITE critical',
    );
  });

  it('lists the jobs of each stage with their counts and parallel limits', async () => {
    await render([
      job({ name: 'smoke' }),
      job({ stage: 'REGRESSION', name: 'regression' }),
      job({ stage: 'REGRESSION', name: 'nightly' }),
    ]);

    expect(sections().map((section) => section.querySelector('h4')?.textContent?.trim())).toEqual([
      'Smoke tests 1',
      'Regression tests 2',
      'Performance tests 0',
    ]);
    expect(sections()[2].querySelector('.list-empty')?.textContent).toBe(
      'No performance test jobs.',
    );
    expect(page().textContent).toContain('tests.maxParallel · left empty: global default 20');
  });

  it('adds, moves and removes jobs within a stage', async () => {
    await render([
      job({ stage: 'REGRESSION', name: 'first' }),
      job({ stage: 'SMOKE', name: 'smoke' }),
      job({ stage: 'REGRESSION', name: 'second' }),
    ]);

    button(sections()[1], 'Down').click();
    await fixture.whenStable();
    expect(names()).toEqual(['smoke', 'second', 'first']);
    expect(form.dirty).toBe(true);

    button(sections()[1], 'Add regression job').click();
    await fixture.whenStable();
    expect(form.controls.testJobs.length).toBe(4);
    expect(sections()[1].querySelectorAll('.list-item').length).toBe(3);

    button(sections()[1], 'Remove').click();
    await fixture.whenStable();
    expect(names()).toEqual(['smoke', 'first', '']);
  });

  it('moves a job up past the jobs of other stages', async () => {
    await render([
      job({ stage: 'REGRESSION', name: 'first' }),
      job({ stage: 'SMOKE', name: 'smoke' }),
      job({ stage: 'REGRESSION', name: 'second' }),
    ]);

    const second = sections()[1].querySelectorAll('.list-item')[1];
    button(second, 'Up').click();
    await fixture.whenStable();

    expect(names()).toEqual(['second', 'first', 'smoke']);
    expect(
      [...sections()[1].querySelectorAll('.list-item strong')].map((name) => name.textContent),
    ).toEqual(['second', 'first']);
  });

  it('shows a problem the API reported for the whole list of jobs', async () => {
    await render([job({ name: 'smoke' })]);

    applyFieldProblems(form, [{ field: 'testJobs', message: 'Too long: at most 4000 characters' }]);
    fixture.changeDetectorRef.markForCheck();
    await fixture.whenStable();

    expect(page().querySelector('.list-error[role=alert]')?.textContent?.trim()).toBe(
      'Too long: at most 4000 characters',
    );
  });

  it('keeps the first job from moving up and the last from moving down', async () => {
    await render([job({ name: 'only' })]);

    const item = sections()[0].querySelector('.list-item')!;
    expect(button(item, 'Up').disabled).toBe(true);
    expect(button(item, 'Down').disabled).toBe(true);
    expect(item.querySelector('strong')?.textContent).toBe('only');
  });

  it('shows the remote Jenkins fields only for a remote job', async () => {
    await render([
      job({ name: 'local' }),
      job({ name: 'remote', type: 'REMOTE', remoteJenkins: 'qa' }),
    ]);

    const items = [...page().querySelectorAll('.list-item')];
    expect(items[0].querySelector('[formControlName=remoteJenkins]')).toBeNull();
    expect(items[1].querySelector<HTMLInputElement>('[formControlName=remoteJenkins]')?.value).toBe(
      'qa',
    );
  });

  it('names the job field after a job given as a URL', async () => {
    await render([job({ job: 'https://jenkins-qa.bbh.com/job/smoke/' })], null);

    expect(page().querySelector('.list-item')?.textContent).toContain('url · a job path');
    expect(page().textContent).toContain('left empty: global default');
  });
});
