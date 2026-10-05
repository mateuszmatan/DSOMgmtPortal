import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ClipboardModule } from '@angular/cdk/clipboard';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';

export interface CodeDialogData {
  title: string;
  subtitle?: string;
  code: string;
  fileName?: string;
}

@Component({
  selector: 'dso-code-dialog',
  imports: [MatDialogModule, MatButtonModule, ClipboardModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content>
      @if (data.subtitle) {
        <p class="subtitle">{{ data.subtitle }}</p>
      }
      <pre class="code mono">{{ data.code }}</pre>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      @if (data.fileName) {
        <button mat-button (click)="download()">Download</button>
      }
      <button mat-button [cdkCopyToClipboard]="data.code" (cdkCopyToClipboardCopied)="copied()">
        Copy
      </button>
      <button mat-flat-button mat-dialog-close>Close</button>
    </mat-dialog-actions>
  `,
  styles: `
    .subtitle {
      margin: 0 0 8px;
      color: var(--dso-muted);
    }
    .code {
      margin: 0;
      padding: 10px 12px;

      background: var(--dso-code-bg);
      color: var(--dso-code-fg);
      font-size: 12px;
      line-height: 1.45;
      max-height: 60vh;
      overflow: auto;
    }
  `,
})
export class CodeDialog {
  protected readonly data = inject<CodeDialogData>(MAT_DIALOG_DATA);
  private readonly snackBar = inject(MatSnackBar);

  protected copied(): void {
    this.snackBar.open('Copied to the clipboard', undefined, { duration: 2000 });
  }

  protected download(): void {
    const url = URL.createObjectURL(new Blob([this.data.code], { type: 'application/yaml' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = this.data.fileName ?? 'config.yaml';
    link.click();
    URL.revokeObjectURL(url);
  }
}
