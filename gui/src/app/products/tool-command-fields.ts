import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { BuildTool } from '../core/models';
import { Field, Fields } from '../shared/fields';
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
      {
        key: 'tasks',
        label: maven ? 'Maven goals' : 'Gradle tasks',
        mono: true,
        placeholder: maven ? this.mavenExample() : this.gradleExample(),
        code: `${key}.${maven ? 'goals' : 'tasks'}`,
        hint: 'separated by spaces',
      },
      {
        key: 'directory',
        label: 'Directory',
        span: maven ? 3 : 6,
        mono: true,
        placeholder: '.',
        code: `${key}.dir`,
      },
      ...(maven
        ? [
            {
              key: 'mavenHome',
              label: 'Maven home',
              span: 3,
              mono: true,
              placeholder: '/opt/maven',
              code: `${key}.mvnPath`,
            } satisfies Field,
          ]
        : []),
      {
        key: 'flags',
        kind: 'area',
        label: 'Flags',
        mono: true,
        placeholder: maven ? '-B' : '--refresh-dependencies',
        code: `${key}.flags`,
        hint: 'one per line',
      },
      {
        key: 'environment',
        kind: 'area',
        label: 'Environment variables',
        mono: true,
        placeholder: 'JAVA_OPTS=-Xmx1g',
        code: `${key}.env`,
        hint: 'one NAME=value per line',
      },
    ];
  }
}
