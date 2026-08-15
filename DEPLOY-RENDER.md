# Deploying the Direitoria API to Render

Render has no native Java runtime, so this service deploys as a Docker image.
`Dockerfile`, `.dockerignore` and `render.yaml` in this directory cover that.

## The one thing that will bite you first

The app runs **`spring.jpa.hibernate.ddl-auto=validate`** — Drizzle owns the
schema, Hibernate never emits DDL. Against a brand-new empty Render database the
app **will fail to start**, with a validation error naming a missing table. That
is correct behaviour, not a bug.

Worse, the migrations are **not in this repo**. They live in the root Node
project (`drizzle/0000…0004*.sql`, applied with `npm run db:migrate`). So the
schema has to be pushed from there, against Render's database, before the first
successful boot.

**Order of operations:**

1. Create the blueprint (`render.yaml`) so the database exists — the web service
   will fail its first boot. Expected.
2. Copy the database's **external** connection string from the Render dashboard.
3. From the **root** project on your machine:
   ```
   DATABASE_URL="postgres://…render…/direitoria_questoes" npm run db:migrate
   ```
   (PowerShell: `$env:DATABASE_URL="…"; npm run db:migrate`)
4. Load the question data the same way — a fresh database has the schema but no
   questions. `npm run ingest` then `npm run normalize`, pointed at the same
   `DATABASE_URL`.
5. Redeploy the web service. It should now pass validation and start.

Every future migration repeats steps 2–3 **before** deploying the API version
that expects it.

## Environment variables

`render.yaml` wires `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`
from the managed database, and generates `JWT_SECRET`. Nothing needs pasting by
hand.

Two changes were made to `application.properties` to make this possible, both
backward compatible — local defaults are unchanged:

- `server.port=${PORT:8080}` — Render assigns the port and routes to it.
- The datasource URL is composed from `DB_HOST`/`DB_PORT`/`DB_NAME` when `DB_URL`
  is absent, because Render exposes those separately and its connection string is
  `postgres://…`, which JDBC cannot parse.
- `spring.datasource.username` is now `${DB_USERNAME:postgres}`; it was hardcoded
  to `postgres`, and managed Postgres generates its own role name.

## Health check

`render.yaml` points at `/api/subjects`, which `SecurityConfig` permits
anonymously and which reads from the database — so a passing check means the app
is genuinely serving, not merely alive. The trade-off: if Postgres goes away, the
check fails and Render restarts the service. That is the right call here (the API
is useless without its database), but it is a deliberate choice, not an accident.

**Noticed while wiring this up:** `SecurityConfig` permits `/actuator/health`,
but `spring-boot-starter-actuator` is **not** a dependency, so that path returns
404 — the rule is dead. Either add the starter (and switch the health check to
`/actuator/health`, which does not touch the database) or drop the rule. Not
fixed here because it is outside a deployment change.

## Things to know about the free plan

- **Free instances sleep when idle.** A JVM cold start takes tens of seconds, so
  the first request after a nap is slow. Fine for a demo, not for students.
- **512 MB RAM.** The Dockerfile sets `-XX:MaxRAMPercentage=75` so the JVM sizes
  its heap from the container limit rather than the host. If it still OOMs, that
  is the signal to move up a plan, not to shrink the heap further.
- **Free Postgres expires after 90 days** on Render. Note the date somewhere.

## Tests are skipped in the image build

`mvn package -DskipTests` is deliberate. The suite uses Testcontainers, which
needs a Docker daemon *inside* the build — Render's builder has none, so running
them there would fail every deploy. Run them locally or in CI.

## Not verified

This Dockerfile has **not been built**. It was written without a running Docker
daemon and with only Java 17 available locally against a Java 21 project. The
first `docker build -t direitoria-api .` in this directory is the real test —
expect to iterate on it, particularly the `dependency:go-offline` step, which can
be fussy with some plugin sets.

## CORS / the frontend

The frontend talks to this API through its own BFF, so set `SPRING_BASE_URL` on
the frontend service to this service's Render URL. The browser never calls this
API directly, which is why no CORS configuration is needed here.
