# DevSecOps Management Portal GUI

The Angular app of the BBH DevSecOps Management Portal, one of the three projects of the Angular workspace in the
repository root (`angular.json`), next to [common-gui](../common-gui/README.md), the code it shares with Beadle, and
[beadle-gui](../beadle-gui/README.md). See the [main README](../README.md) for the whole portal.

```bash
./gradlew installNpmCIDeps            # npm ci from package-lock.json, for every GUI project
./gradlew installDesignSystem         # the BBH Design System packages from the BBH npm registry, inside BBH only
./gradlew :dso-gui:buildAngular       # production bundle in dso-gui/dist, checked against the angular.json budgets
./gradlew :dso-gui:testAngular        # Vitest unit tests with coverage, thresholds in angular.json
./gradlew :dso-gui:smokeTest          # Spock and Playwright: every page in a browser against a stub API
./gradlew :dso-gui:regressionTest     # Spock and Playwright: user journeys against a stub API, with the requests they send
./gradlew :dso-gui:performanceTest    # a generated catalogue of 25 products x 16 services, timed in the browser
./gradlew :dso-gui:check              # all of the above
npm start                             # http://localhost:4200, /api is proxied to the portal service on port 8080
npm run start:public                  # the same without the BBH Design System, for a machine outside BBH
```

`./gradlew dsoJar` builds the bundle and packs it into the portal service, which serves it on port 8080. The tasks run
`npm` and `npx` from the `PATH`: Node.js 20.19 or a later 20.x with npm 10.8 or a later 10.x, as `engines` in
`package.json` requires and `.npmrc` enforces. `.npmrc` points npm at the BBH registry
(`tools.bbh.com/nexus/repository/npm-group`); outside BBH the Gradle build switches to `registry.npmjs.org` by
itself, as "Inside and outside the BBH network" in the main README describes.

| Folder (`src/app`) | Holds |
|--------------------|-------|
| `core/`       | the API clients, the models mirroring the portal's DTOs and the four sections of the menu (`sections.ts`) with the tabs of DevSecOps Admin |
| `pipelines/`  | Pipelines: the pipelines of your department in a table filtered and sorted in its header, the pipeline page with its key, settings, Jenkinsfile and recent runs, and the settings dialog |
| `self-service/` | Self-service: the step-by-step wizard that sets up or changes the pipelines of a product, whose answers `self-service-model.ts` turns into a product request with BBH defaults (build tasks, Nexus delivery, OpenShift project names, Nexus IQ scan patterns) |
| `monitoring/` | Pipeline Monitoring: overview, product pipelines, pipeline details with DORA and Grafana |
| `departments/` | the Departments tab of DevSecOps Admin: the common departments page with the service and pipeline counts and the chart "Pipelines per department" |
| `products/`   | the Products tab: the product list grouped by department, the product editor, the product page with pipelines and keys |
| `settings/`   | the Library defaults tab (tools and servers, security limits, scans, release gate and defaults of every pipeline) and the Service template tab |
| `shared/`     | what several portal pages share: run and key chips, DORA levels, build links, Bitbucket links, the config.yaml dialog, choice tiles, the bar chart |
| `testing/`    | fixtures for the unit tests |

The shell, the UI kit, the form field definitions, the departments and the Admin page come from `common-gui`,
imported as `@common/...` (see its README). `main.ts` is `appConfig('DevSecOps Management Portal', routes)`: the
header reads "BBH DevSecOps Management Portal", the menu lists Pipelines, Self-service, Pipeline Monitoring and
Admin, and nothing of Beadle appears anywhere.

## Forms from field definitions

A form field is written once, as data. `@common/shared/fields.ts` holds the `Field` interface (key, label, the
`config.yaml` path shown at the end of its hint, span of the 12 column grid, kind, placeholder, hint, error message,
select options and number range) with the small builders `line`, `mono`, `area`, `check`, `count` and `choice`, and the
`dso-fields` component that renders a list of them into a `.form-fields` grid: a `dso-form-field` with a Bootstrap
input, select or text area, or a `dso-checkbox`, per entry, bound to the control of that key in the group it is given.
The service editor (`products/service-fields.ts`), its child editors and the Library defaults tab
(`settings/settings-fields.ts`) therefore describe their sections as lists of fields; only the parts that are not a
plain field (the tool command blocks, the test job list, the UrbanCode applications, the OpenShift targets and the
toggle groups) have markup of their own. Labels and hints go through `chips()`, which turns `` `path` `` into a code
chip. A hint shows the plain explanation first, starting with a capital, and the config key last, after a ` · ` (for
example "The portal links the Jenkins job of each pipeline from it · `platform.jenkinsUrl`"); `code` is muted, so
the explanation reads first and an engineer still finds the key.

The service editor's vertical menu, the panes it opens and the validity of each section come from
`SERVICE_SECTIONS` in `products/product-form-model.ts`; the explanation under each pane heading sits next to the
field definitions. The rarely changed settings of the Build, Unit tests and coverage, AppScan SAST and DAST,
SonarQube, Nexus IQ, Bitbucket and Monitoring panes sit in a collapsed "Advanced settings" panel
(`products/advanced-settings.ts`), which names what it holds and opens by itself when a field in it is invalid and
touched or the form was submitted. Requests are mapped with `sent()`, which trims every string of a form value and
sends a blank one as `null`, so each request mapping only names the values that need more than that (lists split
into lines or words, values kept while they are valid, and the sections that do not apply).

## Browser tests

The smoke, regression and performance suites are Spock specifications that drive Chromium with Playwright against
the production build. `GuiServer` (in `common-gui/src/testFixtures`) serves `dist` and answers `/api` from
`DsoStubApi` (`src/testFixtures`), which replays the API answers recorded from the portal service
(`src/testFixtures/resources/.../api`) and records every request, so a specification checks both what the page shows
and the exact JSON the GUI sends. Every specification extends `DsoSpecification`, which knows the portal's menu.
Screenshots of the last state of every feature land in `build/reports/frontend/screenshots`.

- Smoke (`src/smokeTest`): the title, the four menu entries and the footer without any trace of Beadle; every
  section and every Admin tab from the menu and from a direct link; the pages that read the API; the pages without
  icons but the service editor's menu; every page in an 800 and a 600 pixel window without sideways scrolling.
- Regression (`src/regressionTest`): the tabs of DevSecOps Admin; the departments with their pipeline tallies and
  chart, and adding, renaming and deleting them; the products grouped by department; searching and opening products;
  adding a product with two services, through the browser's required-field checks and the server's field errors, to
  the request it sends; saving unchanged products, which sends back what was loaded with its version; the
  unsaved-changes guard; moving, duplicating and removing services; Bitbucket fields, GoldenFix default and test job
  parameters; adding pipelines, replacing, invalidating and regenerating keys, and the keys generated for new
  services; pipeline settings someone else saved meanwhile, reloaded into the dialog; the settings sent to Jenkins
  (config.yaml) and their problem details; library defaults with a version conflict; the Self-service wizard for a
  new product and for one in the portal (adding a pipeline, an OSA (Nexus IQ GoldenFix) pipeline with the Nexus IQ
  application and repository of each service, changing and removing services, a name another new service has, a
  service template that has to load first and a remembered department that is no longer listed); monitoring ranges,
  Jenkins and build links, InfluxDB missing or unreachable, and a failed read of the delivery performance; and
  failing API calls.
- Performance (`src/performanceTest`): the stub serves 25 products with 16 services and 4 pipelines each (Full,
  Security, Extended and SAST scanning, so the workload stays the same as new types are added), with monitoring for
  all of them. The suite times the cold product list, the product page, the editor and a service expansion, the
  monitoring overview and a product's monitoring in the browser, from the click to the content being visible, one
  warm-up and five measured runs each, and fails when a p95 passes its limit. `-Dperformance.factor=2` doubles the
  time limits on a slow machine. The table of timings is `build/reports/performance/dso-gui-performance-report.md`;
  the bundle size is guarded by the `angular.json` budgets.

## Look and layout

- The header holds the name of the application and one flat menu of short labels: Pipelines, Self-service, Pipeline
  Monitoring and Admin; the entry of the current page is underlined. A new feature is a `PortalSection` added to
  `SECTIONS` in `core/sections.ts`, routed in `app.routes.ts`, and to `MENU` in `DsoSpecification`. An Admin page is
  an `AdminArea` of tabs in `core/sections.ts`, routed as children of the common `AdminPage`; its tabs render without
  their own page heading. Each page starts with the full name of its section as the heading and the section
  description under it: one or two plain sentences on what the page is for. A card that needs it explains itself in
  a muted `section-help` line under its heading, and a form says "Fields marked * are required." once.
- The pages show no icons. The only icons are those of the vertical section menu in the service editor.
- Fields are compact (Bootstrap small controls, 30px inputs). Forms reflow to two columns below 760px and to one below
  480px; the menu wraps and wide tables scroll inside their own container, so no page scrolls sideways at 800px or
  600px.
- On/off settings are checkboxes. Expandable panels say what they open: Show or Hide for the Advanced settings panels,
  Edit settings or Close for a service in the product editor.
- A run result reads the same everywhere (`RUN_LOOK` in `shared/status-chip.ts`): Passed, Passed with warnings,
  Failed, Stopped, Not built, No runs yet and Key invalidated.
- An error says what could not be loaded or saved, then the reason, then what to do. `errorMessage()` in
  `@common/core/errors.ts` gives the problem detail of the API, "The portal cannot be reached. Check your network
  connection and try again." when no answer came, and "The portal could not complete the request (error 500). Try
  again in a moment; if it keeps failing, tell the portal administrator." for an answer without a detail; a page that
  can load again offers Try again. A setting the administrator has to make (InfluxDB, Grafana) is named in a muted
  "For the administrator:" sentence.
- `common-gui/src/styles.scss` holds everything more than one page draws (see the common-gui README); a component
  stylesheet keeps only what that page alone needs, and a chart's series colours sit in the chart's own CSS file.

## Bitbucket repository

The Bitbucket section of a service sets `scm.bitbucket.apiUrl`, `workspace`, `projectKey` and `repoSlug` next to
the repository URL. They are optional: the pipeline reads them from the repository URL when they are empty. The
product page links each service to its repository: the repository URL when set, otherwise
`https://bitbucket.org/{workspace}/{repoSlug}` for Bitbucket Cloud or `{apiUrl}/projects/{projectKey}/repos/{repoSlug}`
for Data Center (`users/` for a personal `~` project key).

## Pipeline types

`PIPELINE_TYPES` in `core/models.ts` lists the pipeline types in the order the pages show them: Full, Security,
Extended, SAST scanning and Nexus IQ GoldenFix (`NEXUS_IQ`, run by `devSecOpsNexusIqGoldenFixPipeline`). The Add
pipeline dialog offers the types a service has no pipeline of; only Security and Extended have a job field of another
pipeline. No page lowercases the type itself: `pipelineTypeSlug` gives the part of job and file names (`full`, `sast`,
`nexusiq`, so the suggested job is `DevSecOps/<CODE>/<service>-nexusiq`) and `pipelineTypeName` the name inside a
sentence (`full`, `sast`, `Nexus IQ GoldenFix`).

The settings dialog of a pipeline ("Settings of the pipeline backend-api · Full", **Save settings**; "Add a pipeline to
backend-api", **Add pipeline**) sends the `version` of the pipeline it loaded. When someone else saved the pipeline
meanwhile, the API answers 409: the dialog loads the current settings with their version into the form and says "Not
saved: someone else changed the settings of this pipeline after you opened them. The form now shows their settings:
make your change again, then save." When those settings cannot be loaded either, it says so with the reason.

## Self-service wizard

The wizard has five steps: Product, Pipeline, Services, Review and Next steps. Each asks one question ("Which pipeline
does Payments need?", "Is everything right?"), and the main button names the next step (**Next: Services**) until the
review, which saves with **Create the pipelines** (**Save the changes** for a product in the portal). The Pipeline step
offers SAST, OSA, Security and Full by their long names (`PIPELINES` in `self-service-model.ts`), shows "The pipelines
of Payments today" for a product in the portal, and "Have these at hand for the next step" lists what the chosen one
needs. Only Security and Full deploy (`deploys()` in `self-service-model.ts`), so only they ask where each service
runs. Every pipeline asks for the AppScan application ID of each service, which the portal requires. The Services step
does not go on while a new service has no place to run or has the name of another service (compared without case), or
while the service template a new or changed service takes its build settings from has not loaded (a failed load offers
**Try again**). The review tags each service New, Changed or Removed under "What happens to each service".

For OSA, the Nexus IQ GoldenFix pipeline, the second part of the service dialog ("Part 2 of 2 · Build and run") asks
for the build tool, the Nexus IQ application and the Bitbucket repository (an http or https URL), filled in from a
service in the portal (its first Nexus IQ application and `scm.repositoryUrl`); the Services step does not go on while
a service lacks either. Saving renames the first Nexus IQ application of the service, or adds one with stage `build`
and the scan pattern of its build tool (`**/build/libs/*.jar` for Gradle, `**/target/*.jar` for Maven,
`**/pubspec.lock` for Flutter), and sets the repository URL, with the credentials `bitbucket-http-credentials` when the
service has none. Every other stored setting stays as it is, and the other pipelines neither write nor review these
two answers. The next steps add one: "Review the golden pull requests" GoldenFix opens in each service's repository.

## Pipeline keys

The API sends each key with a `hint`: its first 8 characters, `…` and its last 4. The product page shows the active
key by its hint; **Show key** and **Copy key** (**Show the key** and **Copy the key** on the pipeline page) use the
key `value`, which the management endpoints of products and pipelines send for the active key only. The key history
lists every key by its hint, with "Last used by Jenkins". The monitoring endpoints send no key values at all, so the
monitoring pages only check whether a pipeline has an active key.

Saving a product gives every new service a full pipeline with an active key; the backend creates them in the same
save. The editor remembers which services were new (`GeneratedKeys`, in memory only), and the product page it opens
next says "Pipeline keys generated for 2 new services" (or names the one new service), marks those services as New
and shows their keys in full. The notice appears once: reloading the page or opening it from the list shows none.

A pipeline whose key was invalidated shows a "Regenerate key" text button next to its key status, on the product page
and in the key history dialog, and its pipeline page says so in a banner with the same button. It calls
`POST /api/pipelines/{id}/keys` without a confirmation, since no working key is replaced, and shows the new key; the
invalidated keys stay in the history. "Replace key" in the More menu of a pipeline with an active key still asks
first, because it invalidates the key in use.

## Grafana

The monitoring API sends `grafana` for a pipeline: one entry per Grafana instance, with the instance's `name` and
its `dashboardUrl`, the dashboard with its variables already set. The pipeline page shows a card per entry, named
after the instance, which says "The detailed charts of this pipeline in <name>.", embeds the dashboard in an iframe
with `&kiosk` and links the same address as "Open in <name>". When the backend has no Grafana link the list is empty
and a "Grafana dashboard" card says "No Grafana dashboard is linked to the portal, so the detailed charts of this
pipeline are not shown here." with, for the administrator, the variables to set (`GRAFANA_DASHBOARD_URL`, and
`GRAFANA_2_DASHBOARD_URL` for a second Grafana).

## Build links

Every run carries the `buildUrl` the backend builds from the job that recorded the run (its `JOB_NAME`, so a branch of
a multibranch job links to that branch) and falls back to the pipeline's configured Jenkins job. The monitoring pages
link each build number to that address and show the number without a link when it is `null`.

## GoldenFix switch of a service

"Run GoldenFix" in the GoldenFix section of a service is a select with Global default, On and Off. Global default
sends `goldenFix.enabled: null`, so the service follows "GoldenFix runs by default" of the library defaults, and is
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
- Library defaults: the minimum line coverage is 1 to 100 and the release gate checks at least one scanner. The
  result file of the release gate is not a choice: the field shows `release-gate.json` read-only and every save sends
  that name (`RELEASE_GATE_FILE`), since the library reads no other. The open source scan (SCA) has only its security
  limits; its switch and waiting times are gone, so there is nothing else of it to check.
- Text: `max()` in `@common/shared/form-controls.ts` counts the characters and then the UTF-8 bytes, since the Oracle
  columns are sized in bytes. A text full of accented letters or symbols is therefore refused in the form, not on
  save: "Too long: at most 100 characters, and accented letters and symbols count as two or three".
- Lists: besides the number of entries and the length of each, the entries joined as stored must fit their column,
  counted in UTF-8 bytes (`fitsColumn`; for example 2000 for build flags, 4000 for variables, 1000 for agent labels).

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
