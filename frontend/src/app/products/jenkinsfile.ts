import { DEFAULT_JENKINS_LIBRARY, Pipeline } from '../core/models';

/**
 * The whole Jenkinsfile of a pipeline once the DevSecOps library reads its configuration from the portal: it
 * loads the shared library under the name the global settings give it and passes the pipeline's key.
 */
export function jenkinsfile(pipeline: Pipeline, library: string | null | undefined): string {
  const key = pipeline.activeKey?.value ?? '<issue a new key first>';
  const name = library?.trim() || DEFAULT_JENKINS_LIBRARY;
  return `@Library('${name}') _\n\n${pipeline.entryPoint}(pipelineKey: '${key}')\n`;
}
