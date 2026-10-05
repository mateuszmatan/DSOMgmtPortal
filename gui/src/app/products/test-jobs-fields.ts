import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { GlobalSettings, TestStage } from '../core/models';
import { errorText } from '../shared/form-errors';
import {
  ServiceForm,
  TestJobForm,
  createTestJobForm,
  isJobUrl,
  isRemoteJob,
} from './product-form-model';

type StageParallel = 'smokeMaxParallel' | 'regressionMaxParallel' | 'performanceMaxParallel';

const STAGES: { value: TestStage; label: string; noun: string; parallel: StageParallel }[] = [
  { value: 'SMOKE', label: 'Smoke tests', noun: 'smoke', parallel: 'smokeMaxParallel' },
  {
    value: 'REGRESSION',
    label: 'Regression tests',
    noun: 'regression',
    parallel: 'regressionMaxParallel',
  },
  {
    value: 'PERFORMANCE',
    label: 'Performance tests',
    noun: 'performance',
    parallel: 'performanceMaxParallel',
  },
];

@Component({
  selector: 'dso-test-jobs-fields',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './test-jobs-fields.html',
  styles: `
    :host {
      display: block;
    }
    code {
      font-size: 11.5px;
    }
    .parallel {
      width: 150px;
    }
  `,
})
export class TestJobsFields {
  readonly form = input.required<ServiceForm>();
  readonly defaults = input<GlobalSettings | null>(null);

  protected readonly stages = STAGES;
  protected readonly errorText = errorText;
  protected readonly isRemoteJob = isRemoteJob;
  protected readonly isJobUrl = isJobUrl;

  protected jobsOf(stage: TestStage): TestJobForm[] {
    return this.form().controls.testJobs.controls.filter(
      (job) => job.controls.stage.value === stage,
    );
  }

  protected add(stage: TestStage): void {
    this.form().controls.testJobs.push(createTestJobForm({ stage }));
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

  protected parallelDefault(): string {
    const value = this.defaults()?.serviceDefaults.testsMaxParallel;
    return value === undefined ? 'global default' : `global default ${value}`;
  }
}
