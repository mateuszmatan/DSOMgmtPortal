import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, map } from 'rxjs';
import { EvidenceApi, ProductsApi } from '../core/api';
import { errorMessage } from '../core/errors';
import { ProductEvidence, ServiceEvidence } from '../core/models';
import { EVIDENCE } from '../core/sections';
import { CountedPipe } from '../shared/formatting';
import { FORM_FIELD } from '../ui/form-field';
import { DsoLoading } from '../ui/loading';
import { PANEL } from '../ui/panel';
import { PipelineEvidenceCard } from './pipeline-evidence-card';

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

  protected readonly evidence = signal<ReadonlyMap<number, EvidenceState>>(new Map());
  protected readonly errorMessage = errorMessage;
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
