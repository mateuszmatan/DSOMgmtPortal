import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { inputOf, text } from '../testing/dom';
import { department } from '../testing/fixtures';
import { ProductNameDialog, ProductNameDialogData } from './product-name-dialog';

describe('ProductNameDialog', () => {
  let fixture: ComponentFixture<ProductNameDialog>;
  const close = vi.fn();
  const departments = [department(), department({ id: 5, name: 'Fund Services' })];

  async function render(departmentId: number | null = null) {
    TestBed.configureTestingModule({
      imports: [ProductNameDialog],
      providers: [
        {
          provide: MAT_DIALOG_DATA,
          useValue: { departments, departmentId } satisfies ProductNameDialogData,
        },
        { provide: MatDialogRef, useValue: { close } },
      ],
    });
    fixture = TestBed.createComponent(ProductNameDialog);
    await fixture.whenStable();
  }

  afterEach(() => close.mockReset());

  const page = () => fixture.nativeElement as HTMLElement;
  const departmentId = () => fixture.componentInstance['form'].controls.departmentId;

  async function submit(name: string) {
    const input = inputOf(page(), 'Product name');
    input.value = name;
    input.dispatchEvent(new Event('input'));
    page().querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await fixture.whenStable();
  }

  it('continues with the department and the trimmed name', async () => {
    await render(5);
    expect(departmentId().value).toBe(5);

    await submit('  CertScanner ');

    expect(close).toHaveBeenCalledWith({ name: 'CertScanner', departmentId: 5 });
  });

  it('asks for the department first, ignoring an unknown one', async () => {
    await render(42);

    await submit('CertScanner');

    expect(departmentId().value).toBeNull();
    expect(close).not.toHaveBeenCalled();
    expect(text(page().querySelector('mat-error'))).toBe('Required');

    departmentId().setValue(3);
    await submit('CertScanner');

    expect(close).toHaveBeenCalledWith({ name: 'CertScanner', departmentId: 3 });
  });

  it('asks again when the name is blank', async () => {
    await render(3);

    await submit('   ');

    expect(close).not.toHaveBeenCalled();
    expect(page().querySelector('mat-error')?.textContent).toBe('Required');
  });
});
