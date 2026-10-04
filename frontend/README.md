# Portal frontend

The Angular app of the BBH DevSecOps Management Portal. See the [main README](../README.md) for the whole
portal.

```bash
npm ci
npm start                    # http://localhost:4200, /api is proxied to the backend on port 8080
npm run build                # dist/frontend/browser, packaged into the jar by `mvn -Pfrontend package`
npm test -- --watch=false    # Vitest unit tests
```

Needs Node.js 22.22.3 or newer.

| Folder        | Holds |
|---------------|-------|
| `core/`       | API clients, models mirroring the backend DTOs, error handling |
| `products/`   | Product Management: product list, product editor, product page with pipelines and keys |
| `monitoring/` | Pipeline Monitoring: overview, product pipelines, pipeline details with DORA and Grafana |
| `shared/`     | status chips, dialogs, formatting |
