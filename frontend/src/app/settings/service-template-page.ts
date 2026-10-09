import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize, map } from 'rxjs';
import { ServiceTemplateApi } from '../core/api';
import { errorMessage, fieldProblems } from '../core/errors';
import {
  FieldProblem,
  ServiceTemplate,
  ServiceTemplateRequest,
  ServiceTemplateValues,
} from '../core/models';
import { Notifier } from '../core/notifier';
import { HasUnsavedChanges } from '../core/unsaved-changes';
import { AGENT_LABEL } from '../products/pipeline-dialog';
import { Field, Fields, mono } from '../shared/fields';
import {
  IMAGE_TAG,
  IMAGE_TAG_ERROR,
  SHELL_SAFE_ERROR,
  applyFieldProblems,
  commaItems,
  eachItem,
  filled,
  fitsColumn,
  joinWords,
  max,
  optional,
  shellSafe,
  text,
  url,
} from '../shared/form-controls';
import { RelativeTimePipe } from '../shared/formatting';
import {
  JOB_PLACEHOLDERS,
  SERVICE_PLACEHOLDERS,
  fillTemplate,
  knownPlaceholders,
} from '../shared/service-template';
import { DsoLoading, DsoSpinner } from '../ui/loading';

const MAX_LABELS = 20;
const JOB_PATTERN = /^(?!.*\.\.)[A-Za-z0-9._ /{}-]*$/;
const NEXUS_IQ_PATTERN = /^[A-Za-z0-9._{}-]*$/;
const PROJECT_PATTERN = /^[A-Za-z0-9{}-]*$/;
const EXAMPLE = { code: 'CERT', service: 'backend-api' };

type TemplateKey = keyof ServiceTemplateValues;

interface TemplateSection {
  heading: string;
  note: string;
  fields: Field[];
}

export function createTemplateForm() {
  const service = knownPlaceholders(SERVICE_PLACEHOLDERS);
  return new FormGroup({
    agentLabels: text(
      '',
      filled,
      (control) =>
        commaItems(control.value).length > MAX_LABELS ? { maxItems: { max: MAX_LABELS } } : null,
      eachItem(commaItems, AGENT_LABEL, 'At most 100 characters per label'),
      fitsColumn(commaItems, ',', 1000),
    ),
    jenkinsJob: text(
      '',
      Validators.pattern(JOB_PATTERN),
      max(500),
      knownPlaceholders(JOB_PLACEHOLDERS),
    ),
    gradleTasks: text('', max(500)),
    gradleArtifact: shellSafe('', 500),
    gradleScanPattern: text('', max(300)),
    mavenTasks: text('', max(500)),
    mavenArtifact: shellSafe('', 500),
    mavenScanPattern: text('', max(300)),
    flutterScanPattern: text('', max(300)),
    deliveryTasks: text('', max(500)),
    nexusIqApplication: text('', Validators.pattern(NEXUS_IQ_PATTERN), max(200), service),
    repositoryUrl: url('', 1000, service),
    bitbucketCredentialsId: text('', max(200)),
    openShiftProject: text('', Validators.pattern(PROJECT_PATTERN), max(100), service),
    imageRegistry: text('', Validators.pattern(IMAGE_TAG), max(300)),
    healthCheckUrl: text('', max(500)),
  });
}

export type TemplateForm = ReturnType<typeof createTemplateForm>;

export function patchTemplate(form: TemplateForm, template: ServiceTemplateValues): void {
  const { agentLabels, ...values } = template;
  form.reset({
    ...Object.fromEntries(Object.entries(values).map(([key, value]) => [key, value ?? ''])),
    agentLabels: joinWords(agentLabels, ', '),
  });
}

export function toTemplateRequest(
  form: TemplateForm,
  version: number | null,
): ServiceTemplateRequest {
  const { agentLabels, ...values } = form.getRawValue();
  return {
    ...(Object.fromEntries(
      Object.entries(values).map(([key, value]) => [key, optional(value)]),
    ) as Omit<ServiceTemplateValues, 'agentLabels'>),
    agentLabels: commaItems(agentLabels),
    version,
  };
}

const SECTIONS: TemplateSection[] = [
  {
    heading: 'Pipelines',
    note: 'Every new pipeline runs on these agents, in the job its type gives it.',
    fields: [
      mono('agentLabels', 'Jenkins agent labels', '', 6, {
        placeholder: 'linux-agent',
        hint: 'separated by commas',
      }),
      mono('jenkinsJob', 'Jenkins job', '', 6, {
        placeholder: 'DevSecOps/{CODE}/{service}-{type}',
        hint: '{type} is full, security, extended, sast or nexusiq',
        error: "A job path with letters, digits, spaces, . _ / - and placeholders, without '..'",
      }),
    ],
  },
  {
    heading: 'Build',
    note: 'What a new service builds with, by its build tool, and what the security pipeline publishes.',
    fields: [
      mono('gradleTasks', 'Gradle tasks', '', 6, { placeholder: 'clean build' }),
      mono('gradleArtifact', 'Gradle artifact', '', 6, {
        placeholder: 'build/libs/*.jar',
        error: SHELL_SAFE_ERROR,
      }),
      mono('mavenTasks', 'Maven goals', '', 6, { placeholder: 'clean verify' }),
      mono('mavenArtifact', 'Maven artifact', '', 6, {
        placeholder: 'target/*.jar',
        error: SHELL_SAFE_ERROR,
      }),
      mono('deliveryTasks', 'Nexus delivery goals', '', 6, { placeholder: 'deploy:deploy-file' }),
    ],
  },
  {
    heading: 'Nexus IQ and Bitbucket',
    note: 'Where the Nexus IQ GoldenFix pipeline scans and opens its pull requests.',
    fields: [
      mono('nexusIqApplication', 'Nexus IQ application', '', 6, {
        placeholder: '{code}-{service}',
        error: 'Letters, digits, . _ - and placeholders only',
      }),
      mono('repositoryUrl', 'Bitbucket repository', '', 6, {
        placeholder: 'https://bitbucket.bbh.com/projects/{CODE}/repos/{code}-{service}',
      }),
      mono('bitbucketCredentialsId', 'Bitbucket credentials ID', '', 6, {
        placeholder: 'bitbucket-http-credentials',
      }),
      mono('gradleScanPattern', 'Gradle scan pattern', '', 6, {
        placeholder: '**/build/libs/*.jar',
      }),
      mono('mavenScanPattern', 'Maven scan pattern', '', 6, { placeholder: '**/target/*.jar' }),
      mono('flutterScanPattern', 'Flutter scan pattern', '', 6, {
        placeholder: '**/pubspec.lock',
      }),
    ],
  },
  {
    heading: 'OpenShift',
    note: 'Where a new OpenShift service builds and runs: its projects end in -build, -rd and -qc.',
    fields: [
      mono('openShiftProject', 'OpenShift project', '', 6, {
        placeholder: '{code}-{service}',
        hint: 'written in lower case',
        error: 'Letters, digits, - and placeholders only',
      }),
      mono('imageRegistry', 'Image registry', '', 6, {
        placeholder: 'docker-qc.tools.bbh.com',
        error: IMAGE_TAG_ERROR,
      }),
      mono('healthCheckUrl', 'Health check path', '', 6, { placeholder: '/actuator/health' }),
    ],
  },
];

const EXAMPLES: { key: TemplateKey; label: string }[] = [
  { key: 'jenkinsJob', label: 'Full pipeline job' },
  { key: 'nexusIqApplication', label: 'Nexus IQ application' },
  { key: 'repositoryUrl', label: 'Bitbucket repository' },
  { key: 'openShiftProject', label: 'OpenShift projects' },
];

@Component({
  selector: 'dso-service-template-page',
  imports: [ReactiveFormsModule, DsoLoading, DsoSpinner, Fields, RelativeTimePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="tab-header">
      <p class="meta">
        What Self-service and the Add service and Add pipeline forms fill in for a new service.
        Placeholders: <code>{{ '{CODE}' }}</code> the product code, <code>{{ '{code}' }}</code> the
        code in lower case, <code>{{ '{service}' }}</code> the service name.
        @if (template(); as t) {
          @if (t.version === null) {
            The BBH defaults, not saved yet.
          } @else {
            Version {{ t.version }} · saved {{ t.updatedAt | relative }}
          }
        }
      </p>
    </header>

    @if (loading()) {
      <dso-loading />
    }

    @if (loadError(); as error) {
      <div class="banner" role="alert">{{ error }}</div>
      <button type="button" class="btn btn-outline-primary" (click)="load()">Try again</button>
    } @else if (template()) {
      @if (conflict()) {
        <div class="banner danger conflict" role="alert">
          <div class="banner-text">
            <strong>Someone else saved the template after you opened this page.</strong>
            Your changes were not saved. Reload to get the current values, then make your changes
            again.
          </div>
          <button type="button" class="btn btn-primary" (click)="load()">Reload</button>
        </div>
      }
      <form [formGroup]="form" (ngSubmit)="save()" novalidate>
        @if (unmatchedProblems().length) {
          <div class="banner" role="alert">
            <ul class="problems">
              @for (problem of unmatchedProblems(); track problem.field + problem.message) {
                <li>
                  <span class="mono">{{ problem.field }}</span
                  >: {{ problem.message }}
                </li>
              }
            </ul>
          </div>
        }
        @for (section of sections; track section.heading) {
          <section class="card section">
            <header class="section-head">
              <h2>{{ section.heading }}</h2>
              <p>{{ section.note }}</p>
            </header>
            <div class="form-fields">
              <dso-fields [group]="form" [fields]="section.fields" />
            </div>
          </section>
        }
        <section class="card section example" aria-label="Example">
          <header class="section-head">
            <h2>Example</h2>
            <p>What a service {{ example.service }} of the product {{ example.code }} gets.</p>
          </header>
          <dl class="pairs">
            @for (line of examples(); track line.label) {
              <div>
                <dt>{{ line.label }}</dt>
                <dd class="mono">{{ line.value }}</dd>
              </div>
            }
          </dl>
        </section>
        <div class="save-bar">
          @if (saveError(); as error) {
            <span class="save-error" role="alert">{{ error }}</span>
          } @else if (form.dirty) {
            <span class="muted">Unsaved changes</span>
          }
          <span class="spacer"></span>
          <button
            type="button"
            class="btn btn-link"
            (click)="discard()"
            [disabled]="!form.dirty || saving()"
          >
            Discard changes
          </button>
          <button type="submit" class="btn btn-primary" [disabled]="saving() || conflict()">
            @if (saving()) {
              <dso-spinner />
            }
            Save template
          </button>
        </div>
      </form>
    }
  `,
  styles: `
    .tab-header {
      margin-bottom: 12px;
    }

    .meta {
      margin: 0;
      max-width: 900px;
      color: var(--dso-muted);
      font-size: 12px;
    }

    .section {
      margin-bottom: 12px;
      padding: 12px 16px 4px;
    }

    .section-head {
      padding-bottom: 8px;
      border-bottom: 1px solid var(--dso-border);

      h2 {
        margin: 0 0 2px;
        font-size: 17px;
      }

      p {
        margin: 0;
        color: var(--dso-muted);
        font-size: 12px;
      }

      + .form-fields {
        padding: 10px 0 8px;
      }
    }

    .example {
      padding-bottom: 12px;

      dl {
        margin-top: 10px;
      }

      dd {
        overflow-wrap: anywhere;
      }
    }

    .banner-text {
      flex: 1;
    }
  `,
})
export class ServiceTemplatePage implements OnInit, HasUnsavedChanges {
  private readonly api = inject(ServiceTemplateApi);
  private readonly notifier = inject(Notifier);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly form = createTemplateForm();
  protected readonly sections = SECTIONS;
  protected readonly example = EXAMPLE;
  protected readonly template = signal<ServiceTemplate | null>(null);
  protected readonly loading = signal(false);
  protected readonly loadError = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly saveError = signal<string | null>(null);
  protected readonly conflict = signal(false);
  protected readonly unmatchedProblems = signal<FieldProblem[]>([]);

  private readonly values = toSignal(
    this.form.valueChanges.pipe(map(() => this.form.getRawValue())),
    { initialValue: this.form.getRawValue() },
  );
  protected readonly examples = computed(() => {
    const values = this.values();
    const filledIn = (key: TemplateKey) =>
      fillTemplate(
        values[key as Exclude<TemplateKey, 'agentLabels'>],
        EXAMPLE.code,
        EXAMPLE.service,
        'FULL',
      ) || 'none';
    return EXAMPLES.map(({ key, label }) => {
      const value = filledIn(key);
      return {
        label,
        value:
          key === 'openShiftProject' && value !== 'none'
            ? ['build', 'rd', 'qc'].map((suffix) => `${value.toLowerCase()}-${suffix}`).join(', ')
            : value,
      };
    });
  });

  ngOnInit(): void {
    this.load();
  }

  hasUnsavedChanges(): boolean {
    return this.form.dirty;
  }

  protected load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.api
      .get()
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (template) => this.apply(template),
        error: (error) => this.loadError.set(errorMessage(error)),
      });
  }

  protected discard(): void {
    const template = this.template();
    if (template) {
      this.apply(template);
    }
  }

  protected save(): void {
    this.saveError.set(null);
    this.conflict.set(false);
    this.unmatchedProblems.set([]);
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      this.saveError.set('Some fields need your attention.');
      return;
    }
    this.saving.set(true);
    this.api
      .update(toTemplateRequest(this.form, this.template()?.version ?? null))
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (template) => {
          this.apply(template);
          this.notifier.success('The service template is saved');
        },
        error: (error) => this.showSaveError(error),
      });
  }

  private apply(template: ServiceTemplate): void {
    patchTemplate(this.form, template);
    this.template.set(template);
    this.saveError.set(null);
    this.conflict.set(false);
    this.unmatchedProblems.set([]);
  }

  private showSaveError(error: unknown): void {
    if (error instanceof HttpErrorResponse && error.status === 409) {
      this.conflict.set(true);
      this.saveError.set('Not saved: the template was changed by someone else.');
      return;
    }
    const problems = fieldProblems(error);
    if (problems.length === 0) {
      this.saveError.set(errorMessage(error));
      return;
    }
    this.unmatchedProblems.set(applyFieldProblems(this.form, problems));
    this.saveError.set('The portal did not accept some values. They are marked above.');
  }
}
