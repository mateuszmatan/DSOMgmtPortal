import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { rxResource, takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, map } from 'rxjs';
import { EvidenceApi, ProductsApi } from '../core/api';
import { RETRY, errorMessage } from '../core/errors';
import { ProductEvidence, ProductSummary, ServiceEvidence } from '../core/models';
import { EVIDENCE } from '../core/sections';
import { NOT_IN_A_DEPARTMENT } from '../products/departments';
import { CountedPipe } from '../shared/formatting';
import { FORM_FIELD } from '../ui/form-field';
import { DsoLoading } from '../ui/loading';
import { PANEL } from '../ui/panel';
import { PipelineEvidenceCard } from './pipeline-evidence-card';

const CHECKS = [
  {
    name: 'Unit tests',
    meaning:
      'Automated tests of the code itself, run on every build. Coverage is the share of code lines they run.',
  },
  {
    name: 'Smoke, regression and performance tests',
    meaning:
      'Run on the service deployed in a test region: it starts, existing features still work, it is fast enough.',
  },
  {
    name: 'SAST',
    meaning: 'Static security testing: HCL AppScan reads the source code for security flaws.',
  },
  {
    name: 'DAST',
    meaning:
      'Dynamic security testing: HCL AppScan probes the running service in a test region for weaknesses.',
  },
  {
    name: 'SonarQube',
    meaning: 'Checks the code quality (bugs, risky code, coverage) against the BBH quality gate.',
  },
  {
    name: 'Nexus IQ',
    meaning:
      'Checks the open-source libraries the service uses for known vulnerabilities and licence problems.',
  },
  {
    name: 'Golden pull request',
    meaning:
      "GoldenFix proposes safe versions of vulnerable libraries as a pull request in the service's repository.",
  },
  {
    name: 'Release gate',
    meaning:
      "The pipeline's final decision: the build may be released only when the checked scans stay within the BBH limits.",
  },
];

type EvidenceState =
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'loaded'; evidence: ProductEvidence };

@Component({
  selector: 'dso-change-evidence',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    FORM_FIELD,
    PANEL,
    DsoLoading,
    CountedPipe,
    PipelineEvidenceCard,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './change-evidence.html',
  styleUrl: './change-evidence.scss',
})
export class ChangeEvidencePage {
  private readonly products = inject(ProductsApi);
  private readonly evidenceApi = inject(EvidenceApi);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly section = EVIDENCE;
  protected readonly search = new FormControl('', { nonNullable: true });
  protected readonly query = toSignal(
    this.search.valueChanges.pipe(
      debounceTime(250),
      map((value) => value.trim()),
      distinctUntilChanged(),
    ),
    { initialValue: '' },
  );

  protected readonly list = rxResource({
    params: () => this.query(),
    stream: ({ params }) => this.products.list(params),
  });

  protected readonly groups = computed(() =>
    this.list.hasValue() ? byDepartmentName(this.list.value()) : [],
  );

  protected readonly checks = CHECKS;
  protected readonly evidence = signal<ReadonlyMap<number, EvidenceState>>(new Map());
  protected readonly errorMessage = errorMessage;
  protected readonly retry = RETRY;
  protected readonly loading: EvidenceState = { status: 'loading' };

  protected identifiers(service: ServiceEvidence): { label: string; value: string | null }[] {
    return [
      { label: 'Artifact', value: service.artifactName },
      { label: 'HCL AppScan application ID', value: service.appScanApplicationId },
      { label: 'SonarQube project key', value: service.sonarProjectKey },
      { label: 'Nexus IQ application', value: service.nexusIqApplication },
    ];
  }

  protected opened(productId: number): void {
    const state = this.evidence().get(productId);
    if (!state || state.status === 'error') {
      this.load(productId);
    }
  }

  protected load(productId: number): void {
    this.set(productId, { status: 'loading' });
    this.evidenceApi
      .product(productId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (evidence) => this.set(productId, { status: 'loaded', evidence }),
        error: (error) => this.set(productId, { status: 'error', message: errorMessage(error) }),
      });
  }

  private set(productId: number, state: EvidenceState): void {
    this.evidence.update((states) => new Map(states).set(productId, state));
  }
}

function byDepartmentName(
  products: readonly ProductSummary[],
): { name: string; products: ProductSummary[] }[] {
  const groups = new Map<string, ProductSummary[]>();
  for (const product of [...products].sort((a, b) => a.name.localeCompare(b.name))) {
    const name = product.departmentName ?? NOT_IN_A_DEPARTMENT;
    groups.set(name, [...(groups.get(name) ?? []), product]);
  }
  return [...groups]
    .map(([name, members]) => ({ name, products: members }))
    .sort(
      (a, b) =>
        Number(a.name === NOT_IN_A_DEPARTMENT) - Number(b.name === NOT_IN_A_DEPARTMENT) ||
        a.name.localeCompare(b.name),
    );
}
