import { pipeline, product, service, servicePipelines } from '../testing/fixtures';
import {
  WizardService,
  changesOf,
  fromService,
  jobName,
  pipelineChoices,
  pipelineNames,
  preparation,
  problemText,
  productRequest,
  reviewGroups,
  serviceRequest,
  serviceSummary,
  servicesToStart,
} from './self-service-model';

const APP_ID = '7d1f3a52-9c4b-4e8a-b2d6-0f5e1c9a8b31';

function added(overrides: Partial<WizardService> = {}): WizardService {
  return {
    id: null,
    name: 'gateway',
    description: 'Public payment API',
    appScanId: APP_ID,
    tool: 'GRADLE',
    target: 'VM',
    openShiftProject: '',
    ...overrides,
  };
}

describe('self-service model', () => {
  it('asks only what the chosen pipeline needs', () => {
    expect(preparation('SAST')).toHaveLength(3);
    expect(preparation('FULL')).toContain(
      'Whether each service runs on virtual machines or OpenShift',
    );
  });

  it('builds a Gradle service on a virtual machine with the automatic build tool set-up', () => {
    const request = serviceRequest(added(), 'SECURITY');

    expect(request.id).toBeNull();
    expect(request.name).toBe('gateway');
    expect(request.description).toBe('Public payment API');
    expect(request.appScan.applicationId).toBe(APP_ID);
    expect(request.build).toEqual(
      expect.objectContaining({ tool: 'GRADLE', autoSetup: true, javaPath: null, buildPath: null }),
    );
    expect(request.build.command.tasks).toEqual(['clean', 'build']);
    expect(request.deployment.target).toBe('VM');
    expect(request.delivery.tasks).toEqual([]);
    expect(request.openShiftTargets).toEqual({});
  });

  it('publishes a Maven service on a virtual machine from target with deploy-file', () => {
    const request = serviceRequest(added({ tool: 'MAVEN' }), 'FULL');

    expect(request.build.command.tasks).toEqual(['clean', 'verify']);
    expect(request.build.buildPath).toBe('target/*.jar');
    expect(request.delivery.tasks).toEqual(['deploy:deploy-file']);
  });

  it('names everything of an OpenShift service after its project', () => {
    const request = serviceRequest(
      added({
        name: 'Gateway',
        tool: 'MAVEN',
        target: 'OPENSHIFT',
        openShiftProject: 'pay-payhub',
      }),
      'FULL',
    );

    expect(request.deployment).toEqual(
      expect.objectContaining({
        target: 'OPENSHIFT',
        appName: 'Gateway',
        artifactName: 'Gateway.jar',
      }),
    );
    expect(request.delivery.tasks).toEqual([]);
    expect(request.openShiftTargets.RD).toEqual(
      expect.objectContaining({
        projectBuild: 'pay-payhub-build',
        projectDeployment: 'pay-payhub-rd',
        buildContext: 'target/docker',
        dockerRepoPush: 'docker-qc.tools.bbh.com/pay-payhub/gateway',
        nexusAuthFile: '/home/jenkins/.docker/nexus-auth.json',
      }),
    );
    expect(request.openShiftTargets.QC).toEqual(
      expect.objectContaining({
        projectDeployment: 'pay-payhub-qc',
        configPath: 'openshift/config-qc.yaml',
      }),
    );
  });

  it('keeps a static scan service on virtual machines whatever was chosen before', () => {
    const request = serviceRequest(
      added({ target: 'OPENSHIFT', openShiftProject: 'pay-payhub' }),
      'SAST',
    );

    expect(request.deployment.target).toBe('VM');
    expect(request.openShiftTargets).toEqual({});
    expect(serviceRequest(added({ target: null }), 'SAST').deployment.target).toBe('VM');
  });

  it('changes only the name, description and AppScan application of a service in the portal', () => {
    const stored = service();
    const request = serviceRequest(
      { ...fromService(stored), name: 'web', description: '', appScanId: APP_ID },
      'SAST',
      stored,
    );

    expect(request.id).toBe(stored.id);
    expect(request.name).toBe('web');
    expect(request.description).toBeNull();
    expect(request.appScan.applicationId).toBe(APP_ID);
    expect(request.build).toEqual(stored.build);
    expect(request.deployment).toEqual(stored.deployment);
    expect(request.testJobs).toEqual(stored.testJobs);
  });

  it('puts a service of the portal on another build tool with the build settings of a new one', () => {
    const stored = service();
    const request = serviceRequest({ ...fromService(stored), tool: 'MAVEN' }, 'FULL', stored);

    expect(request.build).toEqual(
      expect.objectContaining({
        tool: 'MAVEN',
        sourceDir: '.',
        javaPath: null,
        autoSetup: true,
        buildPath: 'target/*.jar',
      }),
    );
    expect(request.build.command.tasks).toEqual(['clean', 'verify']);
    expect(request.build.command.flags).toEqual([]);
    expect(request.delivery.tasks).toEqual(['deploy:deploy-file']);
    expect(request.deployment).toEqual(stored.deployment);
    expect(request.urbanCodeApplications).toEqual(stored.urbanCodeApplications);
    expect(request.unitTests).toEqual(stored.unitTests);
    expect(request.testJobs).toEqual(stored.testJobs);
  });

  it('moves a service of the portal to OpenShift with the deployment settings of a new one', () => {
    const stored = service();
    const request = serviceRequest(
      { ...fromService(stored), target: 'OPENSHIFT', openShiftProject: 'cert-gui' },
      'SECURITY',
      stored,
    );

    expect(request.build).toEqual(stored.build);
    expect(request.deployment).toEqual({
      target: 'OPENSHIFT',
      appName: 'gui',
      artifactName: 'gui.jar',
      baseArtifactName: null,
    });
    expect(request.openShiftTargets.RD?.projectDeployment).toBe('cert-gui-rd');
    expect(request.sshTargets).toEqual({});
    expect(request.testJobs).toEqual(stored.testJobs);
  });

  it('moves a service of the portal from OpenShift back to virtual machines', () => {
    const stored = service({
      deployment: {
        target: 'OPENSHIFT',
        appName: 'gui',
        artifactName: 'gui.jar',
        baseArtifactName: null,
      },
    });
    const request = serviceRequest({ ...fromService(stored), target: 'VM' }, 'FULL', stored);

    expect(request.deployment).toEqual({
      target: 'VM',
      appName: null,
      artifactName: null,
      baseArtifactName: null,
    });
    expect(request.openShiftTargets).toEqual({});
    expect(request.build).toEqual(stored.build);
  });

  it('reads a service of the portal back for the wizard', () => {
    expect(fromService(service({ description: null }))).toEqual({
      id: 10,
      name: 'gui',
      description: '',
      appScanId: service().appScan.applicationId,
      tool: 'GRADLE',
      target: 'VM',
      openShiftProject: '',
    });
  });

  it('makes a new product from the answers, leaving empty answers out', () => {
    const request = productRequest(
      {
        departmentId: 5,
        name: ' Payments Hub ',
        code: 'PAYMENTSHUB',
        ownerTeam: ' ',
        contactEmail: 'pay@bbh.com',
        appScanKeyId: ' bbh_key ',
      },
      [added()],
      'SECURITY',
    );

    expect(request).toEqual(
      expect.objectContaining({
        code: 'PAYMENTSHUB',
        name: 'Payments Hub',
        description: null,
        ownerTeam: null,
        contactEmail: 'pay@bbh.com',
        departmentId: 5,
        appScan: { keyId: 'bbh_key', secretCredentialsId: null },
        version: null,
      }),
    );
    expect(request.services.map((entry) => entry.name)).toEqual(['gateway']);
  });

  it('keeps every detail of a product in the portal and adds the new services after its own', () => {
    const stored = product();
    const request = productRequest(stored, [fromService(stored.services[0]), added()], 'FULL', 5);

    expect(request).toEqual(
      expect.objectContaining({
        code: 'CERT',
        name: 'CertScanner',
        description: 'TLS certificate scanner',
        ownerTeam: 'Technology Architecture',
        departmentId: 3,
        appScan: stored.appScan,
        version: 3,
      }),
    );
    expect(request.services.map((entry) => [entry.id, entry.name])).toEqual([
      [10, 'gui'],
      [null, 'gateway'],
    ]);
  });

  it('puts a product in the portal without a department into the chosen one', () => {
    const stored = product({ departmentId: null });

    expect(productRequest(stored, [], 'SAST', 5).departmentId).toBe(5);
    expect(productRequest(stored, [], 'SAST').departmentId).toBeNull();
  });

  it('names the service a problem of the portal belongs to', () => {
    const services = [added(), added({ name: 'ledger' })];

    expect(
      problemText(
        { field: 'services[1].appScan.applicationId', message: 'belongs to CertScanner' },
        services,
      ),
    ).toBe('ledger, AppScan application ID: belongs to CertScanner');
    expect(
      problemText({ field: 'services[4].build.javaPath', message: 'is required' }, services),
    ).toBe('A service, build.javaPath: is required');
    expect(problemText({ field: 'code', message: 'is taken' }, services)).toBe(
      'product code: is taken',
    );
  });

  it('sums up a service for the chosen pipeline', () => {
    const openShift = added({ target: 'OPENSHIFT', openShiftProject: 'pay-payhub' });

    expect(serviceSummary('FULL', openShift)).toBe(
      'Gradle · runs on OpenShift · project pay-payhub',
    );
    expect(serviceSummary('SAST', openShift)).toBe('Gradle');
    expect(serviceSummary('SAST', fromService(service()))).toBe(
      'Gradle · runs on Virtual machines',
    );
    expect(
      serviceSummary(
        'SECURITY',
        fromService(service({ build: { ...service().build, tool: 'FLUTTER' } })),
      ),
    ).toBe('Flutter · runs on Virtual machines');
  });

  it('says what changes for a service of the portal', () => {
    const stored = service();
    const same = fromService(stored);

    expect(changesOf({ ...same, appScanId: same.appScanId.toUpperCase() }, stored)).toEqual([]);
    expect(
      changesOf(
        {
          ...same,
          name: 'web',
          description: '',
          appScanId: APP_ID,
          tool: 'MAVEN',
          target: 'OPENSHIFT',
          openShiftProject: 'cert-web',
        },
        stored,
      ),
    ).toEqual([
      'renamed from gui',
      'no description',
      'new AppScan application ID',
      'built with Maven instead of Gradle, with the default build settings',
      'runs on OpenShift in project cert-web instead of Virtual machines, with the default deployment settings',
    ]);
    expect(changesOf({ ...same, description: 'Web front end' }, stored)).toEqual([
      'new description',
    ]);
  });

  it('groups the services for the review', () => {
    const stored = [
      service(),
      service({ id: 11, name: 'api' }),
      service({ id: 12, name: 'batch' }),
    ];
    const [gui, api, batch] = stored.map(fromService);

    expect(
      reviewGroups('SECURITY', [gui, { ...api, tool: 'MAVEN' }, batch, added()], stored, [12]),
    ).toEqual([
      {
        label: 'Added',
        services: [{ name: 'gateway', text: 'Gradle · runs on Virtual machines' }],
      },
      {
        label: 'Changed',
        services: [
          {
            name: 'api',
            text: 'built with Maven instead of Gradle, with the default build settings',
          },
        ],
      },
      {
        label: 'Removed',
        services: [{ name: 'batch', text: 'Gradle · runs on Virtual machines' }],
      },
      {
        label: 'Unchanged',
        services: [{ name: 'gui', text: 'Gradle · runs on Virtual machines' }],
      },
    ]);
  });

  it('names the pipelines of a service and counts the services that have each one', () => {
    const services = [
      servicePipelines({
        pipelines: [
          pipeline({ type: 'SAST' }),
          pipeline({ type: 'EXTENDED' }),
          pipeline(),
          pipeline({ type: 'SECURITY' }),
        ],
      }),
      servicePipelines({ serviceName: 'api', pipelines: [pipeline({ type: 'SECURITY' })] }),
    ];

    expect(pipelineNames(services[0])).toBe('Full, Security, Extended, Static scan');
    expect(pipelineNames(servicePipelines({ pipelines: [] }))).toBe('');
    expect(pipelineChoices(services).map((choice) => [choice.label, choice.note])).toEqual([
      ['Static scan', '1 of 2 services has it'],
      ['Security', 'Every service has it'],
      ['Full', '1 of 2 services has it'],
    ]);
    expect(pipelineChoices([]).map((choice) => choice.note)).toEqual([
      'No service has it yet',
      'No service has it yet',
      'No service has it yet',
    ]);
    expect(pipelineChoices(null).every((choice) => choice.note === undefined)).toBe(true);
  });

  it('finds the pipeline of the chosen type for every service', () => {
    const security = pipeline({ id: 101, type: 'SECURITY' });
    const starts = servicesToStart(
      [
        servicePipelines({ serviceName: 'gui', pipelines: [pipeline(), security] }),
        servicePipelines({ serviceName: 'api', pipelines: [pipeline()] }),
      ],
      'SECURITY',
    );

    expect(starts).toEqual([
      { serviceName: 'gui', pipeline: security },
      { serviceName: 'api', pipeline: null },
    ]);
    expect(jobName(security)).toBe('DevSecOps/CERT/gui-security');
  });
});
