import { ClipboardModule } from '@angular/cdk/clipboard';
import { DIALOG_DATA } from '@angular/cdk/dialog';
import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, output, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { PipelinesApi } from '../core/api';
import { errorMessage } from '../core/errors';
import { Pipeline, PipelineKey, pipelineTypeName } from '../core/models';
import { Notifier } from '../core/notifier';
import { RelativeTimePipe, capitalized } from '../shared/formatting';
import { DIALOG } from '../ui/dialog';
import { GRID, GridColumn } from '../ui/grid';
import { DsoLoading } from '../ui/loading';

const status = (key: PipelineKey) => (key.status === 'ACTIVE' ? 'Active' : 'Invalidated');

@Component({
  selector: 'dso-key-history-dialog',
  imports: [ClipboardModule, DatePipe, DIALOG, GRID, DsoLoading, RelativeTimePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="modal-header">
      <h2 dsoDialogTitle>Key history</h2>
    </div>
    <div class="modal-body">
      <p class="intro">
        {{ typeName }} pipeline of <strong class="mono">{{ data.serviceName }}</strong> in
        {{ data.productName }}.
      </p>
      @if (pipeline.isLoading()) {
        <dso-loading />
      }
      @if (pipeline.error(); as error) {
        <div class="banner">{{ errorMessage(error) }}</div>
      } @else if (pipeline.hasValue()) {
        @if (pipeline.value().activeKey === null) {
          <div class="key-status row-wrap">
            <span class="chip danger">Key invalidated</span>
            <span class="muted"
              >The pipeline is refused its configuration until its key is regenerated.</span
            >
            <button
              type="button"
              class="text-link"
              (click)="regenerate()"
              [disabled]="regenerating()"
            >
              {{ regenerating() ? 'Regenerating…' : 'Regenerate key' }}
            </button>
          </div>
        } @else if (issuedKey(); as value) {
          <div class="key-status row-wrap" role="status">
            <span class="chip success">New key</span>
            <span class="mono key-value">{{ value }}</span>
            <button
              type="button"
              class="text-link"
              [cdkCopyToClipboard]="value"
              (cdkCopyToClipboardCopied)="copied()"
            >
              Copy
            </button>
          </div>
        }
        @if (regenerateError(); as error) {
          <div class="banner">{{ error }}</div>
        }
        <dso-grid
          label="Keys"
          [rows]="pipeline.value().keys ?? []"
          [columns]="columns"
          [rowId]="keyId"
        >
          <ng-template dsoCell="key" let-key>
            <span class="mono">{{ key.hint }}</span>
          </ng-template>
          <ng-template dsoCell="status" let-key>
            <span class="chip" [class]="key.status === 'ACTIVE' ? 'success' : 'danger'">{{
              status(key)
            }}</span>
          </ng-template>
          <ng-template dsoCell="issuedAt" let-key>
            <span [title]="key.issuedAt | date: 'medium'">{{
              key.issuedAt | date: 'd MMM y, HH:mm'
            }}</span>
          </ng-template>
          <ng-template dsoCell="lastUsedAt" let-key>
            {{ key.lastUsedAt ? (key.lastUsedAt | relative) : '' }}
          </ng-template>
          <ng-template dsoCell="revoked" let-key>
            @if (key.revokedAt) {
              <div>
                <div>{{ key.revokedAt | date: 'd MMM y, HH:mm' }}</div>
                <div class="muted reason">{{ key.revokeReason }}</div>
              </div>
            } @else {
              <span class="muted">–</span>
            }
          </ng-template>
        </dso-grid>
      }
    </div>
    <div class="modal-footer">
      <button type="button" class="btn btn-primary" dsoDialogClose>Close</button>
    </div>
  `,
  styles: `
    .modal-body {
      width: min(860px, 86vw);
    }
    .intro {
      margin: 0 0 12px;
      color: var(--dso-muted);
    }
    .key-status {
      margin-bottom: 10px;
      font-size: 12.5px;
    }
    .reason {
      font-size: 12.5px;
    }
  `,
})
export class KeyHistoryDialog {
  readonly keyIssued = output<Pipeline>();

  protected readonly data = inject<Pipeline>(DIALOG_DATA);
  protected readonly typeName = capitalized(pipelineTypeName(this.data.type));
  private readonly api = inject(PipelinesApi);
  private readonly notifier = inject(Notifier);

  protected readonly status = status;
  protected readonly keyId = (key: PipelineKey) => key.id;
  protected readonly columns: GridColumn<PipelineKey>[] = [
    { key: 'key', header: 'Key', value: (key) => key.hint, width: 150 },
    { key: 'status', header: 'Status', value: status, width: 120 },
    { key: 'issuedAt', header: 'Issued', value: (key) => key.issuedAt, width: 150 },
    { key: 'lastUsedAt', header: 'Last REST fetch', value: (key) => key.lastUsedAt, width: 140 },
    {
      key: 'revoked',
      header: 'Invalidated',
      value: (key) => key.revokedAt,
      minWidth: 200,
      wrap: true,
    },
  ];
  protected readonly pipeline = rxResource({ stream: () => this.api.get(this.data.id) });
  protected readonly regenerating = signal(false);
  protected readonly regenerateError = signal<string | null>(null);
  protected readonly issuedKey = signal<string | null>(null);
  protected readonly errorMessage = errorMessage;

  protected regenerate(): void {
    this.regenerating.set(true);
    this.regenerateError.set(null);
    this.api
      .issueKey(this.data.id)
      .pipe(finalize(() => this.regenerating.set(false)))
      .subscribe({
        next: (updated) => {
          this.issuedKey.set(updated.activeKey?.value ?? null);
          if (updated.keys === null) {
            this.pipeline.reload();
          } else {
            this.pipeline.set(updated);
          }
          this.keyIssued.emit(updated);
        },
        error: (error) => this.regenerateError.set(errorMessage(error)),
      });
  }

  protected copied(): void {
    this.notifier.success('Key copied to the clipboard');
  }
}
