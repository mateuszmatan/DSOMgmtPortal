import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { BuildTool } from '../core/models';
import { Field, Fields, area, check, line, mono } from '../shared/fields';
import { ToolCommandForm } from './product-form-model';

@Component({
  selector: 'dso-tool-command-fields',
  imports: [Fields],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `<dso-fields [group]="group()" [fields]="fields()" />`,
  styles: `
    :host {
      display: contents;
    }
  `,
})
export class ToolCommandFields {
  readonly group = input.required<ToolCommandForm>();
  readonly tool = input.required<BuildTool>();
  readonly path = input.required<string>();
  readonly gradleExample = input('');
  readonly mavenExample = input('');

  private readonly maven = computed(() => this.tool() === 'MAVEN');
  private readonly key = computed(() => `${this.path()}.${this.maven() ? 'maven' : 'gradle'}`);

  protected fields(): Field[] {
    const maven = this.maven();
    const key = this.key();
    return [
      mono(
        'tasks',
        maven ? 'Maven goals' : 'Gradle tasks',
        `${key}.${maven ? 'goals' : 'tasks'}`,
        6,
        {
          placeholder: maven ? this.mavenExample() : this.gradleExample(),
          hint: 'separated by spaces',
        },
      ),
      mono('directory', 'Directory', `${key}.dir`, maven ? 3 : 6, { placeholder: '.' }),
      ...(maven
        ? [mono('mavenHome', 'Maven home', `${key}.mvnPath`, 3, { placeholder: '/opt/maven' })]
        : []),
      area('flags', 'Flags', `${key}.flags`, 6, {
        mono: true,
        placeholder: maven ? '-B' : '--refresh-dependencies',
        hint: 'one per line',
      }),
      area('environment', 'Environment variables', `${key}.env`, 6, {
        mono: true,
        placeholder: 'JAVA_OPTS=-Xmx1g',
        hint: 'one NAME=value per line',
      }),
      line('label', 'Step label', `${key}.label`, 6, {
        hint: 'the name Jenkins shows for the step',
      }),
      check('returnStdout', 'Return the output to the pipeline', `${key}.returnStdout`, 6),
    ];
  }
}
