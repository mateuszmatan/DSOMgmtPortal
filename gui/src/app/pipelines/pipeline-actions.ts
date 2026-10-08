import { Injectable, inject } from '@angular/core';
import { outputToObservable } from '@angular/core/rxjs-interop';
import { MatDialog } from '@angular/material/dialog';
import { EMPTY, Observable, catchError, filter, map, of, switchMap, tap } from 'rxjs';
import { PipelinesApi, SettingsApi } from '../core/api';
import {
  Pipeline,
  PipelineType,
  pipelineTypeLabel,
  pipelineTypeName,
  pipelineTypeSlug,
} from '../core/models';
import { Notifier } from '../core/notifier';
import { jenkinsfile } from '../products/jenkinsfile';
import { KeyHistoryDialog } from '../products/key-history-dialog';
import { PipelineDialog, PipelineDialogData } from '../products/pipeline-dialog';
import { RevokeKeyDialog } from '../products/revoke-key-dialog';
import { CodeDialog, CodeDialogData } from '../shared/code-dialog';
import { ConfirmDialog, ConfirmDialogData } from '../shared/confirm-dialog';

export function typeName(type: PipelineType): string {
  const label = pipelineTypeLabel(type);
  return /[A-Z]/.test(label.slice(1)) ? label : label.toLowerCase();
}

@Injectable({ providedIn: 'root' })
export class PipelineActions {
  private readonly api = inject(PipelinesApi);
  private readonly settings = inject(SettingsApi);
  private readonly dialog = inject(MatDialog);
  private readonly notifier = inject(Notifier);

  copied(): void {
    this.notifier.success('Key copied to the clipboard');
  }

  showConfig(pipeline: Pipeline): void {
    this.api.config(pipeline.id).subscribe({
      next: (code) =>
        this.openCode({
          title: `Configuration of the ${pipeline.serviceName} ${pipelineTypeName(pipeline.type)} pipeline`,
          subtitle:
            "What the DevSecOps library receives for this pipeline's key. Showing it here does not count as a use of the key.",
          code,
          fileName: `${pipeline.productCode.toLowerCase()}-${pipeline.serviceName}-${pipelineTypeSlug(pipeline.type)}.yaml`,
        }),
      error: (error) => this.notifier.error(error),
    });
  }

  showJenkinsfile(pipelines: readonly Pipeline[]): void {
    const together = pipelines.length > 1;
    const subtitle = together
      ? `One run builds ${pipelines.map((p) => p.serviceName).join(', ')}; the first key is the ` +
        'primary service. Everything else comes from the portal by the keys.'
      : 'Once the DevSecOps library reads its configuration from the portal, this is the whole Jenkinsfile of the ' +
        'service: everything else comes from the portal by the key.';
    this.settings
      .get()
      .pipe(catchError(() => of(null)))
      .subscribe((settings) =>
        this.openCode({
          title: together ? 'Jenkinsfile for several services' : 'Jenkinsfile',
          subtitle,
          code: jenkinsfile(pipelines, settings?.platform.jenkinsLibrary),
          fileName: 'Jenkinsfile',
        }),
      );
  }

  add(data: PipelineDialogData): Observable<Pipeline> {
    return this.openPipelineDialog(
      data,
      (pipeline) => `${pipelineTypeLabel(pipeline.type)} pipeline added to ${pipeline.serviceName}`,
    );
  }

  edit(pipeline: Pipeline): Observable<Pipeline> {
    return this.openPipelineDialog(
      {
        service: {
          serviceId: pipeline.serviceId,
          serviceName: pipeline.serviceName,
          pipelines: [pipeline],
        },
        pipeline,
      },
      () => 'Pipeline settings saved',
    );
  }

  revokeKey(pipeline: Pipeline): Observable<Pipeline> {
    return this.dialog
      .open<RevokeKeyDialog, Pipeline, Pipeline>(RevokeKeyDialog, { data: pipeline })
      .afterClosed()
      .pipe(
        filter((updated): updated is Pipeline => !!updated),
        tap(() => this.notifier.success('Key invalidated: the pipeline stops at its next start')),
      );
  }

  replaceKey(pipeline: Pipeline): Observable<Pipeline> {
    return this.confirm({
      title: 'Replace the key?',
      message:
        'The current key is invalidated and a new one is issued. Update the Jenkinsfile with the new key, ' +
        'or the pipeline stops at its next start.',
      confirmLabel: 'Replace key',
      danger: true,
    }).pipe(
      switchMap(() => this.issued(this.api.issueKey(pipeline.id))),
      tap(() => this.notifier.success('New key issued')),
    );
  }

  regenerateKey(pipeline: Pipeline): Observable<Pipeline> {
    return this.issued(this.api.issueKey(pipeline.id)).pipe(
      tap((updated) =>
        this.notifier.success(
          `${pipelineTypeLabel(updated.type)} pipeline of ${updated.serviceName} has a new key: ` +
            'pass it in the Jenkinsfile',
        ),
      ),
    );
  }

  showKeyHistory(pipeline: Pipeline): Observable<Pipeline> {
    return outputToObservable(
      this.dialog.open(KeyHistoryDialog, { data: pipeline, maxWidth: '95vw' }).componentInstance
        .keyIssued,
    );
  }

  deletePipeline(pipeline: Pipeline): Observable<Pipeline> {
    return this.confirm({
      title: 'Delete the pipeline?',
      message:
        `The ${pipelineTypeName(pipeline.type)} pipeline of ${pipeline.serviceName} and its key history are deleted. ` +
        'Jenkins jobs using its key stop working.',
      confirmLabel: 'Delete pipeline',
      danger: true,
    }).pipe(
      switchMap(() =>
        this.api.delete(pipeline.id).pipe(
          map(() => pipeline),
          catchError((error) => this.failed(error)),
        ),
      ),
      tap(() => this.notifier.success('Pipeline deleted')),
    );
  }

  confirm(data: ConfirmDialogData): Observable<boolean> {
    return this.dialog
      .open<ConfirmDialog, ConfirmDialogData, boolean>(ConfirmDialog, { data })
      .afterClosed()
      .pipe(filter((confirmed) => confirmed === true));
  }

  openCode(data: CodeDialogData): void {
    this.dialog.open(CodeDialog, { data, width: '760px', maxWidth: '95vw' });
  }

  private openPipelineDialog(
    data: PipelineDialogData,
    message: (pipeline: Pipeline) => string,
  ): Observable<Pipeline> {
    return this.dialog
      .open<PipelineDialog, PipelineDialogData, Pipeline>(PipelineDialog, { data })
      .afterClosed()
      .pipe(
        filter((pipeline): pipeline is Pipeline => !!pipeline),
        tap((pipeline) => this.notifier.success(message(pipeline))),
      );
  }

  private issued(request: Observable<Pipeline>): Observable<Pipeline> {
    return request.pipe(catchError((error) => this.failed(error)));
  }

  private failed(error: unknown): Observable<never> {
    this.notifier.error(error);
    return EMPTY;
  }
}
