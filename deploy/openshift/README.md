# Running the portal as a container and on OpenShift

The portal ships as one container image: the Spring Boot jar with the Angular GUI inside, on the Red Hat UBI 9
OpenJDK 17 runtime. The image runs as a non-root user and also under the arbitrary UID (group 0) that OpenShift
assigns, so it fits the default `restricted-v2` security context constraint.

## Build the image

```bash
./gradlew :backend:bootJar
docker build -t dso-portal:0.1.0-SNAPSHOT .
```

The Dockerfile copies `backend/build/libs/dso-portal-<version>.jar`, so build the jar first (any Gradle command
that runs `bootJar`, such as `./gradlew build`, works). To build on an internal mirror of the base image, pass
`--build-arg BASE_IMAGE=<mirror>/ubi9/openjdk-17-runtime:<tag>`. Podman and Buildah accept the same arguments.

## Run the image locally

```bash
docker run --rm -p 8080:8080 dso-portal:0.1.0-SNAPSHOT
```

Without a profile the container starts the `local` profile: embedded H2 in `/application/data` with demo data, on
http://localhost:8080. Add `-e INFLUX_URL=... -e INFLUX_TOKEN=... -e GRAFANA_DASHBOARD_URL=...` to show the metrics
from your InfluxDB and Grafana.

## Deploy to OpenShift

The manifests in `deploy/openshift` are a Kustomize base (Deployment, Service, Route and a ConfigMap) with one
overlay per environment: `rd`, `qc` and `prod`, each starting the Spring profile of the same name.

1. Push the image to a registry the cluster can pull from, then point the overlay at it:

   ```bash
   cd deploy/openshift/overlays/rd
   kustomize edit set image dso-portal=<registry>/<project>/dso-portal:0.1.0-SNAPSHOT
   ```

2. Create the database secret in the target project. The values never go into the repository:

   ```bash
   oc create secret generic dso-portal-db \
     --from-literal=DB_URL='jdbc:oracle:thin:@//<host>:1521/<service>' \
     --from-literal=DB_USERNAME='<user>' \
     --from-literal=DB_PASSWORD='<password>'
   ```

   The InfluxDB token is optional, in its own secret:

   ```bash
   oc create secret generic dso-portal-influx --from-literal=INFLUX_TOKEN='<token>'
   ```

   The CyberTrack token is optional too, in its own secret:

   ```bash
   oc create secret generic dso-portal-cybertrack --from-literal=CYBERTRACK_TOKEN='<token>'
   ```

3. Set `INFLUX_URL` and the Grafana dashboard links in the overlay's `configMapGenerator` (add them next to
   `SPRING_PROFILES_ACTIVE`), with the `GRAFANA_2_*` links when a second Grafana instance holds dashboards too. The
   links must be reachable from the users' browsers, because the monitoring pages embed the dashboards. Set
   `CYBERTRACK_URL` to the Jira that holds the project SCP to create real secure coding tickets; empty keeps the demo.

4. Apply the overlay:

   ```bash
   oc apply -k deploy/openshift/overlays/rd
   oc rollout status deployment/dso-portal
   oc get route dso-portal
   ```

Liquibase updates the Oracle schema when the pod starts. The startup probe allows three minutes for that, and the
liveness and readiness probes use `/actuator/health/liveness` and `/actuator/health/readiness`. The route
terminates TLS at the edge and redirects plain HTTP. Memory is limited to 1 GiB with the heap at 75% of it; change
`resources` in an overlay patch if an environment needs more.

| Variable | Source | Purpose |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | ConfigMap | `rd`, `qc` or `prod` |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Secret `dso-portal-db` | Oracle connection |
| `DB_POOL_SIZE` | ConfigMap (optional) | Connection pool size, defaults per profile |
| `INFLUX_URL` | ConfigMap | Your InfluxDB with the DORA metrics DSOEnhanced writes |
| `INFLUX_ORG`, `INFLUX_BUCKET` | ConfigMap (optional) | Default to `DevSecOps` and `DORA-metrics`, as DSOEnhanced writes them |
| `INFLUX_TOKEN` | Secret `dso-portal-influx` (optional) | InfluxDB read token |
| `GRAFANA_DASHBOARD_URL` | ConfigMap (optional) | Link to the DSOEnhanced pipeline dashboard on your Grafana; without any Grafana link the pipeline page shows no dashboard |
| `GRAFANA_SECURITY_DASHBOARD_URL` | ConfigMap (optional) | Link to the DSOEnhanced security dashboard, used for SECURITY, SAST and NEXUS_IQ pipelines |
| `GRAFANA_NAME` | ConfigMap (optional) | Name of that Grafana instance on the pipeline page, `Grafana` by default |
| `GRAFANA_2_DASHBOARD_URL`, `GRAFANA_2_SECURITY_DASHBOARD_URL`, `GRAFANA_2_NAME` | ConfigMap (optional) | The same links and name (`Grafana 2` by default) for a second Grafana instance; the pipeline page shows both |
| `CYBERTRACK_URL` | ConfigMap (optional) | Base URL of the Jira that holds CyberTrack; empty creates demo secure coding tickets |
| `CYBERTRACK_PROJECT_KEY`, `CYBERTRACK_ISSUE_TYPE` | ConfigMap (optional) | The Jira project and issue type of a secure coding ticket, `SCP` and `Task` by default |
| `CYBERTRACK_TOKEN` | Secret `dso-portal-cybertrack` (optional) | Personal access token of a Jira user allowed to create issues in that project |
