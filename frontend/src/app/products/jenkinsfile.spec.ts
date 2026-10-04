import { pipeline } from '../testing/fixtures';
import { jenkinsfile } from './jenkinsfile';

describe('jenkinsfile', () => {
  it('loads the library the global settings name and passes the active key', () => {
    expect(jenkinsfile(pipeline({ entryPoint: 'devSecOpsSecurityPipeline' }), 'BBHLibrary')).toBe(
      "@Library('BBHLibrary') _\n\n" +
        "devSecOpsSecurityPipeline(pipelineKey: '6f1c2d3e-0000-4abc-9def-123456789abc')\n",
    );
  });

  it('falls back to the default library name when the settings give none', () => {
    expect(jenkinsfile(pipeline(), null)).toContain("@Library('DevSecOpsJenkinsLibrary') _");
    expect(jenkinsfile(pipeline(), '  ')).toContain("@Library('DevSecOpsJenkinsLibrary') _");
  });

  it('asks for a new key when the pipeline has none', () => {
    expect(jenkinsfile(pipeline({ activeKey: null }), 'DevSecOpsJenkinsLibrary')).toContain(
      "devSecOpsPipeline(pipelineKey: '<issue a new key first>')",
    );
  });
});
