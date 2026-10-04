import { DEFAULT_JENKINS_LIBRARY, Pipeline } from '../core/models';

export function jenkinsfile(pipeline: Pipeline, library: string | null | undefined): string {
  const key = pipeline.activeKey?.value ?? '<issue a new key first>';
  const name = library?.trim() || DEFAULT_JENKINS_LIBRARY;
  return `@Library('${name}') _\n\n${pipeline.entryPoint}(pipelineKey: '${key}')\n`;
}
