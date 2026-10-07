import { ChangeDetectionStrategy, Component, input, model } from '@angular/core';

export interface Choice<T> {
  value: T;
  label: string;
  description: string;
  points?: readonly string[];
}

@Component({
  selector: 'dso-choice-tiles',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      class="tiles"
      role="radiogroup"
      [attr.aria-label]="label()"
      [style.--columns]="options().length"
    >
      @for (option of options(); track option.value) {
        <button
          type="button"
          role="radio"
          class="tile"
          [class.selected]="option.value === value()"
          [attr.aria-checked]="option.value === value()"
          [attr.aria-label]="option.label"
          [attr.aria-description]="option.description"
          (click)="value.set(option.value)"
        >
          <span class="tile-label">{{ option.label }}</span>
          <span class="tile-description">{{ option.description }}</span>
          @if (option.points?.length) {
            <ul>
              @for (point of option.points; track point) {
                <li>{{ point }}</li>
              }
            </ul>
          }
        </button>
      }
    </div>
  `,
  styles: `
    .tiles {
      display: grid;
      grid-template-columns: repeat(var(--columns), minmax(0, 1fr));
      gap: 10px;
    }

    .tile {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      gap: 3px;
      padding: 10px 12px;
      border: 1px solid var(--dso-border);
      background: var(--dso-card);
      color: inherit;
      font: inherit;
      text-align: left;
      cursor: pointer;

      &:hover {
        border-color: var(--dso-navy-light);
      }

      &:focus-visible {
        outline: 2px solid var(--dso-navy);
        outline-offset: 1px;
      }

      &.selected {
        border-color: var(--dso-navy);
        box-shadow: inset 0 0 0 1px var(--dso-navy);
        background: var(--dso-info-bg);
      }
    }

    .tile-label {
      font-size: 14px;
      font-weight: 600;
      color: var(--dso-navy);
    }

    .tile-description {
      color: var(--dso-muted);
      font-size: 12px;
    }

    ul {
      margin: 6px 0 0;
      padding-left: 16px;
      font-size: 12px;
      list-style: square;
    }

    @media (max-width: 700px) {
      .tiles {
        grid-template-columns: minmax(0, 1fr);
      }
    }
  `,
})
export class ChoiceTiles<T> {
  readonly options = input.required<readonly Choice<T>[]>();
  readonly label = input.required<string>();
  readonly value = model<T | null>(null);
}
