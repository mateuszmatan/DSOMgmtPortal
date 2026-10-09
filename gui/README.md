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
| `core/`       | API clients, models mirroring the backend DTOs, error handling, the portal sections and the header menus |
| `self-service/` | Self-service: the step-by-step wizard that sets up or changes the pipelines of a product, whose answers `self-service-model.ts` turns into a product request with BBH defaults (build tasks, Nexus delivery, OpenShift project names, Nexus IQ scan patterns) |
| `admin/`      | the Admin page with its tab bar, shared by DevSecOps Admin and Beadle Admin, and the Departments tab |
| `products/`   | DevSecOps Admin products: the product list grouped by department, product editor, product page with pipelines and keys |
| `monitoring/` | Pipeline Monitoring: overview, product pipelines, pipeline details with DORA and Grafana |
| `evidence/`   | Change Evidence: builds, tests and scans of each pipeline for ServiceNow changes |
| `settings/`   | Admin > Library defaults: tools, policy and defaults of every pipeline |
| `beadle/`     | Beadle: the department the user works for (remembered in the browser), the Beadle Admin products list and the product page with its facts and change template |
| `changes/`    | Beadle Changes and New Change: the ProTech changes of a department in a table filtered and sorted in its header, the change page with its workflow progress and update status, the edit page that publishes to ProTech, the change wizard, and the ProTech fields form and change tasks editor shared with Beadle Admin |
| `shared/`     | field definitions and the field component, form controls, dialogs, formatting, Bitbucket links |
| `testing/`    | fixtures for the unit tests |

## Forms from field definitions

A form field is written once, as data. `shared/fields.ts` holds the `Field` interface (key, label, the
`config.yaml` path shown as its hint, span of the 12 column grid, kind, placeholder, hint, error message, select
options and number range) with the small builders `line`, `mono`, `area`, `check`, `count` and `choice`, and the
`dso-fields` component that renders a list of them into a `.form-fields` grid: a Material field or checkbox per
entry, bound to the control of that key in the group it is given. The service editor
(`products/service-fields.ts`), its child editors and the Library defaults tab (`settings/settings-fields.ts`)
therefore describe their sections as lists of fields; only the parts that are not a plain field (the tool command
blocks, the test job list, the UrbanCode applications, the OpenShift targets and the toggle groups) have markup of
their own. Labels and hints go through `chips()`, which turns `` `path` `` into a code chip.

A field with a `lookup` gets a magnifier button, "Find <label>", that opens `LookupDialog` (`shared/lookup-dialog.ts`):
a search field and the matching entries of `/api/lookups/{kind}`, each with its value and detail. Picking one fills the
field, or adds it to the comma separated list when the lookup appends, and `detail` names the field the detail of the
entry fills (the Affected CI fills the Direct business service, which typing the CI by hand clears). The input itself
stays free text.

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

- Regression (`src/regressionTest`): the tabs of both Admin pages; the departments with their pipeline tallies, and
  adding, renaming and deleting them; the products grouped by department; searching and opening products; adding a product with two services, through
  the browser's required-field checks and the server's field errors, to the request it sends; saving unchanged
  products, which sends back what was loaded with its version; the unsaved-changes guard; moving, duplicating and
  removing services; Bitbucket fields, GoldenFix default and test job parameters; adding pipelines, replacing,
  invalidating and regenerating keys, and the keys generated for new services; config previews and their problem
  details; library defaults with a version conflict; the Self-service wizard for a new product and for one in the portal
  (adding a pipeline, a Nexus IQ GoldenFix pipeline with the Nexus IQ application and repository of each service,
  changing and removing services); Beadle Admin products and their change template section by section, with a
  lookup, the downtime, the risk answers and the risk they give, privileged accounts, default change tasks and a
  version conflict; the ProTech changes of a department filtered and sorted in the table header, the change page with
  its workflow progress, an update published to ProTech from PENDING to APPLIED with a lookup, a downtime window and a
  risk answer, a NOT_APPLIED update, a stale update, a closed change and the change of another department; the new
  change walked step by step from the product through the lookups, the FixVersion, the approvers, the schedule with its
  downtime window, the planning, the privileged accounts, the risk lists and the secure coding ticket to the review with
  its change tasks and the raised change, a product without a template, and ProTech's field errors marked on the steps;
  change
  evidence and its ServiceNow text, and the golden pull request of a Nexus IQ GoldenFix run; monitoring ranges,
  Jenkins and build links, InfluxDB missing or unreachable; and failing API calls.
- Performance (`src/performanceTest`): the stub serves 25 products with 16 services and 4 pipelines each (Full,
  Security, Extended and SAST scanning, so the workload stays the same as new types are added), with monitoring and
  evidence for all of them. The suite times the cold product list, the product page, the editor and a service
  expansion, the monitoring overview, a product's monitoring and a product's evidence in the browser, from the click
  to the content being visible, one warm-up and five measured runs each, and fails when a p95 passes its limit.
  `-Dperformance.factor=2` doubles the time limits on a slow machine. The table of timings is
  `build/reports/performance/gui-performance-report.md`; the bundle size is guarded by the `angular.json` budgets.

## Look and layout

- The header holds two menus, each opening a compact dropdown of short labels: Beadle (Changes, New Change, Admin), for
  the new features of the portal, and DevSecOps Management (Self-service, Pipeline Monitoring, Change Evidence, Admin).
  A new feature is a `PortalSection` added to the Beadle entry of `MENUS` in `core/sections.ts` and to `MENUS` in
  `GuiSpecification`, whose `menuLink(menu, label)` opens the right menu (both
  menus have an Admin item). An Admin page is an `AdminArea` of tabs in `core/sections.ts`, routed as children of
  `AdminPage`; its tabs render without their own page heading. The menu
  holding the current page is underlined. Each page starts with the full name of its section as the heading and the
  section description under it.
- The pages show no icons. The only icons are those of the vertical section menu in the service editor and the
  magnifier of each lookup field.
- Fields are compact (Material density -4, 32px inputs). Forms reflow to two columns below 760px and to one below
  480px; the menu wraps and wide tables scroll inside their own container, so no page scrolls sideways at 800px or
  600px.
- On/off settings are checkboxes and expandable panels say Show or Hide.
- `src/styles.scss` holds everything more than one page draws: the page header and breadcrumb, cards, tables
  and the 12 column form grid, the chips (`chip` with its tones for run results, checks, DORA levels and key
  states), the run colours as `--dso-run-*` with the `swatch` class, the fact lists (`pairs` and `rows`), the
  quiet links, the side navigation of the service editor and the settings (`side-nav`) and the expansion
  panels. A component stylesheet keeps only what that page alone needs.

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

## New ProTech change

The New Change wizard follows the sections of a ProTech change, listed once in `SECTIONS` of
`changes/change-sections.ts`: Request data (Generic request data), Jira, Approval (Approval and Notification), Schedule,
Planning, Privileged access, Risk assessment and Secure coding, then Review and Raised. Request data starts with the
department and product pickers and the read-only facts (Change number, Approval, Opened By, State), then the request
fields in two columns. Requested For, Requested By and Assigned to default to the signed-in user of `/api/me` and
the Department to the department of the product. Jira shows the Jira project of the template and asks for the
FixVersion and its epics and stories. Schedule asks for the start of the installation, the post-install validation
and the first use as date and time, with hours for the installation and the validation, and a Yes/No downtime with
its own start and hours; the end of each window is shown under its hours. Privileged access asks how many accounts
(none to seven) and then the person and the account of each. Risk assessment offers the nine lists of
`/api/changes/options` with Not assessed first; the Risk field of Request data is read-only and follows the answers
(High when an answer is the last of its list, Moderate when one is past the first, else Low). Review holds the short
description and description written from Jira and the change tasks, which can be changed there.

The Category, Type and risk lists come only from `/api/changes/options`. The Beadle Admin product page shows the same
sections without the facts, with the Jira project and the schedule defaults (start time and hours), and says that an
empty Requested For, Requested By or Assigned to becomes the user who opens the change and an empty Department the
department of the product. The edit page of a change shows the facts and the same sections without Jira, with the
schedule of the change; the change page summary lists every field by section.

## Self-service wizard

The wizard has five steps: Product, Pipeline, Services, Review and Next steps. The Pipeline step offers Static scan,
Nexus IQ GoldenFix, Security and Full, and "Have these at hand" lists what the chosen one needs. Only Security and Full
deploy (`deploys()` in `self-service-model.ts`), so only they ask where each service runs. Every pipeline asks for the
AppScan application ID of each service, which the portal requires.

For Nexus IQ GoldenFix the second part of the service dialog asks for the build tool, the Nexus IQ application and the
Bitbucket repository (an http or https URL), filled in from a service in the portal (its first Nexus IQ application
and `scm.repositoryUrl`); the Services step does not go on while a service lacks either. Saving renames the first
Nexus IQ application of the service, or adds one with stage `build` and the scan pattern of its build tool
(`**/build/libs/*.jar` for Gradle, `**/target/*.jar` for Maven, `**/pubspec.lock` for Flutter), and sets the repository
URL, with the credentials `bitbucket-http-credentials` when the service has none. Every other stored setting stays as
it is, and the other pipelines neither write nor review these two answers. The next steps add one: review the golden
pull requests GoldenFix opens in each service's repository.

## GoldenFix in the change evidence

A run that went through GoldenFix carries `goldenFix`: its status, the upgrades offered, applied and left unresolved,
and the golden pull request. The pipeline card shows it in plain words under the scans with a link to the pull
request, and the ServiceNow text adds a GoldenFix block after the scans. A run without it shows nothing more.

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

The monitoring API sends `grafana` for a pipeline: one entry per Grafana instance, with the instance's `name` and
its `dashboardUrl`, the dashboard with its variables already set. The pipeline page shows a card per entry, named
after the instance, which embeds the dashboard in an iframe with `&kiosk` and links the same address as "Open in
<name>". When the backend has no Grafana link the list is empty and the page says so instead of embedding anything.

## Build links

Every run carries the `buildUrl` the backend builds from the job that recorded the run (its `JOB_NAME`, so a branch of
a multibranch job links to that branch) and falls back to the pipeline's configured Jenkins job. The monitoring pages
link each build number to that address and show the number without a link when it is `null`. The change evidence
uses the build, report, test and artifact links the API sends.

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
- Library defaults: the minimum line coverage is 1 to 100 and the release gate checks at least one scanner.
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
