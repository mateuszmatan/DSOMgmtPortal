import { ComponentFixture, TestBed } from '@angular/core/testing';
import { GlobalSettings, Service } from '../core/models';
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
  const hints = () => [...pane().querySelectorAll('mat-hint')].map((hint) => hint.textContent);

  async function open(label: string) {
    const item = [...page().querySelectorAll<HTMLButtonElement>('.rail-item')].find(
      (button) => button.querySelector('span')?.textContent?.trim() === label,
    );
    item!.click();
    await fixture.whenStable();
  }

  async function chooseOption(trigger: HTMLElement, label: string) {
    trigger.click();
    await fixture.whenStable();
    const option = [...document.querySelectorAll<HTMLElement>('mat-option')].find(
      (element) => element.textContent?.trim() === label,
    );
    option!.click();
    await fixture.whenStable();
  }

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
      await render(service(), { ...settings, goldenFix: { ...settings.goldenFix, enabled: false } });
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
        'The service follows the GoldenFix defaults of the DevSecOps Global Settings.',
      );
    });
  });
});
