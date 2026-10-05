# Portal frontend

The Angular app of the BBH DevSecOps Management Portal. See the [main README](../README.md) for the whole
portal.

```bash
./gradlew :gui:buildGui      # production bundle in gui/build/dist/browser, checked against the angular.json budgets
./gradlew :gui:unitTest      # Vitest unit tests with coverage, thresholds in angular.json
./gradlew :gui:smokeTest     # Spock and Playwright: every page in a browser against recorded API answers
cd gui && npm start          # http://localhost:4200, /api is proxied to the backend on port 8080
```

Gradle downloads Node.js 24 into `gui/.gradle/nodejs`.

| Folder        | Holds |
|---------------|-------|
| `core/`       | API clients, models mirroring the backend DTOs, error handling, the four portal sections |
| `products/`   | Product Management: product list, product editor, product page with pipelines and keys |
| `monitoring/` | Pipeline Monitoring: overview, product pipelines, pipeline details with DORA and Grafana |
| `evidence/`   | Change Evidence: builds, tests and scans of each pipeline for ServiceNow changes |
| `settings/`   | Global Settings: tools, policy and defaults of every pipeline |
| `shared/`     | form controls, dialogs, formatting, Jenkins and Bitbucket links |
| `testing/`    | fixtures for the unit tests |

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
