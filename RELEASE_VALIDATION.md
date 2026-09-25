# Dronzer — Release Validation

Reproducible validation commands for a release candidate. Every command below is
run from the repository root unless stated otherwise.

## 1. Normal validation (required for every release)

### Backend

```
.\mvnw.cmd clean test -q
```

Runs the full JUnit suite on H2 (`test` profile). No database, Docker, or API
credentials required. Non-zero exit means the release is not shippable.

Linux/macOS equivalent: `./mvnw clean test -q`

### Frontend

```
cd frontend
npm ci
npm test
npm run build
```

- `npm ci` installs strictly from `package-lock.json`.
- `npm test` runs the Vitest component suite.
- `npm run build` type-checks (`tsc -b`) and produces `dist/`.

## 2. Optional integration validation — PostgreSQL / pgvector

Prerequisites:

- A running Docker daemon (Testcontainers starts `pgvector/pgvector:pg16`).
- No application credentials; the containers are self-contained.

```
$env:RUN_PGVECTOR_TESTS="true"
.\mvnw.cmd test -Dtest='*IntegrationTest' -DfailIfNoSpecifiedTests=false
```

These tests are opt-in: without `RUN_PGVECTOR_TESTS=true` they are skipped and
never fail a normal build. They exercise real `pgvector` SQL that H2 cannot
emulate (`tsvector`/`ts_rank_cd`, `<=>` vector distance).

## 3. Optional live-provider validation — Tavily

Prerequisites:

- A real `TAVILY_API_KEY` exported in the environment. Never commit it.
- `RUN_TAVILY_INTEGRATION_TESTS=true` to enable the tests.

```
$env:RUN_TAVILY_INTEGRATION_TESTS="true"
$env:TAVILY_API_KEY="<your key>"
.\mvnw.cmd test -Dtest='TavilyWebSearchLiveIntegrationTest' -DfailIfNoSpecifiedTests=false
```

Both conditions must hold; otherwise the class is skipped. The deterministic,
credential-free equivalent is `TavilyWebSearchClientTest`, which runs in the
normal suite.

## 4. Running the application locally (not required for CI)

The build/test commands above are credential-free, but actually starting the
application and exercising `POST /rag/ask` end to end needs more:

- A running PostgreSQL accepting the configured datasource. The application
  fails fast at startup if the database is unreachable — Flyway runs before the
  context is ready, so this is a hard failure, not a degraded mode.
- `SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET`, and `GEMINI_API_KEY` exported in
  the environment.
- Local-only values belong in `src/main/resources/application-local.properties`,
  which is git-ignored (`.gitignore`: `src/main/resources/application-*.properties`).
  Never commit real keys.

Known runtime limit observed during release validation: the configured chat
model is on the Gemini free tier, which caps `generate_content` at 20 requests
per project per day. When that quota is exhausted, the upstream returns HTTP 429
and `/rag/ask` responds 500 with `GeminiUpstreamException` surfaced through
`GlobalExceptionHandler`. This is an upstream quota condition, not an
application defect, but it does mean a live `/rag/ask` smoke test cannot be
relied on to reproduce on demand. Retrieval, document, and authentication
paths do not depend on generation and remain testable without generation quota.

## 5. Continuous integration

`.github/workflows/ci.yml` runs on every push and pull request to `main`:

| Job | Command | Credentials |
| --- | --- | --- |
| Backend tests | `./mvnw clean test -q` | none |
| Frontend build | `npm ci`, `npm test`, `npm run build` | none |
| pgvector integration | `./mvnw test -Dtest='*IntegrationTest'` | `workflow_dispatch` only |
| Live Tavily | `./mvnw test -Dtest='TavilyWebSearchLiveIntegrationTest'` | `workflow_dispatch` only, uses the `TAVILY_API_KEY` secret |

The two optional jobs never run on pull requests. Normal PR CI is fully
independent of external providers and of Docker.

## 6. Expected results

- Backend: all tests pass; only the pgvector and live-Tavily tests report as
  skipped. Skipped tests are not failures.
- Frontend: all Vitest tests pass and the build exits 0.
