import { ComponentFixture, TestBed } from '@angular/core/testing';
import { service } from '../testing/fixtures';
import { OpenShiftTargetFields } from './openshift-target-fields';
import { ServiceForm, createServiceForm } from './product-form-model';

describe('OpenShiftTargetFields', () => {
  let fixture: ComponentFixture<OpenShiftTargetFields>;
  let form: ServiceForm;

  beforeEach(async () => {
    TestBed.configureTestingModule({ imports: [OpenShiftTargetFields] });
    form = createServiceForm(
      service({ deployment: { ...service().deployment, target: 'OPENSHIFT' } }),
    );
    fixture = TestBed.createComponent(OpenShiftTargetFields);
    fixture.componentRef.setInput('form', form);
    await fixture.whenStable();
  });

  const page = () => fixture.nativeElement as HTMLElement;
  const headings = () =>
    [...page().querySelectorAll('.sub-heading')].map((heading) => heading.textContent?.trim());
  const toggle = (label: string) =>
    [...page().querySelectorAll<HTMLButtonElement>('mat-button-toggle button')].find((button) =>
      button.textContent?.includes(label),
    )!;

  const field = (label: string) =>
    [...page().querySelectorAll<HTMLElement>('mat-form-field')].find(
      (element) => element.querySelector('mat-label')?.textContent === label,
    )!;

  async function choose(label: string) {
    toggle(label).click();
    await fixture.whenStable();
  }

  it('asks for the image build of the RD region and marks it until it is filled', async () => {
    expect(page().querySelector('.region-note')?.textContent).toContain('deploy.openshift.rd');
    expect(headings()).toEqual([
      'Image build',
      'Image registry',
      'Deployment',
      'Deployment repository',
    ]);
    expect(toggle('RD region').querySelector('.problem-mark')).not.toBeNull();

    const projectBuild = field('Build project');
    projectBuild.querySelector('input')!.dispatchEvent(new Event('blur'));
    await fixture.whenStable();
    expect(projectBuild.querySelector('mat-error')?.textContent).toBe('Required');
    expect(field('File added to the image').querySelector('mat-error')).toBeNull();

    form.controls.openShiftTargets.controls.RD.patchValue({
      projectBuild: 'cert-build',
      buildConfigPath: 'openshift/buildconfig.yaml',
      dockerFilePath: 'openshift/Dockerfile',
      buildContext: 'target/docker',
      dockerRepoPush: 'nexus.bbh.com:18444/cert',
      nexusAuthFile: '/etc/containers/auth.json',
    });
    await fixture.whenStable();
    expect(toggle('RD region').querySelector('.problem-mark')).toBeNull();
  });

  it('shows the image build of the QC region only once one of its fields is set', async () => {
    await choose('QC region');

    expect(page().querySelector('.region-note')?.textContent).toContain('deploy.openshift.qc');
    expect(headings()).not.toContain('Image build');

    form.controls.openShiftTargets.controls.QC.controls.dockerFilePath.setValue('Dockerfile.qc');
    await choose('RD region');
    await choose('QC region');

    expect(headings()).toContain('Image build');
  });

  it('explains a deployment repository that is not a URL', async () => {
    const url = form.controls.openShiftTargets.controls.RD.controls.deploymentRepoUrl;
    url.setValue('not a url');
    url.markAsTouched();
    await fixture.whenStable();

    expect(page().textContent).toContain('Must be an http, https, ssh or git@ URL');
  });
});
