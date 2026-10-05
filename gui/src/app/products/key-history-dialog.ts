import { ClipboardModule } from '@angular/cdk/clipboard';
import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, output, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { finalize } from 'rxjs';
import { PipelinesApi } from '../core/api';
import { errorMessage } from '../core/errors';
import { Pipeline } from '../core/models';
import { Notifier } from '../core/notifier';
import { RelativeTimePipe } from '../shared/formatting';

@Component({
  selector: 'dso-key-history-dialog',
  imports: [
    ClipboardModule,
    DatePipe,
    MatButtonModule,
    MatDialogModule,
    MatProgressBarModule,
    MatTableModule,
    RelativeTimePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>Key history</h2>
    <mat-dialog-content>
      <p class="intro">
        {{ data.type.charAt(0) + data.type.slice(1).toLowerCase() }} pipeline of
        <strong class="mono">{{ data.serviceName }}</strong> in {{ data.productName }}.
      </p>
      @if (pipeline.isLoading()) {
        <mat-progress-bar mode="indeterminate" />
      }
      @if (pipeline.error(); as error) {
        <div class="banner">{{ errorMessage(error) }}</div>
      } @else if (pipeline.hasValue()) {
        @if (pipeline.value().activeKey === null) {
          <div class="key-status">
            <span class="state">Key invalidated</span>
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
          <div class="key-status" role="status">
            <span class="state active">New key</span>
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
        <div class="table-scroll">
          <table mat-table [dataSource]="pipeline.value().keys ?? []">
            <ng-container matColumnDef="key">
              <th mat-header-cell *matHeaderCellDef>Key</th>
              <td mat-cell *matCellDef="let key" class="mono nowrap">{{ key.hint }}</td>
            </ng-container>
            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef>Status</th>
              <td mat-cell *matCellDef="let key">
                <span class="state" [class.active]="key.status === 'ACTIVE'">{{
                  key.status === 'ACTIVE' ? 'Active' : 'Invalidated'
                }}</span>
              </td>
            </ng-container>
            <ng-container matColumnDef="issuedAt">
              <th mat-header-cell *matHeaderCellDef>Issued</th>
              <td mat-cell *matCellDef="let key" [title]="key.issuedAt | date: 'medium'">
                {{ key.issuedAt | date: 'd MMM y, HH:mm' }}
              </td>
            </ng-container>
            <ng-container matColumnDef="lastUsedAt">
              <th mat-header-cell *matHeaderCellDef>Last used</th>
              <td mat-cell *matCellDef="let key">
                {{ key.lastUsedAt ? (key.lastUsedAt | relative) : 'Never' }}
              </td>
            </ng-container>
            <ng-container matColumnDef="revoked">
              <th mat-header-cell *matHeaderCellDef>Invalidated</th>
              <td mat-cell *matCellDef="let key">
                @if (key.revokedAt) {
                  <div>{{ key.revokedAt | date: 'd MMM y, HH:mm' }}</div>
                  <div class="muted reason">{{ key.revokeReason }}</div>
                } @else {
                  <span class="muted">–</span>
                }
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let row; columns: columns"></tr>
          </table>
        </div>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-flat-button mat-dialog-close>Close</button>
    </mat-dialog-actions>
  `,
  styles: `
    mat-dialog-content {
      width: min(860px, 86vw);
    }
    .intro {
      margin: 0 0 12px;
      color: var(--dso-muted);
    }
    .state {
      padding: 0 6px;
      background: var(--dso-danger-bg);
      color: var(--dso-danger);
      font-weight: 600;
      font-size: 11px;
      line-height: 18px;
    }
    .state.active {
      background: var(--dso-success-bg);
      color: var(--dso-success);
    }
    .key-status {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 4px 10px;
      margin-bottom: 10px;
      font-size: 12.5px;
    }
    .key-value {
      padding: 1px 8px;
      background: var(--dso-code-bg);
      color: var(--dso-code-fg);
      font-size: 11.5px;
      overflow-wrap: anywhere;
    }
    .table-scroll {
      overflow-x: auto;
    }
    .reason {
      font-size: 12.5px;
      max-width: 260px;
    }
    td.mat-mdc-cell {
      padding-top: 4px;
      padding-bottom: 4px;
    }
    .nowrap {
      white-space: nowrap;
    }
  `,
})
export class KeyHistoryDialog {
  readonly keyIssued = output<Pipeline>();

  protected readonly data = inject<Pipeline>(MAT_DIALOG_DATA);
  private readonly api = inject(PipelinesApi);
  private readonly notifier = inject(Notifier);

  protected readonly columns = ['key', 'status', 'issuedAt', 'lastUsedAt', 'revoked'];
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
