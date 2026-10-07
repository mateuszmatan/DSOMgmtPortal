import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BarChart, BarRow } from './bar-chart';

describe('BarChart', () => {
  let fixture: ComponentFixture<BarChart>;

  async function render(rows: BarRow[]) {
    fixture = TestBed.createComponent(BarChart);
    fixture.componentRef.setInput('rows', rows);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('sizes every bar against the largest row and skips empty segments', async () => {
    const page = await render([
      {
        label: 'Custody',
        note: '8 pipelines',
        segments: [
          { swatch: 'success', label: 'success', count: 6 },
          { swatch: 'failure', label: 'failed', count: 2 },
        ],
      },
      {
        label: 'AI Lab',
        note: '2 pipelines',
        segments: [
          { swatch: 'success', label: 'success', count: 2 },
          { swatch: 'failure', label: 'failed', count: 0 },
        ],
      },
    ]);

    const widths = [...page.querySelectorAll<HTMLElement>('.track')].map((track) =>
      [...track.querySelectorAll<HTMLElement>('.swatch')].map((bar) => bar.style.width),
    );
    expect(widths).toEqual([['75%', '25%'], ['25%']]);
    expect(page.querySelector('.track')?.getAttribute('aria-label')).toBe(
      'Custody: 6 success, 2 failed',
    );
    expect(page.querySelectorAll('.swatch.failure').length).toBe(1);
  });

  it('names a row without any count', async () => {
    const page = await render([{ label: 'Fund Services', note: '0 pipelines', segments: [] }]);

    expect(page.querySelector('.track')?.getAttribute('aria-label')).toBe('Fund Services: none');
    expect(page.querySelector('.note')?.textContent).toBe('0 pipelines');
  });
});
