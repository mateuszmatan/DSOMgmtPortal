import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DailyActivity } from '../core/models';
import { ActivityChart } from './activity-chart';

describe('ActivityChart', () => {
  let fixture: ComponentFixture<ActivityChart>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [ActivityChart] });
    fixture = TestBed.createComponent(ActivityChart);
  });

  const svg = () => (fixture.nativeElement as HTMLElement).querySelector('svg')!;
  const all = (selector: string) => [...svg().querySelectorAll(selector)];
  const attr = (element: Element, name: string) => Number(element.getAttribute(name));
  const axis = () => all('text.axis').map((text) => text.textContent?.trim());

  async function render(daily: DailyActivity[]) {
    fixture.componentRef.setInput('daily', daily);
    await fixture.whenStable();
  }

  const day = (date: string, runs: number, failures = 0, deployments = 0): DailyActivity => ({
    date,
    runs,
    failures,
    deployments,
  });

  it('stacks the failed runs on the successful ones and marks deployments', async () => {
    await render([day('2026-10-01', 3, 1, 1), day('2026-10-02', 0), day('2026-10-03', 4, 0, 2)]);

    expect(svg().getAttribute('aria-label')).toBe('7 runs over 3 days');
    expect(all('g title').map((title) => title.textContent)).toEqual([
      'Oct 1: 3 runs, 1 failed, 1 deployment',
      'Oct 2: 0 runs, 0 failed, 0 deployments',
      'Oct 3: 4 runs, 0 failed, 2 deployments',
    ]);
    expect(all('rect.hit').length).toBe(3);
    expect(all('rect.success').length).toBe(2);
    expect(all('rect.failure').length).toBe(1);
    expect(all('rect.deployment').length).toBe(2);
    const [first, third] = all('rect.success');
    const failure = all('rect.failure')[0];
    expect(attr(first, 'height')).toBeCloseTo(attr(third, 'height') / 2);
    expect(attr(failure, 'y') + attr(failure, 'height')).toBeCloseTo(attr(first, 'y'));
  });

  it('scales the axis to an even maximum and labels the first, middle and last day', async () => {
    await render([
      day('2026-10-01', 5),
      day('2026-10-02', 1),
      day('2026-10-03', 2),
      day('2026-10-04', 1),
      day('2026-10-05', 3),
    ]);

    expect(axis()).toEqual(['0', '3', '6', 'Oct 1', 'Oct 3', 'Oct 5']);
    expect(
      all('text.axis')
        .slice(3)
        .map((text) => text.getAttribute('text-anchor')),
    ).toEqual(['start', 'middle', 'end']);
  });

  it('keeps an axis of two runs for quiet days without drawing bars', async () => {
    await render([day('2026-10-01', 0), day('2026-10-02', 0)]);

    expect(axis()).toEqual(['0', '1', '2', 'Oct 1', 'Oct 2']);
    expect(all('rect.success, rect.failure, rect.deployment')).toEqual([]);
    expect(svg().getAttribute('aria-label')).toBe('0 runs over 2 days');
  });

  it('labels a single day once', async () => {
    await render([day('2026-10-05', 1)]);

    expect(axis().slice(3)).toEqual(['Oct 5']);
  });

  it('draws an empty chart when no day is reported', async () => {
    await render([]);

    expect(svg().getAttribute('aria-label')).toBe('0 runs over 0 days');
    expect(all('g')).toEqual([]);
    expect(axis()).toEqual(['0', '1', '2']);
    expect(
      [...(fixture.nativeElement as HTMLElement).querySelectorAll('.legend span')].map(
        (entry) => entry.textContent,
      ),
    ).toEqual(['Successful runs', 'Failed or unstable runs', 'Deployed that day']);
  });
});
