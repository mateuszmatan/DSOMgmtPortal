# Examples

Jenkinsfiles, REST calls and a rendered configuration for the DSO portal. The API scripts run against a portal
started as in [Running it locally](../README.md#running-it-locally), whose [demo data](../README.md#demo-data) they
use. Every pipeline key in these files is a placeholder: take the real one from the product page, or from
`api/pipeline-key.sh`.

| File | What it shows |
|------|---------------|
| [jenkins/full-pipeline.Jenkinsfile](jenkins/full-pipeline.Jenkinsfile) | a service's full pipeline: `devSecOpsPipeline` with one key |
| [jenkins/security-pipeline.Jenkinsfile](jenkins/security-pipeline.Jenkinsfile) | a security pipeline: `devSecOpsSecurityPipeline` |
| [jenkins/extended-pipeline.Jenkinsfile](jenkins/extended-pipeline.Jenkinsfile) | an extended pipeline: `devSecOpsExtendedPipeline` |
| [jenkins/sast-pipeline.Jenkinsfile](jenkins/sast-pipeline.Jenkinsfile) | a SAST scanning pipeline: `devSecOpsSASTScanningPipeline` |
| [jenkins/multi-service/gateway.Jenkinsfile](jenkins/multi-service/gateway.Jenkinsfile) | the full pipeline of Payments Hub `gateway` |
| [jenkins/multi-service/ledger.Jenkinsfile](jenkins/multi-service/ledger.Jenkinsfile) | the full pipeline of Payments Hub `ledger` |
| [jenkins/multi-service/payhub-full.Jenkinsfile](jenkins/multi-service/payhub-full.Jenkinsfile) | one full run that builds every Payments Hub service, `gateway` first |
| [api/list-departments.sh](api/list-departments.sh) | the departments with their product, service and pipeline counts |
| [api/list-products.sh](api/list-products.sh) | the products, optionally filtered by a search text |
| [api/create-product.sh](api/create-product.sh) with [api/new-product.json](api/new-product.json) | a new product with two services, as the onboarding wizard creates it |
| [api/product-pipelines.sh](api/product-pipelines.sh) | the services of a product with their pipelines and active keys |
| [api/pipeline-key.sh](api/pipeline-key.sh) | the active key of one pipeline, found by product code, service and type |
| [api/rotate-key.sh](api/rotate-key.sh) | a new key for a pipeline |
| [api/revoke-key.sh](api/revoke-key.sh) | invalidating a pipeline's key with a reason |
| [api/dso-config.sh](api/dso-config.sh) | the configuration the library reads for a key, as JSON and as YAML |
| [api/monitoring.sh](api/monitoring.sh) | the monitoring status, every product, all pipelines together, one product and one pipeline |
| [api/change-evidence.sh](api/change-evidence.sh) | the change evidence of a product |
| [config/payhub-gateway-full.json](config/payhub-gateway-full.json), [.yaml](config/payhub-gateway-full.yaml) | the configuration of the demo pipeline Payments Hub `gateway`, full |

## Jenkinsfiles

Each file is the whole Jenkinsfile of one Jenkins job, in the form the onboarding wizard and the product page
generate: the `@Library` line loads the shared library named in the Global Settings (`platform.jenkinsLibrary`,
`DevSecOpsJenkinsLibrary` by default), and the entry point of the pipeline type receives the pipeline key as a string.
Nothing else is in the file: no `config.yaml`, no credentials ID and no tool server. The library reads everything else
from the portal by the key, at `DSO_PORTAL_URL`, as described in
[How the library reads its configuration](../README.md#how-the-library-reads-its-configuration). During the cutover,
pin the portal-integrated version in the `@Library` line and in the Global Settings, for example
`DevSecOpsJenkinsLibrary@DSOwithMgmtPortal`.

Save a file as `Jenkinsfile` in the top folder of the service's Bitbucket repository and point a Pipeline job (Pipeline
script from SCM) at it. A service with pipelines of several types has one job per type, and the Jenkinsfile of each
job passes the key of its own pipeline. The demo data names these jobs `DevSecOps/<CODE>/<service>-<type>`, for example
`DevSecOps/PAYHUB/gateway-full`.

- `full-pipeline.Jenkinsfile` runs `devSecOpsPipeline`: build, scans, tests, deployment to RD and QC, and release.
- `security-pipeline.Jenkinsfile` runs `devSecOpsSecurityPipeline`: build and security scans; when the pipeline names
  an extended pipeline job in the portal, it starts that job.
- `extended-pipeline.Jenkinsfile` runs `devSecOpsExtendedPipeline`: the deployment and tests that the security
  pipeline starts. It reads the security run's state, so the first extended run after the cutover needs one security
  build made by the portal-integrated library.
- `sast-pipeline.Jenkinsfile` runs `devSecOpsSASTScanningPipeline`: an AppScan static scan of the sources only, with
  nothing built or deployed.
- `multi-service/gateway.Jenkinsfile` and `multi-service/ledger.Jenkinsfile` are the full pipelines of two services of
  the demo product Payments Hub, each in its own repository with its own key. The key in `gateway.Jenkinsfile`
  (`00000000-0000-4000-8000-000000000000`) stands for the pipeline whose configuration `config/` shows.
- `multi-service/payhub-full.Jenkinsfile` is the Jenkinsfile the product page offers as "Jenkinsfile for several
  services": `pipelineKeys` lists the keys of the full pipelines of `gateway`, `ledger`, `notifications` and
  `mobile-app`, the primary service first. One run builds them all. Extended pipelines join such a run only when they
  name the same security pipeline.

The keys stop working when they are invalidated or replaced: the next build of the job is refused with 403 and the
Jenkinsfile needs the pipeline's new key.

## API scripts

The scripts need bash, `curl` 7.76 or later and `jq`. Each one reads `PORTAL_URL` (default `http://localhost:8080`)
and prints the answer through `jq` (YAML as it comes). When the portal refuses a request, the script prints the
problem detail it sent and exits with a non-zero status.

```bash
export PORTAL_URL=http://localhost:8080
examples/api/list-departments.sh
examples/api/list-products.sh PAYHUB
PRODUCT_ID=$(examples/api/list-products.sh PAYHUB | jq '.[0].id')
examples/api/product-pipelines.sh "$PRODUCT_ID"
KEY=$(examples/api/pipeline-key.sh PAYHUB gateway FULL)
examples/api/dso-config.sh "$KEY"
```

- `list-departments.sh` calls `GET /api/departments`.
- `list-products.sh [SEARCH]` calls `GET /api/products?search=`; the search also matches the department name.
- `create-product.sh [DEPARTMENT]` looks up the department's ID by name (`Custody` by default), puts it into
  `new-product.json` and posts that to `POST /api/products?pipelineType=FULL`, as the onboarding wizard does after the
  choice of a full pipeline. The body is the product Trade Archive with `archive-api`, a Maven build on OpenShift with
  the RD and QC targets the wizard derives from the OpenShift project `cus-archive`, and `archive-gui`, a Gradle build
  on virtual machines. It carries the values the wizard fills in; the fields the wizard sends empty (`null`, `[]` or
  their defaults) are left out, which the API reads the same way. Each service gets a full pipeline with an active key.
  A second run answers 409, because the product code is taken.
- `product-pipelines.sh PRODUCT_ID` calls `GET /api/products/{id}/pipelines` and shows each pipeline's ID, type,
  entry point, Jenkins job and active key.
- `pipeline-key.sh CODE SERVICE [TYPE]` finds the product by its code and prints the active key of the service's
  pipeline of that type (`FULL` by default), for use in the other scripts.
- `rotate-key.sh PIPELINE_ID` calls `POST /api/pipelines/{id}/keys`. An active key is invalidated with the reason
  "Replaced by a new key"; on a pipeline whose key was invalidated this regenerates it.
- `revoke-key.sh PIPELINE_ID REASON` calls `POST /api/pipelines/{id}/keys/revoke` with `{"reason": ...}`. The pipeline
  is disabled until a new key is issued; a pipeline without an active key answers 409.
- `dso-config.sh KEY` calls `GET /api/dso/config/{key}?format=json` and `GET /api/dso/config/{key}`, the requests the
  library sends, and records the key's last use. A revoked key answers 403, an unknown one 404.
- `monitoring.sh PRODUCT_ID PIPELINE_ID [RANGE]` calls `GET /api/monitoring/status`, `/products`,
  `/activity?range=`, `/products/{id}` and `/pipelines/{id}?range=` (`30d` by default) and shortens each answer.
- `change-evidence.sh PRODUCT_ID` calls `GET /api/evidence/products/{id}`.

`create-product.sh`, `rotate-key.sh` and `revoke-key.sh` change the portal's data. On the demo data, rotating or
revoking a key changes nothing in Jenkins, since no job uses those keys.

## Configuration sample

`config/payhub-gateway-full.json` and `config/payhub-gateway-full.yaml` are what the library received for the full
pipeline of Payments Hub `gateway`, generated from a freshly started demo portal with the two requests of
`dso-config.sh`. The JSON is the same document as the YAML, pretty-printed; the key itself does not appear in either.
The document has four parts: `pipeline` (type, entry point, product, the service in `projectNames` and the agent
labels), `platform` and `defaults` (from the Global Settings) and `projects.gateway` (the service's build, scans, tests,
OpenShift targets, Bitbucket repository and InfluxDB tags). It names Jenkins credentials IDs, never their secrets.
`platform.jenkinsUrl` is the URL the demo data sets. Run `api/dso-config.sh` to see the configuration of the portal
you run.

## Screenshots

`screenshots/v1` to `screenshots/v9` hold screenshots of the GUI, one folder per version: v7 shows the departments,
v8 the ten demo integrations with the charts of Product Management and Pipeline Monitoring, and v9 the ServiceNow
production change raised from Beadle.
