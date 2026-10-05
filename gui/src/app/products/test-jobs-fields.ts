import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { GlobalSettings, TEST_STAGES, TestStage } from '../core/models';
import { addItem } from '../shared/form-controls';
import { Field, Fields, area, choice, count, formRevision, line, mono } from '../shared/fields';
import {
  ServiceForm,
  TestJobForm,
  createTestJobForm,
  isJobUrl,
  isRemoteJob,
} from './product-form-model';

type StageParallel = 'smokeMaxParallel' | 'regressionMaxParallel' | 'performanceMaxParallel';

interface Stage {
  value: TestStage;
  label: string;
  noun: string;
  parallel: StageParallel;
}

const STAGES: Stage[] = TEST_STAGES.map((value) => {
  const noun = value.toLowerCase();
  return {
    value,
    label: `${noun.charAt(0).toUpperCase()}${noun.slice(1)} tests`,
    noun,
    parallel: `${noun}MaxParallel` as StageParallel,
  };
});

const REMOTE: Field[] = [
  {
    key: 'remoteJenkins',
    label: 'Remote Jenkins',
    span: 3,
    placeholder: 'perf-jenkins',
    code: 'remoteJenkins',
  },
  {
    key: 'remoteJenkinsUrl',
    label: 'Remote Jenkins URL',
    span: 6,
    placeholder: 'https://perf-jenkins.bbh.com',
    code: 'remoteJenkinsUrl',
    error: 'Must be an http or https URL',
  },
  { key: 'credentialsId', label: 'Credentials ID', span: 3, mono: true, code: 'credentialsId' },
];

@Component({
  selector: 'dso-test-jobs-fields',
  imports: [MatButtonModule, Fields],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './test-jobs-fields.html',
  styles: `
    :host {
      display: block;
    }
    .parallel {
      display: grid;
      width: 150px;
    }
  `,
})
export class TestJobsFields {
  readonly form = input.required<ServiceForm>();
  readonly defaults = input<GlobalSettings | null>(null);

  private readonly changes = formRevision(this.form);

  protected readonly stages = STAGES;

  protected parallelFields(): Field[] {
    const value = this.defaults()?.serviceDefaults.testsMaxParallel;
    return [
      count('maxParallel', 'Parallel jobs, every stage', 'tests.maxParallel', 4, {
        min: 1,
        max: 100,
        hint: `left empty: global default${value === undefined ? '' : ` ${value}`}`,
      }),
    ];
  }

  protected stageParallelField(stage: Stage): Field[] {
    return [
      count(stage.parallel, 'Parallel jobs', `tests.${stage.noun}.maxParallel`, 12, {
        min: 1,
        max: 100,
      }),
    ];
  }

  protected jobFields(job: TestJobForm, stage: Stage): Field[] {
    return [
      line('name', 'Name', 'name', 3, { placeholder: 'smoke' }),
      mono('job', 'Jenkins job', isJobUrl(job.controls.job.value) ? 'url' : 'job', 6, {
        placeholder: 'CERT/gui-smoke-tests',
        hint: 'a job path, or the full URL of a job on another Jenkins',
      }),
      choice(
        'type',
        'Runs on',
        [
          { value: null, label: 'Library default' },
          { value: 'LOCAL', label: 'This Jenkins' },
          { value: 'REMOTE', label: 'Another Jenkins' },
        ],
        'type',
        3,
      ),
      count('timeoutMinutes', 'Timeout (minutes)', 'timeoutMin', 3, { min: 1, max: 1440 }),
      area('parameters', 'Parameters', 'parameters', 6, {
        mono: true,
        placeholder: 'ENV=rd\nSUITE=critical',
        hint: 'One NAME=value per line',
      }),
      choice(
        'stage',
        'Stage',
        STAGES.map((option) => ({ value: option.value, label: option.label })),
        `tests.${stage.noun}.jobs`,
        3,
      ),
      ...(isRemoteJob(job) ? REMOTE : []),
    ];
  }

  protected jobsOf(stage: TestStage): TestJobForm[] {
    this.changes();
    return this.form().controls.testJobs.controls.filter(
      (job) => job.controls.stage.value === stage,
    );
  }

  protected add(stage: TestStage): void {
    addItem(this.form().controls.testJobs, createTestJobForm({ stage }));
    this.form().markAsDirty();
  }

  protected remove(job: TestJobForm): void {
    const jobs = this.form().controls.testJobs;
    jobs.removeAt(jobs.controls.indexOf(job));
    this.form().markAsDirty();
  }

  protected move(job: TestJobForm, offset: -1 | 1): void {
    const jobs = this.form().controls.testJobs;
    const sameStage = this.jobsOf(job.controls.stage.value);
    const neighbour = sameStage[sameStage.indexOf(job) + offset];
    if (!neighbour) {
      return;
    }
    const from = jobs.controls.indexOf(job);
    const to = jobs.controls.indexOf(neighbour);
    jobs.removeAt(from, { emitEvent: false });
    jobs.insert(to, job);
    this.form().markAsDirty();
  }

  protected jobName(job: TestJobForm): string {
    return job.controls.name.value || job.controls.job.value || 'New job';
  }

  protected isFirst(job: TestJobForm): boolean {
    return this.jobsOf(job.controls.stage.value)[0] === job;
  }

  protected isLast(job: TestJobForm): boolean {
    return this.jobsOf(job.controls.stage.value).at(-1) === job;
  }
}
