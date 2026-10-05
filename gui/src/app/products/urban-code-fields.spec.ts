import { ComponentFixture, TestBed } from '@angular/core/testing';
import { GlobalSettings } from '../core/models';
import { globalSettings, service } from '../testing/fixtures';
import { ServiceForm, applyFieldProblems, createServiceForm } from './product-form-model';
import { UrbanCodeFields } from './urban-code-fields';

describe('UrbanCodeFields', () => {
  let fixture: ComponentFixture<UrbanCodeFields>;
  let form: ServiceForm;

  async function render(defaults: GlobalSettings | null = globalSettings()) {
    TestBed.configureTestingModule({ imports: [UrbanCodeFields] });
    form = createServiceForm(service());
    fixture = TestBed.createComponent(UrbanCodeFields);
    fixture.componentRef.setInput('form', form);
    fixture.componentRef.setInput('defaults', defaults);
    await fixture.whenStable();
  }

  const page = () => fixture.nativeElement as HTMLElement;
  const applications = () => [...page().querySelectorAll<HTMLElement>('.list-item')];
  const button = (root: ParentNode, label: string) =>
    [...root.querySelectorAll<HTMLButtonElement>('button')].find(
      (element) => element.textContent?.trim() === label,
    )!;

  async function click(element: HTMLElement) {
    element.click();
    await fixture.whenStable();
  }

  it('shows the stored application with its component and the global defaults', async () => {
    await render();

    expect(applications().length).toBe(1);
    expect(applications()[0].querySelector('strong')?.textContent).toBe('CERT-GUI');
    expect(page().querySelector('.count')?.textContent).toBe('1');
    expect(applications()[0].querySelectorAll('.component').length).toBe(1);
    expect(page().textContent).toContain('deploy.vm.dod.siteName · left empty:');
  });

  it('adds and removes applications and components', async () => {
    await render(null);

    await click(button(page(), 'Add application'));
    expect(applications().length).toBe(2);
    expect(applications()[1].querySelector('strong')?.textContent).toBe('New application');
    expect(form.dirty).toBe(true);

    await click(button(applications()[1], 'Add component'));
    expect(applications()[1].querySelectorAll('.component').length).toBe(2);

    const removeComponent =
      applications()[1].querySelector<HTMLButtonElement>('.component .remove')!;
    expect(removeComponent.getAttribute('aria-label')).toBe('Remove the new component');
    await click(removeComponent);
    await click(applications()[1].querySelector<HTMLButtonElement>('.component .remove')!);

    const empty = applications()[1].querySelector('.list-empty')!;
    expect(empty.textContent?.trim()).toBe('No component yet. Add at least one.');
    expect(empty.classList).not.toContain('missing');
    form.markAllAsTouched();
    await fixture.whenStable();
    expect(empty.classList).toContain('missing');
    expect(form.controls.urbanCodeApplications.at(1).controls.components.errors).toEqual({
      rule: 'Add at least one component',
    });

    await click(button(applications()[1], 'Remove'));
    await click(button(applications()[0], 'Remove'));
    expect(page().querySelector('.list-empty')?.textContent).toContain('No application');
    expect(page().textContent).toContain('left empty: the global default');
  });

  it('shows a problem the API reports for the whole list', async () => {
    await render();

    applyFieldProblems(form, [
      { field: 'urbanCodeApplications', message: 'application names must be unique' },
    ]);
    fixture.componentRef.changeDetectorRef.markForCheck();
    await fixture.whenStable();

    expect(page().querySelector('.list-error')?.textContent).toBe(
      'application names must be unique',
    );
  });
});
