import { doraSummary } from '../testing/fixtures';
import { doraTiles } from './pipeline-monitoring';

describe('doraTiles', () => {
  it('shows the four DORA metrics and that they are measured over deployments', () => {
    const tiles = doraTiles(doraSummary());

    expect(tiles.map((tile) => [tile.title, tile.value, tile.level])).toEqual([
      ['Deployment frequency', '2.8 / week', 'HIGH'],
      ['Lead time for changes', '1h 30m', 'ELITE'],
      ['Change failure rate', '12.5%', 'HIGH'],
      ['Time to restore', '2h', 'HIGH'],
    ]);
    expect(tiles[0].detail).toBe('12 deployments in 30 days');
    expect(tiles[2].detail).toBe('Of 12 deployments in the range');
    expect(tiles[3].detail).toBe('Mean of 3 recoveries from a failed deployment');
    expect(tiles[3].alert).toBeUndefined();
  });

  it('switches the frequency unit to days or months', () => {
    expect(doraTiles(doraSummary({ deploymentsPerWeek: 14 }))[0].value).toBe('2.0 / day');
    expect(doraTiles(doraSummary({ deploymentsPerWeek: 0.5 }))[0].value).toBe('2.1 / month');
  });

  it('shows dashes without data and warns while the pipeline is failing', () => {
    const tiles = doraTiles(
      doraSummary({
        runs: 1,
        deployments: 1,
        deploymentsPerWeek: null,
        leadTimeMedianSeconds: null,
        changeFailureRatePercent: null,
        meanTimeToRestoreSeconds: null,
        restores: 1,
        failingSince: '2026-10-04T08:00:00Z',
      }),
    );

    expect(tiles.map((tile) => tile.value)).toEqual(['–', '–', '–', '–']);
    expect(tiles[0].detail).toBe('1 deployment in 30 days');
    expect(tiles[2].detail).toBe('Of 1 deployment in the range');
    expect(tiles[3].detail).toBe('Mean of 1 recovery from a failed deployment');
    expect(tiles[3].alert).toMatch(/^Failing since 4 Oct, \d\d:00$/);
  });
});
