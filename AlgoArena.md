# AlgoArena / O(1) Outpost

> **A two-sided competitive programming and technical hiring platform.**
> Engineers build a verified skill signal through real-time 1v1 algorithmic battles. Companies turn that signal into a faster, fairer hiring pipeline through private, proctored coding assessments.

**Repository name:** AlgoArena  
**Product name:** O(1) Outpost  
**Document type:** Portfolio project description, architecture note, and business case  
**Prepared from:** the source in this repository (November 2026)  
**Secrets:** no `.env` file, credential, or private key was read or reproduced in this document.

---

## 1. Executive summary

AlgoArena is not "another LeetCode clone." It is two products built on one execution engine:

1. **A consumer skill ladder** — a live 1v1 arena where engineers solve algorithmic problems under time pressure, gain or lose Elo, and build a public, verifiable track record.
2. **A B2B hiring product** — a private assessment platform where companies create their own question vault, invite candidates with a short test code, run a timed and monitored coding session, and receive a ranked shortlist.

The same secure code-execution service powers both. That is the core architectural and commercial insight: the expensive, risky capability (running untrusted code safely at low latency) is built once and monetised twice — once through engagement and reputation, once through recruiting workflow.

### The one-line pitch

> **AlgoArena turns code execution into a trusted measurement layer for engineering talent — a public arena for engineers and a private assessment engine for companies.**

### What makes it portfolio-worthy

- **Six independently deployable Spring Boot services** behind a single Spring Cloud Gateway, not a monolith with folders.
- **A genuine untrusted-code execution boundary**: warm Docker containers, no network, read-only filesystem, CPU/memory caps, and kill-and-replace lifecycle.
- **Two distinct business workflows** (B2C competition and B2B hiring) sharing identity, execution, and observability.
- **Security decisions that are enforced in code**, not just described on a landing page: JWT verification at the edge, role-based route gates, internal service authentication, rate limiting, IP jailing, server-side answer keys, and server-authoritative time.
- **A realistic operating constraint**: the entire production topology is designed to fit a small cloud host, with explicit JVM and container budgets rather than infinite-scale assumptions.

---

## 2. The problem and the product

### 2.1 The problem

Technical hiring is expensive and noisy:

- Resumes and self-reported skills are weak signals.
- Take-home tests leak, are reused, and are often completed by someone else.
- Live interviews are costly, inconsistent between interviewers, and hard to compare across candidates.
- Candidates have no credible, portable way to prove algorithmic ability outside a handful of well-known platforms.
- Companies need a defensible, auditable selection process — especially for high-volume campus or early-career hiring.

### 2.2 The product

AlgoArena solves this with a **measured coding environment**:

| User | What they get | Why it matters |
|---|---|---|
| **Engineer (USER)** | Real-time 1v1 matches, Elo rating, win streaks, global leaderboard, daily challenge, practice problems | A portable, gamified proof of skill |
| **Company (COMPANY)** | Private question vault, assessment builder, invite codes, candidate registration, timed workspace, ranked results | A repeatable hiring funnel with less interviewer time |
| **Overseer (ADMIN)** | Problem library, daily challenge scheduling, admin creation, live health/traffic monitoring, audit logs | Operational control and platform integrity |

### 2.3 The two-sided loop

```
Engineers compete  →  credible Elo + profile  →  companies trust the signal
        ↑                                              ↓
public arena engagement  ←  branded assessments  ←  more hiring activity
```

The arena creates **supply** (engaged, ranked engineers). The hiring product creates **demand** (companies willing to pay for access to that supply and for their own assessments). Neither side is a demo feature; both are implemented in the repository.

---

## 3. What the platform actually does

### 3.1 Engineer journey

1. Register with email and password.
2. Receive a six-digit OTP by email; verify within five minutes.
3. Log in and land in the lobby with Elo, matches played, win rate, streak, and top-player leaderboard.
4. Enter matchmaking. The system pairs the player with someone within a ±400 Elo band.
5. Both players receive a problem selected for the average match rating.
6. Each player writes Java in a Monaco editor and submits.
7. The server wraps the submission in a hidden test harness, compiles and runs it in an isolated container, and broadcasts test progress to both players over WebSocket.
8. The first player to pass all tests wins. Elo is recalculated with K = 32 and both profiles are updated.
9. Leaving a live match counts as a forfeit; the opponent wins.

### 3.2 Company journey

1. Register through the company portal; the account receives the `COMPANY` role.
2. Build a **private question vault**: title, difficulty, description, method signature, starter code, hidden/public test cases, time and memory limits.
3. Create an assessment: title, role/department metadata, start time, duration, and selected private problems.
4. The platform generates a short invite code such as `A7X9-B2`.
5. Candidates discover the assessment in the directory or join with the code.
6. Candidates register with a detailed profile: education, CGPA, skills, links, preferred language, availability, webcam/internet confirmation.
7. On start, the candidate enters a timed, full-screen workspace with the selected problems.
8. Submissions are scored with partial credit per test case. Hidden tests are never sent to the browser.
9. The leaderboard is **blacked out until the assessment ends**, preventing score-chasing and answer sharing during the window.
10. The company views ranked candidates with score and time penalty, then makes the selection decision.

### 3.3 Overseer journey

1. Sign in through a separate admin entry point.
2. Monitor all six services through a live Server-Sent Events (SSE) dashboard: health, requests per second, active WebSockets.
3. Browse, search, edit, and delete public problems.
4. Schedule the daily challenge with an activation time; the problem unlocks automatically for 24 hours.
5. Create additional admin operators.
6. Inspect audit logs from backend services and frontend events.

---

## 4. Why the architecture is special

### 4.1 The central idea: separate the business products, share the hard capability

A naive design would build two applications: a competitive site and a hiring site. That duplicates the hardest, most security-sensitive component — sandboxed code execution — and duplicates identity, monitoring, and problem management.

This platform instead separates **business domains** while sharing **platform capabilities**:

```
                    ┌──────────────────────────────────────────┐
                    │        Shared platform capabilities       │
                    │  identity · execution · observability     │
                    └───────────────┬──────────────────────────┘
                                    │
          ┌─────────────────────────┴─────────────────────────┐
          │                                                   │
 ┌────────▼─────────┐                               ┌─────────▼────────┐
 │  Public Arena     │                               │ Private Hiring   │
 │  1v1 · Elo        │                               │ Assessments      │
 │  daily challenge  │                               │ Question vault   │
 │  leaderboard      │                               │ Candidate funnel │
 └───────────────────┘                               └──────────────────┘
```

The result is a **portfolio of products on one operating system**, not a pile of CRUD services.

### 4.2 Service map

| Service | Port | Business responsibility | Data owned | Why it is separate |
|---|---:|---|---|---|
| **Gateway** | 8080 | Single public entry point, JWT verification, RBAC, rate limiting, IP jail, internal-token injection, request metrics | Redis (ephemeral counters, jail keys) | Centralises trust and abuse control so every service does not re-implement it |
| **Auth** | 8081 | Registration, email OTP, login, role issuance, admin creation | PostgreSQL `app_users` | Identity lifecycle changes independently of matches or assessments |
| **Arena** | 8084 | Public problem library, daily challenge, matchmaking, match lifecycle, Elo, profiles, WebSocket fan-out | MongoDB: `problem`, `matches`, `match_records`, `daily_question`, `player_profiles` | Competitive domain has its own rules, read patterns, and real-time behaviour |
| **Assessment** | 8083 | Company question vault, assessment builder, candidate registration, timed workspace, submissions, scoring, leaderboard | MongoDB: `company_problems`, `assessments`, `assessment_registrations`, `assessment_submissions`, `assessment_leaderboards` | Hiring domain has different privacy, timing, and audit requirements |
| **Analytics** | 8082 | Health checks, traffic aggregation, audit log ingestion/query, SSE dashboard stream | PostgreSQL `system_logs` | Operational visibility must survive failure of the services it observes |
| **Code Sandbox** | 8085 | Secure compilation and execution of untrusted code | None (stateless execution utility) | The highest-risk capability is isolated behind a narrow, internal-only API |

### 4.3 Data ownership instead of a shared database

Each service owns its schema. There is no cross-service join and no shared "god database."

- Relational data (users, audit logs) lives in PostgreSQL, where uniqueness and transactional integrity matter.
- Document data (problems with nested test cases, assessments with problem ID lists, registrations with rich candidate profiles) lives in MongoDB, where the shape is naturally hierarchical and evolves quickly.
- Redis holds only ephemeral coordination state: OTPs, cooldown keys, rate-limit buckets, and IP jail entries.

This is a deliberate **polyglot persistence** decision, not database sprawl. Each store is chosen for the access pattern of its owning service.

### 4.4 The trust boundary

```
Internet
   │
   ▼
[ Gateway :8080 ]  ← only published port in production
   │  JWT verified, role checked, X-Internal-Token injected
   ├──────────────► Auth        :8081  (internal only)
   ├──────────────► Arena       :8084  (internal only)
   ├──────────────► Assessment  :8083  (internal only)
   └──────────────► Analytics   :8082  (internal only)
                          │
                          ▼
                    [ Sandbox :8085 ]  (internal only)
                          │
                          ▼
                 Ephemeral Docker containers
                 no network · 128 MB · 0.5 CPU
```

Three layers protect internal services:

1. **Network isolation** — only the gateway publishes a host port; everything else uses `expose` on an internal bridge network.
2. **Edge authentication and authorisation** — the gateway verifies the JWT signature and expiry, extracts the subject and role, enforces route-level role rules, and strips the client's identity claims before injecting its own `loggedInUser` and `X-User-Role` headers.
3. **Internal shared secret** — the gateway injects `X-Internal-Token`; each downstream service rejects requests that do not present it when a secret is configured. In local development the check is a no-op so the system remains easy to run.

This is defense in depth, not a single magic header.

---

## 5. Architecture in motion

### 5.1 A 1v1 match, end to end

```
Browser                Gateway              Arena                 Sandbox
   │                      │                   │                      │
   │ POST /api/match/join │                   │                      │
   ├─────────────────────►│  JWT + role check │                      │
   │                      ├──────────────────►│                      │
   │                      │                   │ Elo ±400 lookup      │
   │                      │                   │ pair or queue        │
   │                      │                   │ select problem       │
   │ ◄────────────────────┴───────────────────┤                      │
   │  matchId + problemId (or WAITING)        │                      │
   │                      │                   │                      │
   │ STOMP /topic/lobby/{user}                │                      │
   │ ◄────────────────────────────────────────┤ MATCH_FOUND          │
   │                      │                   │                      │
   │ POST /api/match/{id}/submit              │                      │
   ├─────────────────────►├──────────────────►│ build Java harness   │
   │                      │                   ├─────────────────────►│
   │                      │                   │                      │ compile + run
   │                      │                   │                      │ classify result
   │                      │                   │ ◄────────────────────┤
   │                      │                   │ parse PROGRESS x/y   │
   │                      │                   │ Elo + profiles       │
   │ STOMP /topic/match/{id}                  │                      │
   │ ◄────────────────────────────────────────┤ progress / SUCCESS   │
```

Two design choices stand out:

- **REST carries commands and truth.** Joining, submitting, leaving, and scoring are HTTP operations with server-side validation.
- **WebSocket carries experience.** STOMP topics push lobby events and match progress so the UI feels live without polling.

### 5.2 A company assessment, end to end

```
Company                 Assessment service              Sandbox
   │                          │                            │
   │ create private problem   │                            │
   ├─────────────────────────►│ companyId ownership        │
   │ create assessment        │                            │
   ├─────────────────────────►│ generate testCode          │
   │                          │ status = UPCOMING          │
   │                          │                            │
Candidate                     │                            │
   │ register (profile)       │                            │
   ├─────────────────────────►│ REGISTERED                 │
   │ join with test code      │                            │
   ├─────────────────────────►│ time window + registration │
   │                          │ status = IN_PROGRESS       │
   │ start workspace          │ startedAt stamped once     │
   ├─────────────────────────►│                            │
   │ submit code              │                            │
   ├─────────────────────────►│ sanitize hidden tests?     │
   │                          │ no — inject all tests      │
   │                          ├───────────────────────────►│
   │                          │                            │ compile + run
   │                          │ ◄──────────────────────────┤
   │                          │ partial score + delta      │
   │ ◄────────────────────────┤ leaderboard update         │
   │                          │                            │
Company                       │                            │
   │ view candidates          │                            │
   ├─────────────────────────►│ owner check + ranked list  │
```

### 5.3 Request and response rules

| Concern | Implementation |
|---|---|
| Public entry | Only the gateway; production Compose uses `expose` for internal services |
| Authentication | JWT signed with a shared secret; gateway validates signature and expiry |
| Identity propagation | Gateway injects `loggedInUser` (JWT subject) and `X-User-Role` |
| Service-to-service trust | `X-Internal-Token` shared secret; no-op when unset in local dev |
| Rate limiting | Redis token bucket, 10 requests/second replenish, 20 burst per user key |
| Abuse response | More than 1,000 requests from one IP in five minutes → 24-hour jail (HTTP 403) |
| Timeouts | Gateway connect timeout 5 s; response timeout 10 s |
| WebSocket | STOMP over SockJS at `/ws-arena`; simple broker on `/topic` |
| Operational telemetry | SSE stream every 2 s; gateway RPS snapshot every 1 s; arena WebSocket gauge |
| Failure UX | Axios interceptor detects network/5xx and routes to a dedicated "server down" screen; 401 clears the session |

---

## 6. Module-by-module business logic

This section is intentionally written in business language first, implementation second. Each module is treated as a product capability with rules, not as a folder of classes.

### 6.1 Gateway service — the front door, the bouncer, and the meter

**Business question:** *How do we expose one reliable API without exposing six internal services, and how do we stop abuse before it reaches expensive systems?*

**What it does**

- Routes `/api/auth/**` to Auth, `/api/match/**`, `/api/problems/**`, `/api/profiles/**`, `/api/arena/**` to Arena, `/api/candidate/**` and `/api/assessment/**` to Assessment, `/api/analytics/**` to Analytics, and `/api/gateway/metrics` locally.
- Keeps `/api/problems/daily` public so the daily challenge can be read without login; all admin daily routes fall under the protected arena route.
- Verifies the JWT, extracts the user identity and role, and forwards them as headers.
- Enforces role rules at the edge:
  - `COMPANY` or `ADMIN` for `/api/assessment/company/**`.
  - `ADMIN` for any path containing `/admin/`.
  - `USER` for match execution and candidate workspace routes.
  - `USER` for player profiles, except the public leaderboard.
- Applies a Redis-backed rate limiter per user and an IP jail for sustained abuse.
- Injects the internal shared secret into every downstream request.
- Counts requests and exposes a one-second RPS snapshot for the operations console.

**Business rules encoded**

| Rule | Why it exists |
|---|---|
| Company routes require `COMPANY`/`ADMIN` | A candidate must never be able to create or inspect hiring assessments |
| Admin routes require `ADMIN` | Problem authoring and daily scheduling are platform operations |
| Execution routes require `USER` | Companies and admins should not consume player matchmaking or candidate attempts |
| Profile routes require `USER` (leaderboard exempt) | Player reputation belongs to players; companies should not impersonate them |
| Rate limit per identity | Protects the sandbox and database from runaway clients and credential stuffing |
| IP jail | Adds a cheap, stateful defence against volumetric abuse without a WAF |

**Failure modes handled**

- Missing or malformed `Authorization` → 401.
- Expired or forged token → 401.
- Wrong role → 403.
- Too many requests → 429, then a 24-hour 403 for the IP.
- Downstream unavailable → the SPA shows a server-down screen rather than a broken page.

**Business value:** a single, auditable policy layer. The internal services stay simple and are not reachable from the internet.

---

### 6.2 Auth service — identity, verification, and role issuance

**Business question:** *How do we know who is a player, a company, or an operator, and how do we prevent fake or unverified accounts from entering the funnel?*

**What it does**

- Registers users with email + password and a requested role (`USER` by default, `COMPANY` when requested).
- Hashes passwords with Spring Security's password encoder.
- Generates a six-digit OTP, stores it in Redis for five minutes, and emails it.
- Enforces a 45-second resend cooldown to prevent mail abuse.
- Marks the account verified only after the correct OTP is presented.
- Issues a 24-hour JWT containing the email subject and the role claim.
- Allows an existing admin to create another admin; there is no public admin sign-up.
- Rejects login until the email is verified.

**Business rules encoded**

| Rule | Why it exists |
|---|---|
| Email verification before login | Stops throwaway accounts and validates company contactability |
| OTP TTL = 5 minutes | Limits the window for interception and brute force |
| Resend cooldown = 45 seconds | Protects the email provider and the user's inbox |
| Role assigned at registration, embedded in JWT | Downstream services do not need a user lookup for every request |
| Admin creation is admin-only (`@PreAuthorize`) | Prevents privilege escalation through a public endpoint |
| `COMPANY` self-registration | Low-friction B2B onboarding; domain verification is a roadmap hardening item |

**State machine**

```
UNVERIFIED ──verify OTP──► VERIFIED ──login──► JWT (24 h)
     │
     ├── resend OTP (after 45 s)
     └── OTP expires (5 min) → request a new one
```

**Business value:** a clean identity boundary that supports three very different customer types without duplicating login logic in every product.

---

### 6.3 Arena service — the competitive engine

**Business question:** *How do we create a fair, engaging, repeatable skill signal that engineers want to climb and companies can trust?*

**What it does**

1. **Public problem library** — problems with difficulty, description, starter Java code, method signature, test cases (public and hidden), time limit, and memory limit.
2. **Daily challenge** — a scheduled problem with an activation instant and automatic 24-hour expiry. Before activation the API returns `403` with a countdown and no problem content. After expiry it returns `404`. Within the window it returns the problem plus `remainingSeconds`. This is a LeetCode-style server-side time lock.
3. **Matchmaking** — a player enters the queue with their Elo. The service looks for a waiting match within ±400 rating. If found, it pairs them, sets `IN_PROGRESS`, chooses a problem by average rating, and notifies both players. If not, it creates a `WAITING` match.
4. **Difficulty selection** — average Elo below 1200 → `EASY`; below 1800 → `MEDIUM`; otherwise `HARD`. If no problem exists at that difficulty, it falls back to any available problem and fails loudly only if the collection is empty.
5. **Match execution** — on submission, the service loads the match, builds a complete Java harness around the user's solution, injects all test cases, forwards it to the sandbox, parses `PROGRESS|passed/total`, and broadcasts the result.
6. **Win resolution** — the first `ALL_TESTS_PASSED` wins. Elo is updated with K = 32. Win/loss, streak, and highest streak are updated. Leaving a match forfeits it; the opponent wins.
7. **Leaderboard and profiles** — top 10 by Elo; a profile is created lazily on first access.

**The Elo model**

```
expected = 1 / (1 + 10^((opponentRating - playerRating) / 400))
newRating = round(oldRating + 32 × (actualScore - expected))
```

- A new player starts at **1200**.
- A win against a stronger opponent yields more points than a win against a weaker one.
- A loss against a weaker opponent costs more.
- Ratings are floored at zero.

**Business rules encoded**

| Rule | Why it exists |
|---|---|
| ±400 matchmaking band | Keeps matches competitive and avoids unwinnable pairings |
| Average-rating difficulty | The problem should test both players, not favour the stronger one |
| First to pass all tests wins | A simple, spectator-friendly win condition |
| Leaving = forfeit | Prevents rage-quitting to avoid an Elo loss |
| Idempotent completion | Re-submitting after a decided match does not double-award Elo |
| Hidden test cases | Prevents hard-coding the visible examples |
| Server-side harness | The client never receives the answer key or the hidden tests |

**Real-time experience**

- `/topic/lobby/{playerId}` receives `MATCH_FOUND`.
- `/topic/match/{matchId}` receives progress and outcome events.
- The opponent sees test-by-test progress, which creates the "race" feeling that makes the arena engaging.

**Business value:** the Elo ladder is the **supply-side asset**. It is portable, comparable, and costly to fake at scale because it is earned through timed, executed code.

---

### 6.4 Assessment service — the hiring engine

**Business question:** *How does a company turn its own hiring bar into a repeatable, timed, auditable assessment without leaking questions or losing control of the candidate experience?*

**What it does**

1. **Private question vault** — each problem is stamped with the owning `companyId`; a company cannot read another company's problem. Problems are separate from the public arena collection.
2. **Assessment builder** — a company selects private problems, sets title, role/department metadata, start time, duration, and receives a short test code (`XXXX-XX`).
3. **Candidate directory** — only assessments whose end time is in the future are listed; search and pagination are supported.
4. **Registration** — a candidate registers with a detailed profile: contact, education, CGPA, links, contest rating, problems solved, preferred language, availability, webcam and internet confirmation, skills, and interests.
5. **Join validation** — joining by test code enforces:
   - the code exists;
   - the assessment has started;
   - the candidate is within the 10-minute late-entry window;
   - the assessment has not ended;
   - the candidate is formally registered;
   - the session is not already `COMPLETED` or `TERMINATED`.
6. **Timed workspace** — the first start stamps `startedAt` and moves the registration to `IN_PROGRESS`; re-entry is allowed only while the session is live.
7. **Sanitized problems** — candidates receive only non-hidden test cases.
8. **Submission and scoring** — the service injects the full test suite into a generated Java harness, runs it in the sandbox, parses `ASSESSMENT_RESULT|passed|total`, and awards partial credit:

```
points = (passedTests / totalTests) × difficultyWeight
EASY = 100, MEDIUM = 300, HARD = 500
```

9. **Anti-farming scoring** — only the candidate's best score per problem counts. If a new submission improves the best, only the **delta** is added to the leaderboard. Repeated submissions cannot farm points.
10. **Time-penalty tie-breaker** — every scoring improvement adds elapsed time since the assessment start. More iterations cost more time, so the ranking rewards first-attempt accuracy and speed.
11. **Leaderboard blackout** — the ranked list is hidden until the assessment end time. This prevents candidates from sharing ranks or answers mid-window.
12. **Recruiter selection view** — the company sees candidates sorted by total points descending and time penalty ascending.

**State machine**

```
REGISTERED ──join/start──► IN_PROGRESS ──finish──► COMPLETED
      │                        │
      │                        ├── time expires ─────► COMPLETED
      │                        ├── anti-cheat trigger ► TERMINATED
      │                        └── manual finish ─────► COMPLETED
      └── assessment ends ────────────────────────────► cannot register
```

The lock is idempotent: a `TERMINATED` session cannot be downgraded to `COMPLETED`.

**Business rules encoded**

| Rule | Why it exists |
|---|---|
| Company ownership check on every private problem | Prevents cross-tenant data leaks |
| 10-minute late-entry window | Protects assessment integrity without punishing minor connection issues |
| Registration required before join | The company controls who is in the funnel |
| Server-side time, not client time | A candidate cannot extend the clock by changing their device time |
| Hidden tests stripped from the client payload | Prevents hard-coding and test-case leakage |
| Partial credit | Produces a useful ranking even when nobody solves everything |
| Best-score-only, delta-only leaderboard | Stops point farming through repeated submissions |
| Time penalty on improvement | Rewards accuracy and speed, not brute-force iteration |
| Leaderboard blackout | Preserves fairness during the live window |
| `COMPLETED`/`TERMINATED` cannot re-enter | A finished attempt is final |

**Business value:** this is the **demand-side product**. It replaces a spreadsheet plus a take-home plus a subjective review with a controlled, measurable funnel. The company keeps its own question IP, sees only its own candidates, and gets a defensible ranking.

---

### 6.5 Code sandbox — the trust boundary

**Business question:** *How do we run arbitrary, potentially malicious code from strangers without letting it damage the platform, the data, or the host?*

**What it does**

- Maintains a pool of **three pre-warmed** `eclipse-temurin:17-alpine` containers.
- Borrows a container for a submission, writes the source over `stdin` (no `docker cp`), compiles with `javac`, and runs with a per-problem heap limit.
- Reads `stdout` and `stderr` concurrently on separate threads.
- Waits exactly the problem's time limit; on timeout, kills the process.
- Classifies the outcome:
  - `COMPILATION_ERROR` when stderr contains `error:`;
  - `RUNTIME_ERROR` for other stderr output;
  - `TIME_LIMIT_EXCEEDED` when the wait expires;
  - `MEMORY_LIMIT_EXCEEDED` when the process exits with code 137 (Linux OOM kill);
  - otherwise returns the program output, which includes the harness's progress marker.
- **Never reuses a container.** After every execution it removes the used container and asynchronously warms a replacement.

**Isolation controls**

| Control | Value | Purpose |
|---|---|---|
| Network | `--network none` | No exfiltration, no external calls, no lateral movement |
| Memory | `--memory=128m`, `--memory-swap=128m` | Hard cap; swap disabled |
| CPU | `--cpus=0.5` | Prevents one submission from starving the host |
| Root filesystem | `--read-only` | No persistent writes |
| `/app` | `tmpfs`, 32 MB, `noexec` | Writable workspace for source and class files |
| `/tmp` | `tmpfs`, 16 MB, `noexec` | Scratch space |
| Lifetime | Removed after every run | Prevents class-file leakage and cross-tenant contamination |
| Pool | 3 warm containers | Removes image pull and JVM start from the request path |

**The subtle details that matter**

- Writing source over `stdin` removes a `docker cp` round trip and a temp file from the hot path.
- Reading `stdout` and `stderr` on separate threads prevents the classic pipe-buffer deadlock: if the child fills the OS pipe (~64 KB) while the parent is blocked in `waitFor`, the process appears to hang and is misclassified as a time-limit error.
- Exit code 137 is the reliable signal for an OOM kill; inspecting the JVM's own error message is too late and too inconsistent.
- `noexec` on `/app` is safe because the JVM reads bytecode; it does not need to execute a binary from that mount.
- The container is destroyed even on failure. The replacement is asynchronous, so the user does not pay for pool refill.

**Business value:** this is the capability that makes both products credible. A platform that cannot safely run untrusted code cannot sell either competition or hiring.

---

### 6.6 Analytics service — the control tower

**Business question:** *How does a small team know the platform is healthy, how much traffic it is handling, and what happened during an incident?*

**What it does**

- Pings all five peer services every two seconds.
- Streams health, traffic, and alerts to the admin dashboard over SSE.
- Reads gateway RPS from `/api/gateway/metrics` and active WebSocket count from `/api/arena/metrics`.
- Stores audit records (`serviceName`, `logLevel`, `message`, `stackTrace`, `timestamp`) in PostgreSQL.
- Serves the last 100 logs or filters by service name.

**A key correctness decision**

The health check uses `exchangeToMono(...)` rather than `retrieve()`. `retrieve()` throws on 4xx/5xx; suppressing that exception would also suppress real connection failures, making every service look `UP`. `exchangeToMono` treats **any HTTP response** as proof of life and lets only network-level failures (connection refused, timeout) become `DOWN`. This is the difference between a dashboard that lies and one that is useful.

**Business value:** operational confidence without a full observability stack. For a small deployment, this is the right amount of tooling: enough to see service death and traffic, not so much that it becomes its own project.

---

### 6.7 Web client — three products in one SPA

**Business question:** *How do we give engineers, companies, and admins completely different experiences without building three frontends?*

**What it does**

- React 18 + Vite single-page application with role-guarded routes.
- **Engineer experience:** landing page, login/signup, lobby, matchmaking, Monaco arena with split panes, live match log, progress bars, result overlay.
- **Company experience:** company login/signup, dashboard, private question vault, assessment builder, candidate selection table.
- **Candidate experience:** assessment directory, registration form, "my tournaments" dashboard, timed workspace, post-assessment leaderboard.
- **Overseer experience:** separate login, live service monitor, problem library, daily scheduler, problem injection/editing, admin creation, audit log viewer.
- **Session integrity:** decodes the JWT, re-syncs the stored role from the token, proactively signs out at expiry, clears storage on 401, and routes to a server-down screen on network/5xx failures.
- **Assessment integrity:** fullscreen enforcement, tab-switch and window-blur grace periods, copy/paste/cut blocking, right-click blocking, DevTools shortcut blocking, and an unload warning.
- **Resilience:** Axios interceptors centralise token attachment and failure handling; SSE reconnects after five seconds.

**Business value:** one deployable frontend, three coherent journeys, and a consistent security posture. The role boundary is visible in the UI but enforced in the backend.

---

## 7. Tech stack and why it was chosen

| Layer | Choice | Real reason, not resume keywords |
|---|---|---|
| Frontend | React 18, Vite, React Router | Fast iteration, simple role-based routing, large ecosystem for the editor and real-time clients |
| Editor | Monaco | The same editor engine as VS Code; familiar to engineers and good enough for a serious coding surface |
| Real-time client | STOMP.js + SockJS | Simple topic subscriptions for lobby and match events; SockJS fallback for restrictive networks |
| Backend | Java 21, Spring Boot 4.x | Strong typing, mature security, first-class scheduling, WebSocket, JPA, and MongoDB support |
| Gateway | Spring Cloud Gateway (WebFlux) | Reactive routing with composable filters for JWT, RBAC, rate limiting, and metrics |
| Identity | Spring Security + JJWT | Standard, auditable password hashing and signed-token verification |
| Relational store | PostgreSQL | Users and audit logs benefit from constraints, indexes, and transactional writes |
| Document store | MongoDB | Problem statements with nested test cases and candidate profiles are naturally document-shaped |
| Cache / coordination | Redis | TTL semantics are perfect for OTPs and cooldowns; atomic increments for rate limits and jails |
| Execution | Docker + `eclipse-temurin:17-alpine` | Strong process isolation with a small runtime and a familiar Java toolchain |
| Telemetry | SSE + Actuator health probes + in-process counters | Low operational overhead; no Kafka/ELK/Prometheus stack to run for a small deployment |
| Delivery | Docker Compose, GitHub Actions, Docker Hub, AWS EC2 | Reproducible images, a simple matrix build, and a deployment target that matches the project's scale |

**Deliberate non-choices**

- No Kafka: the only asynchronous needs are progress fan-out and pool refill, both handled in-process or by a thread pool. A broker would add operational cost without a current business requirement.
- No service mesh: six services with a single ingress do not justify Istio's complexity.
- No Kubernetes: the target deployment is one small EC2 host. Compose with health checks and memory limits is the honest fit.
- No shared database: service ownership was prioritised over convenience joins.

---

## 8. The mental model

### 8.1 Five planes

Think of the platform as five planes that can fail independently:

| Plane | Services | Question it answers |
|---|---|---|
| **Identity plane** | Auth, Gateway | Who is this, and what are they allowed to do? |
| **Competition plane** | Arena | Who is better, by how much, and in real time? |
| **Hiring plane** | Assessment | Can a company measure and compare candidates fairly? |
| **Execution plane** | Sandbox | Can we run this code without trusting it? |
| **Observability plane** | Analytics, Gateway metrics | Is the system healthy, and what happened? |

The planes share contracts, not databases. That is what allows the arena and the hiring product to evolve separately while using the same identity and execution capabilities.

### 8.2 Seven design principles

1. **Each service owns its data.** No cross-service joins, no shared schema.
2. **The gateway is the only public trust boundary.** Internal ports stay internal.
3. **Untrusted code never runs in a service JVM.** It runs in a disposable, constrained container.
4. **Time is server-authoritative.** Client clocks are for display, never for eligibility.
5. **Answer keys never leave the server.** Hidden tests are injected server-side.
6. **REST is truth; WebSocket is experience.** Commands are validated synchronously; progress is a broadcast.
7. **Fail closed on auth, fail safe on telemetry.** A missing token is rejected; a failed health ping is reported, not fatal.

### 8.3 State machines are the business rules

The most important business logic is expressed as allowed transitions:

- **Match:** `WAITING → IN_PROGRESS → COMPLETED` (leave is a forfeit that also ends in `COMPLETED`).
- **Registration:** `REGISTERED → IN_PROGRESS → COMPLETED | TERMINATED`.
- **Assessment:** created as `UPCOMING`; eligibility is derived from the time window, not from a client flag.
- **OTP:** issued with a five-minute TTL; verified once; resend blocked for 45 seconds.

If a transition is not allowed, the API says so. That is more reliable than hiding a button in the UI.

---

## 9. Difficult problems and how they were solved

These are the problems that shaped the system. Each one is stated as an engineering constraint, not as a feature.

### 9.1 The cold-start versus isolation trade-off

**Problem:** Starting a fresh container for every submission is the safest design, but image pull, container creation, and JVM startup add seconds to every run. In a 1v1 match, that latency destroys the experience; in an assessment, it looks like a broken product.

**What made it hard:** The obvious optimisation — reuse a container — creates cross-tenant contamination. A previous submission's class files, environment, or process state could leak into the next run. The safe and the fast options appeared mutually exclusive.

**Resolution:** A **warm pool** of three containers is created at startup and kept ready. A submission borrows one, writes source over `stdin`, compiles, runs, and then the container is destroyed. A replacement is warmed asynchronously. The fast path is warm, but every execution still gets a clean container.

**Result:** No image pull or container creation in the request path, while preserving the "never reuse" isolation guarantee.

### 9.2 The pipe-buffer deadlock that looked like a timeout

**Problem:** Some submissions timed out even though the code was correct.

**What made it hard:** The sandbox waited for the process with `waitFor(timeout)` while reading output only afterwards. If the child wrote more than the operating system's pipe buffer (~64 KB — easy with verbose output or a large test suite), the child blocked on write, the parent blocked on wait, and the run was classified as a time-limit error. The symptom pointed at the user's algorithm; the cause was the harness.

**Resolution:** Two dedicated reader threads consume `stdout` and `stderr` concurrently while the process runs. The readers are cancelled before a forced kill. Their results are collected with a short guarded timeout so the service can never hang after the process ends.

**Result:** Accurate classification of timeouts and runtime errors; no more false time-limit verdicts caused by output volume.

### 9.3 Answer-key leakage and hard-coded solutions

**Problem:** If the browser receives all test cases, a candidate can hard-code outputs or share the tests. If the browser receives only visible tests, the assessment is too easy to game.

**What made it hard:** The code has to be compiled and run somewhere. The tests must reach the runner, but not the client, and the client still needs enough examples to understand the problem.

**Resolution:** The server owns the harness. It builds a complete Java `Main` class around the submitted `Solution`, injects every test case (including hidden ones), runs it in the sandbox, and returns only a verdict and the count of passed tests. Candidate-facing problem payloads strip hidden cases before serialization. The leaderboard is blacked out until the assessment ends.

**Result:** The answer key never crosses the trust boundary. Cheating requires solving the problem, not inspecting the network tab.

### 9.4 Fair scoring under repeated submissions

**Problem:** A candidate can submit the same problem repeatedly. A naive leaderboard adds points every time, so persistence beats skill. A naive "best score only" rule still allows gaming if partial improvements are added on top of the full previous score.

**What made it hard:** The system must support iteration (developers debug), reward improvement (partial credit), and still produce a fair ranking.

**Resolution:** For each candidate/problem pair, the service computes the previous maximum points. A new submission only affects the leaderboard if it beats that maximum, and only the **delta** is added. The time penalty is applied only when the score improves. The final sort is points descending, then time penalty ascending.

**Result:** Iteration is allowed, farming is not. The ranking rewards accuracy and speed rather than submission volume.

### 9.5 Time zones, re-entry, and the "my clock says I have time" problem

**Problem:** Assessment windows were drifting by 5 hours 30 minutes, and candidates could re-enter sessions that should have been closed.

**What made it hard:** Java's `LocalDateTime` has no time zone. The frontend parsed it as local time. The server might run in UTC while the business operates in IST. A candidate can change their device clock. The same timestamp meant different instants in different layers.

**Resolution:** The assessment service pins its business clock to a single offset and compares all eligibility in that frame. The frontend appends `Z` when parsing server timestamps so the browser treats them as UTC. The workspace timer is display-only; the server re-checks the end time on every submission. Session locking is a server-side state transition, not a client-side flag. The lock is idempotent so repeated "finish" calls cannot corrupt the state.

**Result:** One authoritative clock; the browser can show a countdown, but it cannot grant extra time.

### 9.6 Real-time match feel without a message broker

**Problem:** Two players need to see each other's progress during a match, but the platform runs on a small host and should not require Kafka or a dedicated STOMP relay.

**What made it hard:** WebSocket fan-out is stateful. A naive per-request poll adds load and latency. A full broker adds operational complexity that is hard to justify at this scale.

**Resolution:** Spring's simple broker handles `/topic` subscriptions in-process. The arena publishes lobby events to `/topic/lobby/{playerId}` and match events to `/topic/match/{matchId}`. SockJS provides a fallback. The client subscribes before joining the queue so it cannot miss the pairing event. Active WebSocket connections are counted with `SessionConnectedEvent`/`SessionDisconnectEvent` and exposed as an operational metric.

**Result:** A live, low-latency match experience with almost no infrastructure. The trade-off is documented: horizontal arena replicas would need a broker relay or sticky sessions.

### 9.7 Security at the edge without trusting the client

**Problem:** The SPA hides buttons based on role, but a hidden button is not authorisation. A candidate could call company endpoints directly; a company could call matchmaking; an admin route could be reached by a normal user.

**What made it hard:** Identity is needed by six services, and re-implementing JWT parsing everywhere creates drift and inconsistency.

**Resolution:** The gateway is the only place that parses the token. It validates the signature, extracts the subject and role, and applies route-level RBAC before forwarding. It overwrites identity headers so a client cannot spoof `loggedInUser`. It injects an internal shared secret so downstream services can reject direct calls. The auth service additionally enforces `@PreAuthorize("hasRole('ADMIN')")` for admin creation.

**Result:** One authorisation policy at the edge, defense in depth inside, and no trust in client-supplied role data.

### 9.8 Running six JVMs on a 4 GB host

**Problem:** Six Spring Boot services, two databases, Redis, and a pool of execution containers will not fit in a default cloud VM if each JVM is allowed to grow freely.

**What made it hard:** Spring Boot is memory-hungry by default. The sandbox must reserve headroom for the ephemeral containers. The gateway and arena carry the most traffic and need more heap than the analytics service.

**Resolution:** Explicit budgets: gateway 384 MB heap / 512 MB container, arena 384/512, auth 320/448, assessment 320/448, analytics 256/384, sandbox 256/384. Lazy initialization is enabled. Health checks and `restart: unless-stopped` keep the topology self-healing. Log rotation caps disk usage. The total is roughly 1.9 GB of heap and 2.7 GB of container limits, leaving headroom for the host and execution containers.

**Result:** A production-shaped deployment on a small EC2 instance, with the constraint made explicit rather than discovered during an outage.

### 9.9 Session drift and silent expiry in a single-page app

**Problem:** Users could remain on a page with an expired or tampered stored role, then see a confusing mix of allowed and forbidden actions.

**What made it hard:** The JWT is the source of truth, but the SPA also stores a role for routing. The two can diverge after a role change, a stale tab, or manual storage edits.

**Resolution:** The client decodes the JWT payload (never trusting it for authorisation), re-synchronises the stored role from the token on load, and starts a timer that signs the user out exactly when the token expires. Axios clears storage and redirects on any 401. The backend still validates every request, so a tampered local role changes nothing server-side.

**Result:** A predictable session lifecycle and a smaller window for stale-role confusion.

### 9.10 Consistency without distributed transactions

**Problem:** A match completion touches a match document, a match record, two player profiles, and possibly a leaderboard. These live in MongoDB, and there is no transaction spanning services.

**What made it hard:** A partial failure could award a win but not update the profile, or update the profile twice on retry.

**Resolution:** Completion is idempotent: if the match is already `COMPLETED`, the service returns the stored ratings instead of recalculating. State transitions are guarded by status checks. Repository queries are targeted (`findByAssessmentIdAndCandidateId`) rather than scanning all documents and filtering in memory. A compound unique index on `(assessmentId, candidateId)` prevents duplicate registrations at the database level, not just in application code.

**Result:** Safe retries and no duplicate registrations. The remaining risk — a multi-document update failing halfway — is acknowledged and is a candidate for a transactional outbox or a reconciliation job.

---

## 10. Business perspective

### 10.1 Market framing

AlgoArena sits at the intersection of two large, adjacent markets:

- **Technical hiring and assessment** — companies spend heavily on sourcing, interviewing, and filtering engineers.
- **Competitive programming and upskilling** — engineers invest time in practice, contests, and visible credentials.

Most products pick one side. AlgoArena's thesis is that the **same verified skill signal** can serve both: engineers want to earn it; companies want to buy access to it.

### 10.2 Customer segments

| Segment | Pain | Willingness to pay | Entry point |
|---|---|---|---|
| **Mid-market engineering teams** | Interview loops are slow and inconsistent | High per open role | Assessment credits or annual seat licence |
| **Campus and early-career hiring** | Thousands of applicants, low signal-to-noise | High volume, price-sensitive | Cohort assessment package |
| **Colleges and bootcamps** | Placement outcomes need proof | Medium | Student cohorts, branded contests |
| **Individual engineers** | No portable proof beyond a resume | Low, but high engagement | Free arena, premium analytics |
| **Enterprise** | Compliance, SSO, private question IP | Very high | Annual contract, private deployment |

### 10.3 Revenue model

A realistic mix, in order of near-term feasibility:

1. **Company assessment credits** — pay per assessment or per candidate seat. This is the simplest wedge: the product already creates an invite code, runs the assessment, and produces a ranked shortlist.
2. **Annual company licence** — unlimited assessments, private question vault, branded experience, priority support. Land with a pilot, expand to the hiring team.
3. **Usage-based execution** — sandbox runs are the marginal cost; heavy hiring events can be priced as a compute package. This aligns price with cost.
4. **Campus / cohort packages** — a fixed fee for a placement season, including contests, proctoring, and ranking.
5. **Candidate premium** — advanced performance analytics, personalised practice plans, and profile visibility (roadmap; must not compromise fairness).
6. **Sponsorship** — branded contests and leaderboards for companies that want engineering brand exposure.

### 10.4 Unit economics

The important cost driver is not storage or bandwidth; it is **code execution concurrency**.

- Each submission is one container run: compile plus execute.
- The warm pool has three containers, so a single sandbox node supports **three concurrent executions**. The fourth request waits up to ten seconds for a borrowed container or receives a system error.
- On a 4 GB host, the pool size is deliberately small to leave room for six JVMs and the OS.

A simple model:

```
assessment compute ≈ candidates × submissions per candidate × average run time
concurrent capacity ≈ sandbox pool size × sandbox nodes
```

For a 500-candidate event with 3 problems and 4 submissions each, that is 6,000 runs. At an average of 1–2 seconds, the raw compute is modest, but the **peak concurrency** is the constraint: without scheduling or additional sandbox nodes, candidates will queue. This is a product decision as much as an infrastructure one: stagger start times, cap concurrent candidates per window, or autoscale sandbox workers.

**Gross-margin levers**

- Warm pool reduces the cost of cold starts.
- Per-problem time limits cap runaway executions.
- Container limits prevent one submission from consuming a whole core.
- Static problem statements and small payloads keep bandwidth negligible.
- Java-only execution keeps the runtime image and toolchain simple; each added language adds image size, compile time, and security surface.

### 10.5 Go-to-market

A two-sided marketplace cannot launch both sides at once. The practical sequence:

1. **Supply first.** Launch the public arena in colleges, coding communities, and bootcamps. Free competition creates ranked profiles and a reason to return.
2. **Prove the signal.** Publish leaderboards, streaks, and Elo histories. Let engineers share a credible profile.
3. **Land the first companies.** Offer a pilot assessment to a mid-market team or a campus placement cell. Use their own private questions so the value is obvious.
4. **Turn pilots into repeatability.** Templates, candidate pipelines, and historical comparisons create switching costs.
5. **Expand upmarket.** Add SSO, audit exports, custom branding, and compliance controls for enterprise buyers.

### 10.6 Moat

- **A portable, earned reputation graph.** Elo plus contest history is hard to fake and becomes more valuable as more engineers participate.
- **Two-sided network effects.** More engineers make the arena more competitive; more companies make the arena more valuable to engineers.
- **Private question IP and workflow lock-in.** A company's vault, assessment templates, and candidate history are painful to migrate.
- **Integrity data.** Patterns of suspicious sessions, repeated submissions, and unusual timing become a proprietary signal.
- **Operational know-how.** Safe, low-latency execution is genuinely hard; a competitor cannot copy it with a landing page.

### 10.7 KPIs that matter

| Funnel stage | Metric | Why it matters |
|---|---|---|
| Acquisition | Weekly new engineers; company sign-ups | Marketplace liquidity |
| Activation | First match completed within 10 minutes | The arena must feel instantaneous |
| Engagement | Matches per active engineer per week; daily challenge participation | Supply-side retention |
| Match quality | Time-to-match p95; match completion rate | A queue that never resolves is a churn machine |
| Assessment health | Assessment completion rate; median score; score spread | A test everyone fails (or everyone passes) is not a useful signal |
| Integrity | Terminated sessions; plagiarism flags; anomalous submission timing | Trust is the product |
| Recruiter value | Time-to-shortlist; candidate-to-interview conversion | The economic buyer's ROI |
| Platform | Sandbox p95 latency; execution error rate; cost per 1,000 runs | Gross margin and reliability |
| Commercial | Pilot-to-paid conversion; net revenue retention | Whether the business works |

### 10.8 Risks and mitigations

| Risk | Why it is serious | Mitigation |
|---|---|---|
| Two-sided cold start | No engineers → no companies; no companies → no reason to compete | Supply-first campus/community launch; free arena; seed with public contests |
| Cheating / AI assistance | Undermines the hiring signal | Server-side tests, blackout leaderboard, proctoring signals, plagiarism analysis, randomised problem sets |
| Sandbox escape | A host compromise is existential | Dedicated worker nodes, rootless Docker, seccomp/AppArmor, gVisor/Firecracker, no host socket in the long term |
| Language coverage | Java-only limits the addressable market | Add runtimes incrementally with per-language images and resource profiles |
| Big-platform competition | HackerRank, CodeSignal, LeetCode have distribution | Differentiate on live arena engagement + verified Elo + private hiring workflow, not on problem count |
| Sales cycle | Enterprise hiring software is slow to buy | Start with self-serve pilots and campus packages; expand to enterprise later |
| Privacy / candidate data | Rich profiles include personal data | Data minimisation, retention policy, access controls, consent, regional hosting |
| Operational concentration | One EC2 host is a single point of failure | Health checks, backups, documented recovery, then multi-AZ/containers |

### 10.9 What a businessman should take away

- The platform is not selling "code problems." It is selling **trusted measurement**.
- The arena is the **customer acquisition and supply engine**; the assessment product is the **revenue engine**.
- The hard technical asset — safe execution — is also the **defensible operating asset**.
- The near-term constraint is not features; it is **concurrency economics** and **marketplace liquidity**. Both are measurable and manageable.
- The roadmap should protect the integrity of the signal above all else. A platform that cannot be trusted has no pricing power.

---

## 11. Portfolio framing

### 11.1 What this project demonstrates

- **Distributed systems design:** six services, clear ownership, synchronous and asynchronous contracts, failure handling, and deployment topology.
- **Security engineering:** an edge trust boundary, role-based authorisation, service-to-service authentication, abuse controls, and untrusted-code isolation.
- **Product thinking:** two business models on one platform, with workflows and state machines that reflect real hiring and competition rules.
- **Operational maturity:** memory budgets, health checks, CI/CD, audit logs, live telemetry, and documented trade-offs.
- **Honest engineering judgment:** deliberate non-choices (no Kafka, no Kubernetes, no shared database) with reasons.

### 11.2 Resume bullets

- Architected a six-service Spring Boot platform (Java 21, Spring Cloud Gateway, React) with JWT/RBAC at the edge, per-service PostgreSQL/MongoDB ownership, and Redis-backed rate limiting and abuse controls.
- Built a warm-pool code-execution sandbox that compiles and runs untrusted Java in ephemeral, network-isolated Docker containers (128 MB / 0.5 CPU), eliminating cold starts from the request path while preserving a clean container per submission.
- Designed an Elo-based matchmaker (±400 band, K = 32) with real-time STOMP progress broadcasting and server-injected test harnesses.
- Implemented a B2B assessment engine with private question vaults, short invite codes, a 10-minute late-entry window, partial-credit scoring, anti-farming leaderboard deltas, and a post-assessment leaderboard blackout.
- Shipped a GitHub Actions matrix pipeline that builds six service images, publishes them to Docker Hub, and deploys the stack to AWS EC2 via Docker Compose.

### 11.3 Sixty-second pitch

> "AlgoArena is a two-sided platform for competitive programming and technical hiring. Engineers compete in real-time 1v1 matches and build an Elo rating; companies use the same secure execution engine to run private, timed assessments with their own question banks. The hard part is running untrusted code safely and fast, so I built a warm Docker container pool with network isolation, hard resource caps, and server-side test injection. The backend is six Spring Boot services behind a single gateway with JWT role enforcement, Redis rate limiting, and a shared internal secret. It runs on a small EC2 host with explicit JVM budgets and a full CI/CD pipeline. The project is interesting because it is both a product and a systems problem: the arena creates the talent signal, the assessment product monetises it, and the sandbox is the trust boundary that makes both credible."

### 11.4 Demo script for a reviewer

1. **Engineer:** sign up → receive OTP → verify → lobby shows 1200 Elo → enter matchmaking → match found → solve a problem → watch opponent progress → win → see Elo change.
2. **Company:** sign up → open the private vault → create a problem with hidden tests → build an assessment → copy the invite code → view the candidate table after a candidate finishes.
3. **Candidate:** register from the directory → join with the code → enter fullscreen workspace → submit a partial solution → see partial credit → leaderboard remains locked until the end.
4. **Admin:** open the overseer console → show live service health and RPS → schedule a daily challenge → inject/edit a problem → inspect audit logs.
5. **Engineering:** show the sandbox logs (borrow, compile, run, destroy, refill), the gateway RBAC block for a wrong role, and the CI workflow.

### 11.5 Interview questions to expect

**"Why microservices instead of a modular monolith?"**
Because the execution boundary is a fundamentally different security and scaling concern from identity, competition, and hiring. The sandbox must be isolated and scaled independently. The arena has real-time state; the assessment has strict privacy and timing. The gateway centralises trust. That said, a modular monolith would be a reasonable starting point for a smaller team; the service boundaries here follow real domain and risk lines.

**"Why both PostgreSQL and MongoDB?"**
Users and audit logs benefit from relational constraints. Problems and assessments are hierarchical documents with nested test cases and variable candidate profiles. Forcing either into the other store would add complexity without business value. Each service owns one store.

**"How do you prevent cheating?"**
Layer defenses: hidden tests never reach the client, the harness is server-generated, the leaderboard is blacked out during the window, sessions are fullscreen and monitored, copy/paste and DevTools shortcuts are blocked, and the server enforces time and lock states. The frontend measures are deterrence; the authoritative controls are server-side. Plagiarism detection and stronger proctoring are roadmap items, not claims.

**"How would you scale the sandbox?"**
The sandbox is stateless, so scale horizontally with one worker node per host, each with its own warm pool. Put a queue in front to smooth bursts, cap concurrent executions per tenant, and autoscale based on queue depth. Longer term, replace the Docker socket with a dedicated execution cluster using rootless containers or a microVM runtime.

**"What would you change if you rebuilt it?"**
Atomic matchmaking claims, a transactional outbox for cross-document consistency, OpenAPI contracts and contract tests, refresh tokens with revocation, multi-language runtimes, and a proper proctoring/telemetry pipeline.

---

## 12. Reality check: current limitations and next steps

A portfolio project is more credible when it is precise about what is shipped.

| Area | Current state | Next step |
|---|---|---|
| Language support | The live arena and routed assessment workspace execute **Java**; the landing page's "10+ languages" is positioning copy, not shipped capability | Add per-language runtimes with isolated images and resource profiles |
| Sandbox trust | Runs through the host Docker socket, which grants substantial host authority | Dedicated worker nodes, rootless Docker, seccomp/AppArmor, gVisor or Firecracker |
| Matchmaking concurrency | Pairing is optimistic (find waiting match, then save); concurrent joins can create unmatched waiting matches | Atomic claim with `findOneAndUpdate` on `status = WAITING` |
| Cross-document consistency | Elo, match record, and profiles are separate writes; a partial failure can drift | Transactional outbox or a reconciliation job |
| Proctoring | Fullscreen, blur/tab grace periods, copy/paste and shortcut blocking | Server-side heartbeat, session telemetry, webcam/identity checks where legally appropriate |
| Sessions | 24-hour JWT, no refresh or revocation list | Short-lived access token + refresh token + revocation |
| High availability | Single EC2 host; Redis, Mongo, and Postgres are external but the app tier is not HA | Multi-instance gateway/arena, managed databases, load balancer |
| Real-time scale | In-process STOMP broker | Broker relay (RabbitMQ/ActiveMQ) when arena replicas exceed one |
| Contracts | Controllers and DTOs are the source of truth | OpenAPI spec, generated clients, contract tests |
| Test coverage | Auth has unit and slice tests; other services have context-load tests | Integration tests for match/assessment flows and sandbox classification |
| Daily challenge UI | Public API and admin scheduler exist; the candidate-facing card is not wired | Add a lobby/dashboard card with countdown and solve link |
| Company verification | Self-service company registration | Domain/email verification, admin approval for sensitive plans |
| Gateway config safety | Route definitions are hand-edited YAML | Schema validation and a smoke test in CI |
| Frontend cleanup | `AssessmentApi.js` duplicates `candidateApi.js`; an older multi-language `CandidateWorkspace` exists but is not routed | Consolidate the API client and remove or finish the legacy workspace |
| Marketing claims | Landing copy mentions AI-driven evaluation; the shipped system is deterministic | Either implement AI-assisted review or remove the claim |

---

## 13. Repository map

```
AlgoArena/
├── gateway-service/        Spring Cloud Gateway · JWT · RBAC · rate limit · IP jail · metrics
├── auth-service/           Registration · OTP · login · roles · admin creation
├── arena-service/          Problems · daily challenge · matchmaking · Elo · STOMP
├── assessment-service/     Private vault · assessments · candidates · scoring · leaderboards
├── analytics-service/      Health · traffic · SSE dashboard · audit logs
├── code-sandbox/           Warm pool · compile/run · isolation · result classification
├── O-one-outpost-web/      React SPA · engineer/company/admin/candidate journeys
├── docs/                   Project guide · deployment · EC2 setup
├── .github/workflows/      Matrix build → Docker Hub → EC2 deploy
├── docker-compose.yml      Local orchestration
├── docker-compose.prod.yml Production topology with memory limits and health checks
└── README.md               Original project overview
```

### Where to look first

| If you want to see... | Open |
|---|---|
| Edge security and RBAC | `gateway-service/.../filter/AuthenticationFilter.java` |
| Abuse controls | `gateway-service/.../filter/IpBlockingFilter.java`, `RateLimiterConfig.java` |
| Untrusted execution | `code-sandbox/.../service/ContainerPool.java`, `CodeExecutionService.java` |
| Matchmaking and Elo | `arena-service/.../services/MatchService.java` |
| Server-side test harness | `arena-service/.../services/CodeGeneratorService.java` |
| Assessment scoring | `assessment-service/.../service/AssessmentExecutionService.java` |
| Eligibility and locking | `assessment-service/.../service/AssessmentService.java`, `AssessmentRegistrationService.java` |
| OTP and cooldown | `auth-service/.../services/OtpService.java` |
| Operational telemetry | `analytics-service/.../service/SystemHealthService.java` |
| Candidate proctoring | `O-one-outpost-web/src/components/TournamentWorkspace.jsx` |

---

## 14. Glossary

| Term | Meaning |
|---|---|
| **Elo** | A relative skill rating; a win against a stronger opponent moves the rating more than a win against a weaker one |
| **Warm pool** | Pre-created containers kept ready so execution does not pay container startup cost |
| **Test harness** | Generated `Main` class that parses inputs, calls the candidate's method, compares outputs, and reports results |
| **Hidden test** | A test case never sent to the browser; used to prevent hard-coding |
| **Leaderboard blackout** | Hiding rankings until an assessment ends to preserve fairness |
| **Internal token** | Shared secret header that proves a request came through the gateway |
| **IP jail** | Temporary block applied to an IP after sustained abusive traffic |
| **SSE** | Server-Sent Events; a one-way stream from server to browser used by the admin console |
| **STOMP** | A messaging protocol used over WebSocket for topic subscriptions |
| **Test code** | Short invite code a company shares with candidates |
| **Time penalty** | Tie-breaker that increases with time spent before scoring improvements |

---

## 15. Closing note

AlgoArena is a useful portfolio project because it combines the three things that make software hard in the real world:

1. **A real user problem** — hiring signal is noisy and expensive to produce.
2. **A real technical risk** — running untrusted code safely, quickly, and cheaply.
3. **A real business tension** — a two-sided marketplace must keep engineers engaged while giving companies a trustworthy, private workflow.

The implementation does not pretend to be a hyperscale platform. It makes deliberate choices for its scale, documents the trade-offs, and leaves a clear path for the next stage. That is the difference between a demo and an engineered product.
