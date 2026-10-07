import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AbstractControl, FormArray, FormGroup } from '@angular/forms';
import { BuildTool, DeployTarget, GlobalSettings, Service } from '../core/models';
import { wholeNumber } from '../shared/form-controls';
import { buttonOf, checkboxOf, fieldOf, text } from '../testing/dom';
import { globalSettings, service } from '../testing/fixtures';
import { ServiceForm, createServiceForm } from './product-form-model';
import { ServiceFields } from './service-fields';

describe('ServiceFields', () => {
  let fixture: ComponentFixture<ServiceFields>;
  let form: ServiceForm;

  async function render(
    stored: Partial<Service> | undefined = service(),
    defaults: GlobalSettings | null = globalSettings(),
  ) {
    TestBed.configureTestingModule({ imports: [ServiceFields] });
    form = createServiceForm(stored);
    fixture = TestBed.createComponent(ServiceFields);
    fixture.componentRef.setInput('form', form);
    fixture.componentRef.setInput('productCode', 'CERT');
    fixture.componentRef.setInput('defaults', defaults);
    await fixture.whenStable();
  }

  const page = () => fixture.nativeElement as HTMLElement;
  const pane = () => page().querySelector<HTMLElement>('.pane')!;
  const hints = () => [...pane().querySelectorAll('mat-hint')].map((hint) => text(hint));

  async function open(label: string) {
    const item = [...page().querySelectorAll<HTMLButtonElement>('.rail-item')].find(
      (button) => button.querySelector('span')?.textContent?.trim() === label,
    );
    item!.click();
    await fixture.whenStable();
  }

  const railLabels = () =>
    [...page().querySelectorAll('.rail-item span:not(.problem-mark)')].map(
      (label) => label.textContent?.trim() ?? '',
    );

  const SELECTS = new Set(['tool', 'target', 'stage', 'type', 'authType', 'platform']);

  function breakEveryField(control: AbstractControl, key = ''): void {
    if (control instanceof FormGroup || control instanceof FormArray) {
      Object.entries(control.controls).forEach(([name, child]) => breakEveryField(child, name));
    } else if (control.enabled && !SELECTS.has(key)) {
      if (typeof control.value === 'string') {
        control.setValue('x'.repeat(5001));
      } else if (control.hasValidator(wholeNumber)) {
        control.setValue(1.5);
      }
    }
  }

  function everything(tool: BuildTool, target: DeployTarget): Service {
    const stored = service();
    return service({
      build: { ...stored.build, tool },
      deployment: { ...stored.deployment, target },
      appScan: { ...stored.appScan, compile: true, dastEnabled: true },
      goldenFix: { ...stored.goldenFix, minThreatLevel: 7 },
      testJobs: [
        ...stored.testJobs,
        {
          ...stored.testJobs[0],
          stage: 'REGRESSION',
          name: 'remote',
          type: 'REMOTE',
          job: 'CERT/regression',
          parameters: 'ENV=rd',
          remoteJenkins: 'qa',
          pollIntervalSec: 20,
          tokenCredentialsId: 'qa-trigger-token',
          useCrumbCache: true,
        },
      ],
    });
  }

  const combinations: [BuildTool, DeployTarget][] = [
    ['GRADLE', 'VM'],
    ['MAVEN', 'VM'],
    ['MAVEN', 'OPENSHIFT'],
    ['FLUTTER', 'VM'],
    ['FLUTTER', 'OPENSHIFT'],
  ];

  async function chooseOption(trigger: HTMLElement, label: string) {
    trigger.click();
    await fixture.whenStable();
    const option = [...document.querySelectorAll<HTMLElement>('mat-option')].find(
      (element) => element.textContent?.trim() === label,
    );
    option!.click();
    await fixture.whenStable();
  }

  describe('every section', () => {
    it.each(combinations)(
      'opens each section of a %s service on %s',
      async (tool, target) => {
        await render(everything(tool, target));

        for (const label of railLabels()) {
          await open(label);
          expect(pane().querySelector('h3')?.textContent).toBe(label);
        }
        expect(railLabels()).toContain(target === 'VM' ? 'SSH targets' : 'OpenShift targets');
        expect(railLabels().includes('Flutter')).toBe(tool === 'FLUTTER');
      },
      20_000,
    );

    it.each(combinations)(
      'explains every problem of a %s service on %s',
      async (tool, target) => {
        await render(everything(tool, target));
        breakEveryField(form);
        form.markAllAsTouched();
        fixture.componentRef.setInput('submitted', true);
        await fixture.whenStable();

        const marked = [...page().querySelectorAll('.rail-item.problem span:first-of-type')];
        expect(marked.length).toBeGreaterThan(5);
        for (const label of railLabels()) {
          await open(label);
          const errors = [...pane().querySelectorAll('mat-error')].map((e) =>
            e.textContent?.trim(),
          );
          expect(errors.every((error) => !!error)).toBe(true);
        }
      },
      20_000,
    );

    it('works without the global settings and reveals the first section with a problem', async () => {
      await render(service(), null);
      for (const label of railLabels()) {
        await open(label);
      }
      expect(fixture.componentInstance.revealFirstProblem()).toBe(false);

      form.controls.sonar.controls.command.controls.tasks.setValue('');
      expect(fixture.componentInstance.revealFirstProblem()).toBe(true);
      await fixture.whenStable();
      expect(page().querySelector('.rail-item.active span')?.textContent).toBe('SonarQube');
    });
  });

  describe('Build', () => {
    const field = (label: string) => fieldOf(pane(), label)!;

    it('asks for the JDK of a Flutter build and turns automatic setup off', async () => {
      await render(service({ build: { ...service().build, autoSetup: true, javaPath: null } }));
      await open('Build');
      expect(field('JDK path').textContent).toContain(
        'unless the build tool is set up automatically',
      );

      form.controls.build.controls.tool.setValue('FLUTTER');
      await fixture.whenStable();
      expect(field('JDK path').textContent).toContain('JAVA_HOME of the Flutter build stages');

      form.controls.build.controls.javaPath.markAsTouched();
      await fixture.whenStable();
      expect(field('JDK path').textContent).toContain('Required');
      expect(checkboxOf(pane(), 'Set up the build tool automatically').disabled).toBe(true);
      expect(pane().querySelector('.note')?.textContent).toContain('needs the JDK path');
    });

    it('names the artifact the Nexus delivery uploads for Maven on virtual machines', async () => {
      await render();
      await open('Build');
      expect(field('Artifact path').textContent).toContain('what the build produces');

      form.controls.build.controls.tool.setValue('MAVEN');
      await fixture.whenStable();

      expect(field('Artifact path').textContent).toContain(
        'the artifact the Nexus snapshot delivery uploads',
      );
    });
  });

  describe('Flutter', () => {
    it('marks the delivery coordinates required on virtual machines only', async () => {
      await render(service({ build: { ...service().build, tool: 'FLUTTER' } }));
      await open('Flutter');
      expect(hints()).toContain('delivery.group · required on virtual machines');
      expect(hints()).toContain('tools.flutter.flutterModules · one per line, at least one');

      form.controls.deployment.controls.target.setValue('OPENSHIFT');
      await fixture.whenStable();

      expect(hints()).toContain('delivery.group');
    });
  });

  describe('Nexus IQ', () => {
    const items = () => [...pane().querySelectorAll<HTMLElement>('.list-item')];

    it('lists the applications, adds and removes them and names the global server', async () => {
      await render();
      await open('Nexus IQ');
      expect(hints()).toContain('tools.nexusIq.serverUrl · left empty: https://tools.bbh.com/IQ');
      expect(hints()).toContain('tools.nexusIq.credentialsId · left empty: nexusiqP');
      expect(items().map((item) => item.querySelector('strong')?.textContent)).toEqual([
        'cert-gui',
      ]);

      buttonOf(pane(), 'Add application').click();
      await fixture.whenStable();
      expect(items().length).toBe(2);
      expect(form.controls.nexusIqApplications.at(1).controls.stage.value).toBe('build');
      expect(form.dirty).toBe(true);

      for (const label of ['Remove the new application', 'Remove cert-gui']) {
        pane().querySelector<HTMLButtonElement>(`button[aria-label="${label}"]`)!.click();
        await fixture.whenStable();
      }
      expect(pane().querySelector('.list-empty')?.textContent).toContain('skips the Nexus IQ scan');
    });
  });

  describe('servers and credentials of the service', () => {
    it('name the global value a blank field falls back to', async () => {
      await render();
      await open('SonarQube');
      expect(hints()).toContain('tools.sonar.serverUrl · left empty: https://tools.bbh.com/sonar');
      await open('DORA metrics');
      expect(hints()).toContain('influx.credentialsId · left empty: influxdb-token');
      await open('AppScan SAST and DAST');
      expect(text(fieldOf(pane(), 'Secret text credentials ID'))).toContain(
        "left empty: the product's",
      );
    });
  });

  describe('GoldenFix', () => {
    it('offers Global default, On and Off and names the global default', async () => {
      await render();
      await open('GoldenFix');

      const select = pane().querySelector<HTMLElement>('mat-select')!;
      expect(select.textContent?.trim()).toBe('Global default');
      expect(hints()).toContain('goldenFix.enabled · Global default: on');

      select.click();
      await fixture.whenStable();
      expect(
        [...document.querySelectorAll('mat-option')].map((option) => option.textContent?.trim()),
      ).toEqual(['Global default', 'On', 'Off']);
      (document.querySelector('mat-option:last-of-type') as HTMLElement).click();
      await fixture.whenStable();

      expect(form.controls.goldenFix.controls.enabled.value).toBe(false);
    });

    it('turns GoldenFix on for the service only', async () => {
      const settings = globalSettings();
      await render(service(), {
        ...settings,
        goldenFix: { ...settings.goldenFix, enabled: false },
      });
      await open('GoldenFix');

      expect(hints()).toContain('goldenFix.enabled · Global default: off');
      await chooseOption(pane().querySelector<HTMLElement>('mat-select')!, 'On');

      expect(form.controls.goldenFix.controls.enabled.value).toBe(true);
    });

    it('describes the inherited policy or shows the fields the service overrides', async () => {
      await render();
      await open('GoldenFix');

      expect(pane().querySelector('.note')?.textContent).toContain(
        'maven, npm, pypi, threat level 2 and above, direct dependencies only, each fix verified by a build.',
      );
      form.controls.goldenFix.controls.inherit.setValue(false);
      await fixture.whenStable();

      expect(pane().querySelector('dso-golden-fix-fields')).not.toBeNull();
    });

    it('works without the global settings', async () => {
      await render(service(), null);
      await open('GoldenFix');

      expect(hints()).toContain('goldenFix.enabled');
      expect(pane().querySelector('.note')?.textContent).toBe(
        'The service follows the GoldenFix defaults of the DSOEnhanced library.',
      );
    });
  });
});
