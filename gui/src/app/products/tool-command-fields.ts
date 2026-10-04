import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { BuildTool } from '../core/models';
import { errorText } from '../shared/form-errors';
import { ToolCommandForm } from './product-form-model';

@Component({
  selector: 'dso-tool-command-fields',
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    @let g = group();
    <mat-form-field class="span-6">
      <mat-label>{{ maven() ? 'Maven goals' : 'Gradle tasks' }}</mat-label>
      <input
        matInput
        [formControl]="g.controls.tasks"
        [placeholder]="maven() ? mavenExample() : gradleExample()"
        class="mono"
        autocomplete="off"
      />
      <mat-hint
        ><code>{{ key() }}.{{ maven() ? 'goals' : 'tasks' }}</code> · separated by spaces</mat-hint
      >
      <mat-error>{{ errorText(g.controls.tasks) }}</mat-error>
    </mat-form-field>
    <mat-form-field [class]="maven() ? 'span-3' : 'span-6'">
      <mat-label>Directory</mat-label>
      <input matInput [formControl]="g.controls.directory" placeholder="." class="mono" />
      <mat-hint
        ><code>{{ key() }}.dir</code></mat-hint
      >
      <mat-error>{{ errorText(g.controls.directory) }}</mat-error>
    </mat-form-field>
    @if (maven()) {
      <mat-form-field class="span-3">
        <mat-label>Maven home</mat-label>
        <input
          matInput
          [formControl]="g.controls.mavenHome"
          placeholder="/opt/maven"
          class="mono"
        />
        <mat-hint
          ><code>{{ key() }}.mvnPath</code></mat-hint
        >
        <mat-error>{{ errorText(g.controls.mavenHome) }}</mat-error>
      </mat-form-field>
    }
    <mat-form-field class="span-6">
      <mat-label>Flags</mat-label>
      <textarea
        matInput
        [formControl]="g.controls.flags"
        rows="2"
        class="mono"
        spellcheck="false"
        [placeholder]="maven() ? '-B' : '--refresh-dependencies'"
      ></textarea>
      <mat-hint
        ><code>{{ key() }}.flags</code> · one per line</mat-hint
      >
      <mat-error>{{ errorText(g.controls.flags) }}</mat-error>
    </mat-form-field>
    <mat-form-field class="span-6">
      <mat-label>Environment variables</mat-label>
      <textarea
        matInput
        [formControl]="g.controls.environment"
        rows="2"
        class="mono"
        spellcheck="false"
        placeholder="JAVA_OPTS=-Xmx1g"
      ></textarea>
      <mat-hint
        ><code>{{ key() }}.env</code> · one NAME=value per line</mat-hint
      >
      <mat-error>{{ errorText(g.controls.environment) }}</mat-error>
    </mat-form-field>
  `,
  styles: `
    :host {
      display: contents;
    }
    code {
      font-size: 11.5px;
    }
  `,
})
export class ToolCommandFields {
  readonly group = input.required<ToolCommandForm>();
  readonly tool = input.required<BuildTool>();
  readonly path = input.required<string>();
  readonly gradleExample = input('');
  readonly mavenExample = input('');

  protected readonly maven = computed(() => this.tool() === 'MAVEN');
  protected readonly key = computed(() => `${this.path()}.${this.maven() ? 'maven' : 'gradle'}`);
  protected readonly errorText = errorText;
}
