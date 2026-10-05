# Portal frontend

The Angular app of the BBH DevSecOps Management Portal. See the [main README](../README.md) for the whole
portal.

```bash
./gradlew :gui:buildGui         # production bundle in gui/build/dist/browser, checked against the angular.json budgets
./gradlew :gui:unitTest         # Vitest unit tests with coverage, thresholds in angular.json
./gradlew :gui:smokeTest        # Spock and Playwright: every page in a browser against recorded API answers
./gradlew :gui:regressionTest   # Spock and Playwright: user journeys against a stub API, with the requests they send
./gradlew :gui:performanceTest  # a generated catalogue of 25 products x 16 services, timed in the browser
./gradlew :gui:check            # all of the above
cd gui && npm start             # http://localhost:4200, /api is proxied to the backend on port 8080
```

Gradle downloads Node.js 24 into `gui/.gradle/nodejs`.

| Folder        | Holds |
|---------------|-------|
| `core/`       | API clients, models mirroring the backend DTOs, error handling, the four portal sections |
| `products/`   | Product Management: product list, product editor, product page with pipelines and keys |
| `monitoring/` | Pipeline Monitoring: overview, product pipelines, pipeline details with DORA and Grafana |
| `evidence/`   | Change Evidence: builds, tests and scans of each pipeline for ServiceNow changes |
| `settings/`   | Global Settings: tools, policy and defaults of every pipeline |
| `shared/`     | field definitions and the field component, form controls, dialogs, formatting, Bitbucket links |
| `testing/`    | fixtures for the unit tests |

## Forms from field definitions

A form field is written once, as data. `shared/fields.ts` holds the `Field` interface (key, label, the
`config.yaml` path shown as its hint, span of the 12 column grid, kind, placeholder, hint, error message, select
options and number range) with the small builders `line`, `mono`, `area`, `check`, `count` and `choice`, and the
`dso-fields` component that renders a list of them into a `.form-fields` grid: a Material field or checkbox per
entry, bound to the control of that key in the group it is given. The service editor
(`products/service-fields.ts`), its child editors and the Global Settings page (`settings/settings-fields.ts`)
therefore describe their sections as lists of fields; only the parts that are not a plain field (the tool command
blocks, the test job list, the UrbanCode applications, the OpenShift targets and the toggle groups) have markup of
their own. Labels and hints go through `chips()`, which turns `` `path` `` into a code chip.

The service editor's vertical menu, the panes it opens and the validity of each section come from
`SERVICE_SECTIONS` in `products/product-form-model.ts`; the explanation under each pane heading sits next to the
field definitions. Requests are mapped with `sent()`, which trims every string of a form value and sends a blank
one as `null`, so each request mapping only names the values that need more than that (lists split into lines or
words, values kept while they are valid, and the sections that do not apply).

## Browser tests

The smoke, regression and performance suites are Spock specifications that drive Chromium with Playwright against
the production build. `GuiServer` serves `build/dist/browser` and answers `/api` from `StubApi`, which replays the
API answers recorded from the backend (`src/testFixtures/resources/.../api`) and records every request, so a
specification checks both what the page shows and the exact JSON the gui sends. Screenshots of the last state of
every feature land in `build/reports/gui/screenshots`.

- Regression (`src/regressionTest`): searching and opening products; adding a product with two services, through
  the browser's required-field checks and the server's field errors, to the request it sends; saving unchanged
  products, which sends back what was loaded with its version; the unsaved-changes guard; moving, duplicating and
  removing services; Bitbucket fields, GoldenFix default and test job parameters; adding pipelines, replacing,
  invalidating and regenerating keys, and the keys generated for new services; config previews and their problem
  details; global settings with a version conflict; change evidence and its ServiceNow text; monitoring ranges,
  Jenkins and build links, InfluxDB missing or unreachable; and failing API calls.
- Performance (`src/performanceTest`): the stub serves 25 products with 16 services and 4 pipelines each, with
  monitoring and evidence for all of them. The suite times the cold product list, the product page, the editor and
  a service expansion, the monitoring overview, a product's monitoring and a product's evidence in the browser, one
  warm-up and five measured runs each, and fails when a p95 passes its limit or the initial bundle passes the
  `angular.json` warning budget. `-Dperformance.factor=2` doubles the time limits on a slow machine. The report is
  `build/reports/performance/gui-performance-report.md`.

## Look and layout

- The top menu is one compact line with short labels: Product Management, Pipeline Monitoring, Change Evidence and
  Global Settings. Each page starts with the full name of its section as the heading and the section description
  under it.
- The pages show no icons. The only icons are those of the vertical section menu in the service editor.
- Fields are compact (Material density -4, 32px inputs). Forms reflow to two columns below 760px and to one below
  480px; the menu wraps and wide tables scroll inside their own container, so no page scrolls sideways at 800px or
  600px.
- On/off settings are checkboxes and expandable panels say Show or Hide.

## Bitbucket repository

The Bitbucket section of a service sets `scm.bitbucket.apiUrl`, `workspace`, `projectKey` and `repoSlug` next to
the repository URL. They are optional: the pipeline reads them from the repository URL when they are empty. The
product page links each service to its repository: the repository URL when set, otherwise
`https://bitbucket.org/{workspace}/{repoSlug}` for Bitbucket Cloud or `{apiUrl}/projects/{projectKey}/repos/{repoSlug}`
for Data Center (`users/` for a personal `~` project key).

## Pipeline keys

The API sends each key with a `hint`: its first 8 characters, `…` and its last 4. The product page shows the active
key by its hint; Show and Copy use the key `value`, which the product management endpoints send for the active key
only. The key history lists every key by its hint. The monitoring endpoints send no key values at all, so the
monitoring pages only check whether a pipeline has an active key.

Saving a product gives every new service a full pipeline with an active key; the backend creates them in the same
save. The editor remembers which services were new (`GeneratedKeys`, in memory only), and the product page it opens
next says "Pipeline keys generated for 2 new services" (or names the one new service), marks those services as New
and shows their keys in full. The notice appears once: reloading the page or opening it from the list shows none.

A pipeline whose key was invalidated shows a "Regenerate key" text button next to its key status, on the product page
and in the key history dialog. It calls `POST /api/pipelines/{id}/keys` without a confirmation, since no working key
is replaced, and shows the new key; the invalidated keys stay in the history. "Replace key" in the More menu of a
pipeline with an active key still asks first, because it invalidates the key in use.

## Grafana

The monitoring API sends one `grafana.dashboardUrl` for a pipeline, with the dashboard and its variables already
set. The pipeline page embeds it in an iframe with `&kiosk` and links the same address as "Open in Grafana". When
the backend has no `GRAFANA_DASHBOARD_URL` the field is `null` and the page says so instead of embedding anything.

## Build links

Every run carries the `buildUrl` the backend builds from the job that recorded the run (its `JOB_NAME`, so a branch of
a multibranch job links to that branch) and falls back to the pipeline's configured Jenkins job. The monitoring pages
link each build number to that address and show the number without a link when it is `null`. The change evidence
uses the build, report, test and artifact links the API sends.

## GoldenFix switch of a service

"Run GoldenFix" in the GoldenFix section of a service is a select with Global default, On and Off. Global default
sends `goldenFix.enabled: null`, so the service follows "GoldenFix runs by default" of the Global Settings, and is
where every new service starts. Selects whose first option means "not set" (Global default, Library default,
Detected from the URL, Global value) show that option for a `null` value.

## Test job parameters

The parameters of a test job are a text area with one `NAME=value` per line, the format the DevSecOps library splits
on. Every non-blank line must match `^[A-Za-z_][A-Za-z0-9_.-]*=.*$`, and the stored text is sent back unchanged.

## Validation mirrored from the backend

The forms check on the visible fields what the backend checks, so a save the API would refuse is caught before it is
sent. The API problems still land on their fields when a rule only the backend knows fails.

- Build: the JDK path is required unless a Gradle or Maven build sets it up automatically. A Flutter build always
  needs it, for SonarQube, so automatic setup is off for Flutter. A Maven service deployed to virtual machines needs
  the build path, which the Nexus delivery reads.
- Nexus IQ: the application and the scan patterns are set together or not at all.
- Flutter: at least one module and one test module; the delivery group, artifact and plugin on virtual machines.
- UrbanCode: every application has at least one component, and every component its base folder and include patterns.
- OpenShift: the RD region needs the image build fields (build project, BuildConfig file, Dockerfile, build context),
  the image push target and the Nexus auth file.
- Global Settings: the minimum line coverage is 1 to 100 and the release gate checks at least one scanner.
- Lists: besides the number of entries and the length of each, the entries joined as stored must fit their column
  (for example 2000 characters for build flags, 4000 for variables, 1000 for agent labels).

Fields that do not apply are disabled, so they never block a save without showing why: the DAST fields while DAST is
off, the AppScan compile command while compiling is off, the Maven home of a Gradle build, the remote Jenkins fields of
a test job that runs on this Jenkins, and the job of the other pipeline type in the pipeline dialog. A hidden value is
kept and sent only while it is valid.

## Values unique within a product

Like the backend, the product editor wants each service name, SonarQube project key and metrics project with its
environment (the project defaults to `{code}-{name}`) used once within the product, compared without case. The later
service gets the error, with the backend's message, and it clears as soon as either service changes. A problem the
API reports for one of these fields stays on the field only until anything in the product changes, so fixing the
conflict on the other service is enough to save again.
