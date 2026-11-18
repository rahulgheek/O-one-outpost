# O(1) Outpost — Project Guide

This guide describes the current repository and gives developers and operators
a starting point for understanding, running, and maintaining the platform.
For deployment procedures, see the [Botkeep deployment guide](../deploy/README.md).
The older [production deployment](./README-deployment.md) and
[EC2 setup](./ec2-setup.md) guides are kept as legacy references.

## 1. Purpose and user-facing capabilities

O(1) Outpost is a competitive-programming and coding-assessment platform. It
provides:

- Account registration, verification, and login.
- Public programming problems, daily questions, matches, profiles, and
  leaderboards.
- Company-managed problem banks and assessments, candidate registration, a
  coding workspace, submissions, and assessment leaderboards.
- A browser-based coding interface with a Monaco editor.
- An isolated service for compiling and executing submitted code.
- Analytics, audit logging, and gateway metrics.

The web application has separate user, company, and overseer routes. Route
access is guarded in the frontend; backend authorization remains authoritative.

## 2. Architecture

The system is organized as a React web client and independently deployed
Spring Boot services. Requests enter through the API gateway, which routes
traffic to internal services. Docker Compose provides the local and production
orchestration surfaces.

```text
Browser
   |
   v
Web client -----> API Gateway -----> Auth Service
                                  +-> Arena Service
                                  +-> Assessment Service
                                  +-> Analytics Service
                                  +-> Code Sandbox -----> ephemeral executor
```

| Component | Responsibility | Technology |
|---|---|---|
| `O-one-outpost-web` | Sign-in and registration, user/company screens, arena and assessment workspaces | React, Vite, React Router |
| `gateway-service` | Central HTTP entry point, request routing, metrics, and gateway-level controls | Java, Spring Boot, Spring Cloud Gateway |
| `auth-service` | User accounts, verification, authentication, and authorization support | Java, Spring Boot, Spring Security |
| `arena-service` | Public problems, daily questions, competitive matches, player profiles | Java, Spring Boot, MongoDB |
| `assessment-service` | Company problems and assessments, candidate workflows, submissions, leaderboards | Java, Spring Boot, MongoDB |
| `analytics-service` | Analytics streams, audit records, and system metrics | Java, Spring Boot |
| `code-sandbox` | Accepts execution requests and runs them via the OnlineCompiler.io HTTPS API (secondary: warm Docker pool) | Java, Spring Boot |

Service port numbers and published host ports are deployment-specific. Consult
the relevant Compose file and service configuration before relying on a port.

## 3. Typical user workflows

### Practice and arena

1. A user registers or signs in.
2. The user browses problems or the daily question.
3. The user joins a match and submits a solution from the arena workspace.
4. The platform executes code through the sandbox and updates match/profile
   results.

### Company assessment

1. A company user signs in, curates problems, and creates an assessment.
2. Candidates discover or join the assessment and register.
3. A candidate starts the assessment, works through its problems, and submits
   code.
4. Candidate progress and leaderboard information are available through the
   assessment experience.

These workflows summarize the application surface; eligibility, timing,
scoring, and state-transition rules are defined by the respective service
implementations.

## 4. Web routes

Routes are declared in `O-one-outpost-web/src/App.jsx`. The application
redirects unknown routes to `/`.

| Route | Purpose |
|---|---|
| `/`, `/login`, `/signup` | Landing and user authentication |
| `/lobby`, `/dashboard`, `/arena/:matchId` | User lobby, dashboard, and match workspace |
| `/company/login`, `/company/signup` | Company authentication |
| `/company/dashboard`, `/company/question-bank`, `/company/create-assessment`, `/company/selection` | Company assessment management |
| `/overseer`, `/admin` | Overseer interface (`/admin` redirects to `/overseer`) |
| `/candidate/directory`, `/candidate/register/:assessmentId`, `/candidate/dashboard`, `/candidate/join` | Candidate assessment discovery and registration |
| `/workspace/:assessmentId`, `/workspace/:assessmentId/leaderboard` | Assessment workspace and leaderboard |

## 5. HTTP API map

The following route families are declared by the service controllers. Use the
gateway for normal client access; verify the gateway route configuration when
changing or integrating an endpoint. Request and response schemas are owned by
the controller DTOs and should be checked in source before building a client.

| Service | Route family | Operations represented in the controllers |
|---|---|---|
| Auth | `/api/auth` | `POST /register`, `/admin/create-admin`, `/verify`, `/resend-otp`, `/login` |
| Arena | `/api/problems` | Problem read/list and admin create, update, search, and delete |
| Arena | `/api/problems/daily` | Read daily question and admin list, schedule, and delete |
| Arena | `/api/match` | Join, submit to, and leave a match |
| Arena | `/api/profiles` | Read leaderboard and player profile |
| Assessment | `/api/candidate` | Candidate join |
| Assessment | `/api/assessment/company` | Manage company problems and assessments; list candidates |
| Assessment | `/api/candidate/workspace` | Read workspace/problems, register/start/finish, submit, read leaderboard, directory, and candidate tournaments |
| Analytics | `/api/analytics` | Health/check endpoint and server-sent dashboard stream |
| Analytics | `/api/analytics/audit` | Create and query audit records |
| Gateway | `/api/gateway` | Gateway metrics |
| Sandbox | `/api/sandbox` | `POST /execute` |

For the exact HTTP methods, parameter names, DTOs, validation, and response
codes, see the corresponding controller and request/response types in each
service's `src/main/java` tree.

## 6. Data ownership

Persistence is owned by the services rather than shared through a single
application schema:

- **Auth** uses a relational `User` entity and Spring Data JPA.
- **Arena** uses MongoDB documents for problems, matches, match records,
  daily questions, and player profiles.
- **Assessment** uses MongoDB documents for assessments, company problems,
  candidate registrations/submissions, and leaderboard records.
- **Analytics** has a relational system-log entity; analytics also exposes a
  live dashboard stream.
- **Gateway** uses Redis integration for gateway capabilities such as
  rate-limiting/configuration as wired in the gateway.
- **Sandbox** is an execution service; it is not the system of record for
  application users or assessments.

The entity/document classes and repository interfaces are the schema source
of truth. Review those classes and the relevant service configuration before
changing persistence fields or collection/table behavior.

## 7. Local development

### Prerequisites

- Docker Engine with the Docker Compose plugin.
- Node.js and npm for the web client.
- A compatible JDK and Maven for running Spring services directly.

### Start the backend

From the repository root, use the development Compose file:

```powershell
docker compose up --build -d
docker compose ps
```

Follow logs for a service with:

```powershell
docker compose logs -f gateway-service
```

Local service configuration and required variables are defined by the
application configuration and Compose setup. Supply secrets through your local
secret/configuration mechanism; do not commit them. Do not copy production
credentials into development.

The sandbox sends submissions to the OnlineCompiler.io HTTPS API by default and
needs only an `ONLINECOMPILER_API_KEY`. Arena, tournament and assessment
submissions may be written in Java, Python, C++, C, JavaScript, TypeScript, Go
or Rust; the client sends a `language` id and the sandbox selects the matching
compiler. Defaults are pinned in `code-sandbox/src/main/resources/application.yaml`
and can be overridden per language with `ONLINECOMPILER_COMPILER_<LANGUAGE>`
(for example `ONLINECOMPILER_COMPILER_CPP=g++-15-cpp20`). The legacy
`ONLINECOMPILER_COMPILER` value remains the Java fallback. The optional Docker
backend requires an explicit `JUDGE_DOCKER_HOST` or `JUDGE_DOCKER_SSH_HOST`
plus access to a Docker Engine, is privileged, and supports Java only; use a
trusted development host and read the
[deployment security notes](./README-deployment.md) before enabling it.

### Start the web client

```powershell
Set-Location O-one-outpost-web
npm install
npm run dev
```

Vite prints the local URL when the dev server starts. The API base URL and
other frontend configuration must match the local backend setup.

### Run a service outside Compose

Each Java service includes a Maven wrapper. From a service directory, use
`.\mvnw.cmd spring-boot:run` on Windows or `./mvnw spring-boot:run` on
Unix-like systems, after providing that service's required configuration and
dependencies.

## 8. Testing and quality checks

The frontend exposes these package scripts:

```powershell
npm run lint
npm run build
```

Run them from `O-one-outpost-web`. Java services use Maven and include service
test source sets under `src/test`; run the focused service suite with:

```powershell
.\mvnw.cmd test
```

Run tests in each changed service and the frontend checks affected by a change.
For changes crossing service boundaries, validate the workflow through the
gateway and verify the relevant Compose services together.

## 9. Security and operational constraints

- Production publishes the gateway as the public entry point; internal
  services should remain on the internal Docker network. See the deployment
  guide for the actual production topology.
- The deployment documentation describes an internal shared-secret header in
  addition to network isolation. Treat secrets as secrets and rotate them
  through the deployment process.
- The sandbox executes untrusted code remotely through OnlineCompiler.io by
  default. The API key is server-side only, and submissions leave your
  infrastructure and are processed by the provider; confirm this matches your
  data-handling policy.
- The optional Docker backend executes untrusted code by asking the host Docker
  daemon to create constrained, ephemeral containers. Mounting the Docker socket
  gives the sandbox substantial host-level authority; this is a material trust
  boundary, not a substitute for host isolation.
- Execution containers are documented as using resource limits, disabled
  networking, read-only filesystems, and temporary writable mounts. Keep
  these restrictions in place and review sandbox changes as security-sensitive.
- Never expose internal service ports directly to the public internet.
- Do not include credentials, private keys, access tokens, or production
  configuration values in documentation, source control, or issue reports.

For production sizing, deployment steps, CI/CD secrets, and the detailed
sandbox lifecycle, use the [Botkeep deployment guide](../deploy/README.md).

## 10. Maintenance and documentation ownership

When changing the system:

1. Update this guide when a service responsibility, user workflow, route
   family, data owner, or local development step changes.
2. Update the Botkeep deployment guide (`deploy/README.md`) when production
   topology, build, configuration, or operator steps change. Keep the legacy
   EC2 guides accurate only while that fallback is maintained.
3. Keep API details anchored to controller DTOs and tests; avoid documenting
   behavior that is not implemented.
4. Add or update tests with behavior changes, and record operationally
   significant limitations in the relevant deployment documentation.

This guide is a current system overview, not a complete OpenAPI specification
or a release history. Those should be maintained separately if the project
adopts them.
