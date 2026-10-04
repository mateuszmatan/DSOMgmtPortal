import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { PipelinesApi } from '../core/api';
import { errorMessage } from '../core/errors';
import { Pipeline } from '../core/models';
import { MaskKeyPipe, RelativeTimePipe } from '../shared/formatting';

@Component({
  selector: 'dso-key-history-dialog',
  imports: [
    DatePipe,
    MatButtonModule,
    MatDialogModule,
    MatIconModule,
    MatProgressBarModule,
    MatTableModule,
    MaskKeyPipe,
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
        <div class="banner">
          <mat-icon>error_outline</mat-icon><span>{{ errorMessage(error) }}</span>
        </div>
      } @else if (pipeline.hasValue()) {
        <table mat-table [dataSource]="pipeline.value().keys ?? []">
          <ng-container matColumnDef="key">
            <th mat-header-cell *matHeaderCellDef>Key</th>
            <td mat-cell *matCellDef="let key" class="mono nowrap">{{ key.value | maskKey }}</td>
          </ng-container>
          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef>Status</th>
            <td mat-cell *matCellDef="let key">
              <span class="state" [class.active]="key.status === 'ACTIVE'">
                <mat-icon>{{ key.status === 'ACTIVE' ? 'key' : 'key_off' }}</mat-icon>
                {{ key.status === 'ACTIVE' ? 'Active' : 'Invalidated' }}
              </span>
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
      display: inline-flex;
      align-items: center;
      gap: 4px;
      color: var(--dso-danger);
      font-weight: 600;
      font-size: 12.5px;
    }
    .state.active {
      color: var(--dso-success);
    }
    .state mat-icon {
      font-size: 16px;
      width: 16px;
      height: 16px;
    }
    .reason {
      font-size: 12.5px;
      max-width: 260px;
    }
    td.mat-mdc-cell {
      padding-top: 8px;
      padding-bottom: 8px;
    }
    .nowrap {
      white-space: nowrap;
    }
  `,
})
export class KeyHistoryDialog {
  protected readonly data = inject<Pipeline>(MAT_DIALOG_DATA);
  private readonly api = inject(PipelinesApi);

  protected readonly columns = ['key', 'status', 'issuedAt', 'lastUsedAt', 'revoked'];
  protected readonly pipeline = rxResource({ stream: () => this.api.get(this.data.id) });
  protected readonly errorMessage = errorMessage;
}
