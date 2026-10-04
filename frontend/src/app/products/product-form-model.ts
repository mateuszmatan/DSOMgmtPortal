import {
  AbstractControl,
  FormArray,
  FormControl,
  FormGroup,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import {
  BuildTool,
  DeployTarget,
  FieldProblem,
  Product,
  ProductRequest,
  ServiceRequest,
} from '../core/models';

/**
 * The reactive form of a product and its services. Its shape follows the API request, so a field problem
 * reported by the API (for example {@code services[2].build.javaPath}) points at the control it concerns.
 * The validators repeat the API's rules to give feedback while typing; the API stays the authority.
 */

export const PRODUCT_CODE = /^[A-Z][A-Z0-9_-]{1,49}$/;
export const SERVICE_NAME = /^[a-z0-9][a-z0-9._-]{0,99}$/;
export const UUID =
  /^\s*[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\s*$/;
export const HTTP_URL = /^https?:\/\/\S+$/;
export const METRICS_TAG = /^[A-Za-z0-9._-]*$/;
export const SONAR_KEY = /^([a-zA-Z0-9_.:-]*[a-zA-Z_.:-][a-zA-Z0-9_.:-]*)?$/;

/** Top-level keys the additional YAML may use, as the API accepts them. */
export const ADDITIONAL_CONFIG_KEYS = [
  'tests',
  'deploy',
  'build',
  'delivery',
  'coverage',
  'flutter',
  'jenkins',
  'includedDirs',
  'excludedDirs',
  'sca',
  'baseArtifactName',
];

const text = (value = '', ...validators: ValidatorFn[]) =>
  new FormControl(value, { nonNullable: true, validators });
const flag = (value: boolean) => new FormControl(value, { nonNullable: true });

export function createServiceForm(service?: Partial<ServiceRequest>) {
  const form = new FormGroup({
    id: new FormControl<number | null>(service?.id ?? null),
    name: text(service?.name ?? '', Validators.required, Validators.pattern(SERVICE_NAME)),
    description: text(service?.description ?? '', Validators.maxLength(2000)),
    build: new FormGroup({
      tool: new FormControl<BuildTool>(service?.build?.tool ?? 'GRADLE', { nonNullable: true }),
      sourceDir: text(service?.build?.sourceDir ?? '.', Validators.maxLength(500)),
      javaPath: text(
        service?.build?.javaPath ?? '',
        Validators.maxLength(500),
        requiredWhen((build) => build['tool'] !== 'FLUTTER' && !build['autoSetup']),
      ),
      autoSetup: flag(service?.build?.autoSetup ?? false),
    }),
    deployment: new FormGroup({
      target: new FormControl<DeployTarget>(service?.deployment?.target ?? 'VM', {
        nonNullable: true,
      }),
      appName: text(
        service?.deployment?.appName ?? '',
        Validators.maxLength(200),
        requiredWhen((deployment) => deployment['target'] === 'OPENSHIFT'),
      ),
      artifactName: text(
        service?.deployment?.artifactName ?? '',
        Validators.maxLength(300),
        requiredWhen((deployment) => deployment['target'] === 'OPENSHIFT'),
      ),
    }),
    appScan: new FormGroup({
      applicationId: text(
        service?.appScan?.applicationId ?? '',
        Validators.required,
        Validators.pattern(UUID),
      ),
      sastScanName: text(service?.appScan?.sastScanName ?? '', Validators.maxLength(200)),
      dastEnabled: flag(service?.appScan?.dastEnabled ?? false),
      dastTargetUrl: text(
        service?.appScan?.dastTargetUrl ?? '',
        Validators.pattern(HTTP_URL),
        Validators.maxLength(1000),
        requiredWhen((appScan) => !!appScan['dastEnabled']),
      ),
      dastPresenceId: text(service?.appScan?.dastPresenceId ?? '', Validators.maxLength(100)),
    }),
    sonar: new FormGroup({
      projectName: text(service?.sonar?.projectName ?? '', Validators.maxLength(200)),
      projectKey: text(
        service?.sonar?.projectKey ?? '',
        Validators.pattern(SONAR_KEY),
        Validators.maxLength(400),
      ),
    }),
    nexusIq: new FormGroup({
      application: text(service?.nexusIq?.application ?? '', Validators.maxLength(200)),
      scanPatterns: text((service?.nexusIq?.scanPatterns ?? []).join('\n'), maxLines(20)),
    }),
    scm: new FormGroup({
      repositoryUrl: text(
        service?.scm?.repositoryUrl ?? '',
        Validators.pattern(HTTP_URL),
        Validators.maxLength(1000),
      ),
      credentialsId: text(service?.scm?.credentialsId ?? '', Validators.maxLength(200)),
      goldenFixEnabled: flag(service?.scm?.goldenFixEnabled ?? true),
    }),
    metrics: new FormGroup({
      enabled: flag(service?.metrics?.enabled ?? true),
      influxProject: text(
        service?.metrics?.influxProject ?? '',
        Validators.pattern(METRICS_TAG),
        Validators.maxLength(200),
      ),
      influxEnv: text(
        service?.metrics?.influxEnv ?? 'test',
        Validators.pattern(METRICS_TAG),
        Validators.maxLength(50),
      ),
    }),
    additionalConfig: new FormGroup({
      yaml: text(service?.additionalConfig?.yaml ?? '', Validators.maxLength(100_000)),
    }),
  });
  revalidateOnChange(form.controls.build.controls.tool, form.controls.build.controls.javaPath);
  revalidateOnChange(form.controls.build.controls.autoSetup, form.controls.build.controls.javaPath);
  revalidateOnChange(
    form.controls.deployment.controls.target,
    form.controls.deployment.controls.appName,
    form.controls.deployment.controls.artifactName,
  );
  revalidateOnChange(
    form.controls.appScan.controls.dastEnabled,
    form.controls.appScan.controls.dastTargetUrl,
  );
  // The conditional validators read sibling controls, which do not exist yet when a control is created.
  [
    form.controls.build.controls.javaPath,
    form.controls.deployment.controls.appName,
    form.controls.deployment.controls.artifactName,
    form.controls.appScan.controls.dastTargetUrl,
  ].forEach((control) => control.updateValueAndValidity());
  return form;
}

export type ServiceForm = ReturnType<typeof createServiceForm>;

export function createProductForm() {
  return new FormGroup({
    code: text('', Validators.required, Validators.pattern(PRODUCT_CODE)),
    name: text('', Validators.required, Validators.maxLength(200)),
    description: text('', Validators.maxLength(4000)),
    ownerTeam: text('', Validators.maxLength(200)),
    contactEmail: text('', Validators.email, Validators.maxLength(320)),
    appScan: new FormGroup({
      keyId: text('', Validators.required, Validators.maxLength(200)),
      secretCredentialsId: text('', Validators.maxLength(200)),
    }),
    services: new FormArray<ServiceForm>([]),
  });
}

export type ProductForm = ReturnType<typeof createProductForm>;

/** Fills the form with a stored product, replacing its services. */
export function patchProduct(form: ProductForm, product: Product): void {
  form.patchValue({
    code: product.code,
    name: product.name,
    description: product.description ?? '',
    ownerTeam: product.ownerTeam ?? '',
    contactEmail: product.contactEmail ?? '',
    appScan: {
      keyId: product.appScan.keyId,
      secretCredentialsId: product.appScan.secretCredentialsId ?? '',
    },
  });
  form.controls.services.clear();
  product.services.forEach((service) => form.controls.services.push(createServiceForm(service)));
}

/**
 * A new service with the settings of an existing one. The values that must be unique across BBH, the
 * metrics project tag and the SonarQube project key, are left for the user to fill.
 */
export function duplicateService(source: ServiceForm): ServiceForm {
  const copy = createServiceForm(toServiceRequest(source));
  copy.patchValue({
    id: null,
    name: `${source.controls.name.value}-copy`.slice(0, 100),
    sonar: { projectKey: '' },
    metrics: { influxProject: '' },
  });
  return copy;
}

export function toProductRequest(form: ProductForm, version: number | null): ProductRequest {
  const value = form.getRawValue();
  return {
    code: value.code.trim(),
    name: value.name.trim(),
    description: optional(value.description),
    ownerTeam: optional(value.ownerTeam),
    contactEmail: optional(value.contactEmail),
    appScan: {
      keyId: value.appScan.keyId.trim(),
      secretCredentialsId: optional(value.appScan.secretCredentialsId),
    },
    version,
    services: form.controls.services.controls.map(toServiceRequest),
  };
}

export function toServiceRequest(form: ServiceForm): ServiceRequest {
  const v = form.getRawValue();
  const openShift = v.deployment.target === 'OPENSHIFT';
  return {
    id: v.id,
    name: v.name.trim(),
    description: optional(v.description),
    build: {
      tool: v.build.tool,
      sourceDir: optional(v.build.sourceDir),
      javaPath: optional(v.build.javaPath),
      autoSetup: v.build.autoSetup,
    },
    deployment: {
      target: v.deployment.target,
      appName: openShift ? optional(v.deployment.appName) : null,
      artifactName: openShift ? optional(v.deployment.artifactName) : null,
    },
    appScan: {
      applicationId: v.appScan.applicationId.trim(),
      sastScanName: optional(v.appScan.sastScanName),
      dastEnabled: v.appScan.dastEnabled,
      dastTargetUrl: optional(v.appScan.dastTargetUrl),
      dastPresenceId: optional(v.appScan.dastPresenceId),
    },
    sonar: { projectName: optional(v.sonar.projectName), projectKey: optional(v.sonar.projectKey) },
    nexusIq: {
      application: optional(v.nexusIq.application),
      scanPatterns: lines(v.nexusIq.scanPatterns),
    },
    scm: {
      repositoryUrl: optional(v.scm.repositoryUrl),
      credentialsId: optional(v.scm.credentialsId),
      goldenFixEnabled: v.scm.goldenFixEnabled,
    },
    metrics: {
      enabled: v.metrics.enabled,
      influxProject: optional(v.metrics.influxProject),
      influxEnv: optional(v.metrics.influxEnv),
    },
    additionalConfig: { yaml: optional(v.additionalConfig.yaml) },
  };
}

/**
 * Shows each field problem the API reported on its control, for example {@code services[2].build.javaPath};
 * returns the problems that match no control so they can be listed separately.
 */
export function applyFieldProblems(
  form: AbstractControl,
  problems: FieldProblem[],
): FieldProblem[] {
  const unmatched: FieldProblem[] = [];
  for (const problem of problems) {
    const control = controlAt(form, problem.field);
    if (control) {
      control.setErrors({ ...control.errors, server: problem.message });
      control.markAsTouched();
    } else {
      unmatched.push(problem);
    }
  }
  return unmatched;
}

/** The control a field path names, or the nearest one above it, for example a list element's list. */
export function controlAt(form: AbstractControl, field: string): AbstractControl | null {
  const path = field.split('.').flatMap((segment) => {
    const match = /^(\w+)\[(\d+)]$/.exec(segment);
    return match ? [match[1], Number(match[2])] : [segment];
  });
  for (let length = path.length; length > 0; length--) {
    const control = form.get(path.slice(0, length));
    if (control) {
      return control;
    }
  }
  return null;
}

/** Index of the first service holding a field problem, to open its panel. */
export function firstServiceWithProblem(problems: FieldProblem[]): number | null {
  const indexes = problems
    .map((problem) => /^services\[(\d+)]/.exec(problem.field))
    .filter((match): match is RegExpExecArray => match !== null)
    .map((match) => Number(match[1]));
  return indexes.length ? Math.min(...indexes) : null;
}

function optional(value: string | null | undefined): string | null {
  const trimmed = value?.trim();
  return trimmed ? trimmed : null;
}

function lines(value: string): string[] {
  return [
    ...new Set(
      value
        .split('\n')
        .map((line) => line.trim())
        .filter((line) => line.length > 0),
    ),
  ];
}

/**
 * Required while the sibling values meet the condition. The siblings are read from their controls, since the
 * group's value is updated only after a changed control has notified its listeners.
 */
function requiredWhen(condition: (siblings: Record<string, unknown>) => boolean): ValidatorFn {
  return (control) => {
    const group = control.parent as FormGroup | null;
    return group && condition(group.getRawValue()) ? Validators.required(control) : null;
  };
}

function maxLines(max: number): ValidatorFn {
  return (control) => (lines(control.value ?? '').length > max ? { maxLines: { max } } : null);
}

function revalidateOnChange(source: AbstractControl, ...targets: AbstractControl[]): void {
  source.valueChanges.subscribe(() => targets.forEach((target) => target.updateValueAndValidity()));
}
