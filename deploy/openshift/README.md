# Running the portal as a container and on OpenShift

The portal ships as one container image: the Spring Boot jar with the Angular GUI inside, on the Red Hat UBI 9
OpenJDK 21 runtime. The image runs as a non-root user and also under the arbitrary UID (group 0) that OpenShift
assigns, so it fits the default `restricted-v2` security context constraint.

## Build the image

```bash
./gradlew :backend:bootJar
docker build -t dso-portal:0.1.0-SNAPSHOT .
```

The Dockerfile copies `backend/build/libs/dso-portal-<version>.jar`, so build the jar first (any Gradle command
that runs `bootJar`, such as `./gradlew build`, works). To build on an internal mirror of the base image, pass
`--build-arg BASE_IMAGE=<mirror>/ubi9/openjdk-21-runtime:<tag>`. Podman and Buildah accept the same arguments.

## Run the image locally

```bash
docker run --rm -p 8080:8080 dso-portal:0.1.0-SNAPSHOT
```

Without a profile the container starts the `local` profile: embedded H2 in `/application/data` with demo data, on
http://localhost:8080. To run it next to the local InfluxDB and Grafana from `docker-compose.yml`:

```bash
./gradlew :backend:bootJar
docker compose --profile app up --build
```

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

3. Set `INFLUX_URL`, `INFLUX_ORG`, `INFLUX_BUCKET`, `GRAFANA_URL` and `GRAFANA_ORG_ID` in the overlay's
   `configMapGenerator` (add them next to `SPRING_PROFILES_ACTIVE`). `GRAFANA_URL` must be reachable from the
   users' browsers, because the monitoring pages embed Grafana panels.

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
| `INFLUX_URL`, `INFLUX_ORG`, `INFLUX_BUCKET` | ConfigMap | InfluxDB with the DORA metrics |
| `INFLUX_TOKEN` | Secret `dso-portal-influx` (optional) | InfluxDB read token |
| `GRAFANA_URL`, `GRAFANA_ORG_ID` | ConfigMap | Grafana for the embedded dashboards |
