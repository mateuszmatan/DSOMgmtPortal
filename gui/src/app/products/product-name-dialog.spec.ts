import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { inputOf } from '../testing/dom';
import { ProductNameDialog } from './product-name-dialog';

describe('ProductNameDialog', () => {
  let fixture: ComponentFixture<ProductNameDialog>;
  const close = vi.fn();

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [ProductNameDialog],
      providers: [{ provide: MatDialogRef, useValue: { close } }],
    });
    fixture = TestBed.createComponent(ProductNameDialog);
    await fixture.whenStable();
  });

  afterEach(() => close.mockReset());

  const page = () => fixture.nativeElement as HTMLElement;

  async function submit(name: string) {
    const input = inputOf(page(), 'Product name');
    input.value = name;
    input.dispatchEvent(new Event('input'));
    page().querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await fixture.whenStable();
  }

  it('continues with the trimmed name', async () => {
    await submit('  CertScanner ');

    expect(close).toHaveBeenCalledWith('CertScanner');
  });

  it('asks again when the name is blank', async () => {
    await submit('   ');

    expect(close).not.toHaveBeenCalled();
    expect(page().querySelector('mat-error')?.textContent).toBe('Required');
  });
});
