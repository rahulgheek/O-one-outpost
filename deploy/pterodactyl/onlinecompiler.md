# code-sandbox on Pterodactyl (OnlineCompiler.io backend)

This is the **primary** deployment guide for `code-sandbox`. The service runs as
a plain Pterodactyl Java server and sends each submission to the
**OnlineCompiler.io HTTPS API**. It needs **no Docker host, no Docker CLI, and no
Docker socket**.

The warm Docker `ContainerPool` is still in the codebase as a secondary backend.
It is inactive unless you explicitly set `JUDGE_EXECUTION_BACKEND=docker`. For
that path use [`code-sandbox.md`](./code-sandbox.md).

```text
Pterodactyl node                              Internet
┌───────────────────────────┐                ┌──────────────────────────────┐
│ code-sandbox container    │   HTTPS 443    │ api.onlinecompiler.io        │
│  - Spring Boot API :PORT  │───────────────▶│  - compiles/runs 8 languages │
│  - judge harness generator│  Authorization │  - runs in a fresh sandbox   │
│  - NO Docker CLI/socket   │  <API_KEY>     │  - no network in the run     │
└───────────────────────────┘                └──────────────────────────────┘
            ▲
            │ HTTPS + X-Internal-Token
   arena-service / assessment-service
```

---

## 1. How execution works

1. `arena-service` / `assessment-service` POST a generated harness (named
   `Main.{java,py,cpp,c,...}` per language) to `/api/sandbox/execute` or
   `/api/sandbox/execute/result`, together with the selected `language` id.
2. `code-sandbox` resolves the compiler id for that language, then clamps the
   time/memory limits.
3. It sends `POST https://api.onlinecompiler.io/api/run-code-sync/` with the
   source, the compiler id and `Authorization: <API_KEY>`.
4. The provider compiles and runs the whole harness, then returns
   `output`, `error`, `status`, `exit_code`, `time`, `total`, and `memory`.
5. The sandbox maps the provider result to the normal verdicts: `AC`, `WA`,
   `TLE`, `MLE`, `RE`, `CE`, `IE`.
6. The existing line protocol (`PROGRESS|`, `ASSESSMENT_RESULT|`,
   `ALL_TESTS_PASSED`, `WRONG_ANSWER`, `RUNTIME_ERROR`) is parsed exactly as it
   was for Docker, so the judge verdicts do not change.

Provider mapping used by the sandbox:

| Provider signal | Verdict |
|---|---|
| `exit_code = 0` and harness passes | `AC` |
| `exit_code = 0` and harness reports failures | `WA` |
| `exit_code = 124` (provider timeout) | `TLE` |
| `exit_code = 137` (memory kill) | `MLE` |
| Java compiler diagnostics (`Main.java:N: error: ...`) | `CE` |
| `exit_code = 153` (output flood) or `71` (process limit) | `RE` |
| Any other non-zero exit | `RE` |
| HTTP 401/403/429/5xx, timeout, network failure | `IE` |

CPU time is not exposed by the API, so `cpuTimeMs` is `-1`. `time` becomes the
execution time and `total` becomes the wall time. Peak `memory` is reported in
KB.

> Java is executed in single-file source mode. The provider's `time` includes
> the `javac` compile phase, and `total` includes it as well. `time_limit`
> enforcement gives Java a start-up allowance, but the reported execution time
> still contains compilation. On the Docker backend compilation is excluded from
> the contestant's time. Budget problem limits accordingly, and verify the
> compile overhead with your key.

---

## 2. Provider limits and plan sizing

Free / Starter limits (verify on your provider dashboard):

| Limit | Value | Sandbox handling |
|---|---|---|
| Simultaneous runs | 4 | `ONLINECOMPILER_MAX_CONCURRENCY=4` |
| Runs per minute | 60 | Local concurrency cap and `429` retries |
| Runs per month | 100,000 | Provider dashboard |
| Max run time | 30 s | `MAX_TIME_LIMIT_MS` must stay below 30,000 |
| CPU | 2 cores | Provider-managed |
| Processes per run | 16 | Provider stops the run with exit 71 |
| Memory | 512 MB | `ONLINECOMPILER_MAX_MEMORY_MB=512` |
| Code size | 100 KB | `ONLINECOMPILER_MAX_CODE_BYTES=100000` |
| Standard input | 5 MB | `ONLINECOMPILER_MAX_INPUT_BYTES=5242880` |
| Output per stream | 64 KB | Provider truncates; `truncated` is surfaced |

Source code above `ONLINECOMPILER_MAX_CODE_BYTES` is rejected with HTTP 400
during request validation, before anything is sent to the provider. When the
online backend is active the effective limit is the lower of
`JUDGE_MAX_CODE_BYTES` and `ONLINECOMPILER_MAX_CODE_BYTES`. Oversized standard
input fails closed with `SYSTEM_ERROR` rather than being silently truncated.

Memory accounting matters for Java. The provider's `memory` figure includes
mapped JVM/runtime files, so a trivial Java program already reports roughly
230 MB. The sandbox therefore sends:

```text
provider memory_limit = problem memory + ONLINECOMPILER_MEMORY_HEADROOM_MB
```

clamped to `ONLINECOMPILER_MAX_MEMORY_MB`. With the defaults (256 MB problem,
256 MB headroom, 512 MB cap) a 256 MB problem sends 512 MB.

> The provider cap is a ceiling, not a reservation. Java sizes its heap from the
> container's visible memory, so the effective heap is well below 512 MB and the
> problem's memory limit is enforced only approximately. If valid solutions MLE,
> raise `ONLINECOMPILER_MEMORY_HEADROOM_MB` so even smaller problems send the
> 512 MB cap; do not raise `ONLINECOMPILER_MAX_MEMORY_MB` above your plan.
> Because the cap is 512 MB on Free/Starter, keep problem memory limits at or
> below 256 MB for reliable Java results. Headroom is explicit configuration
> only; it is never auto-tuned from host load.

What the judge does **not** control on this backend:

- No cgroup CPU quota, PID limit, or read-only root filesystem of our own; the
  provider applies its sandbox policy.
- No automatic container replacement; each request is an independent remote run.
- No CPU-time metric.
- No on-premises execution: submissions leave your infrastructure and are
  processed by the provider. Check your data-handling requirements before
  sending participant code.

Only 429 (rate/capacity) and 503 (queue unavailable) are retried, and only while
nothing has run. A request timeout is never retried because the remote program
may already have run and been billed.

---

## 3. Create the Pterodactyl server

### 3.1 Server settings

1. Create a server using a **Java 21** egg.
2. Allocate one port. This is the HTTP port the gateway/arena/assessment use.
3. Set memory to **512 MB** (the JVM uses `-Xmx320m`; remote runs do not consume
   Pterodactyl memory).
4. Upload `deploy/artifacts/code-sandbox/app.jar` to `/home/container/app.jar`.
   Build it with `deploy/scripts/build-jars.ps1 -Services code-sandbox`.
5. Keep at least 1 GB of server disk for the JAR and logs.

### 3.2 Startup command

```text
java -Xms64m -Xmx320m -Xss256k -XX:+UseG1GC -XX:MaxGCPauseMillis=100 -XX:+ExitOnOutOfMemoryError -Dserver.port={{SERVER_PORT}} -jar app.jar
```

Notes:

- `{{SERVER_PORT}}` is Pterodactyl's allocated port; do not hard-code 8085.
- `-Xmx320m` assumes a 512 MB server. If you allocate 1 GB, use `-Xmx640m`.
- Do not start two JVMs in one server.
- No Docker image is pulled anywhere for this backend.

### 3.3 Environment variables

Pterodactyl only passes variables declared on the egg. As an administrator, add
each variable below to the egg (`Admin → Nests → <nest> → <egg> → Variables`),
then set the values on the server's **Startup** tab.

Minimum production set:

```dotenv
INTERNAL_SECRET=<shared secret, identical to gateway/arena/assessment>
ANALYTICS_SERVICE_URL=https://analytics.yourdomain.com
LOG_LEVEL_SANDBOX=INFO

JUDGE_EXECUTION_BACKEND=onlinecompiler
ONLINECOMPILER_API_KEY=<paste your key here>
ONLINECOMPILER_BASE_URL=https://api.onlinecompiler.io
ONLINECOMPILER_COMPILER=openjdk-25

# Optional per-language overrides. Defaults are listed below; pin any language
# independently when the provider publishes a newer runtime.
# ONLINECOMPILER_COMPILER_JAVA=openjdk-25
# ONLINECOMPILER_COMPILER_PYTHON=python-3.14
# ONLINECOMPILER_COMPILER_CPP=g++-15
# ONLINECOMPILER_COMPILER_C=gcc-15
# ONLINECOMPILER_COMPILER_JAVASCRIPT=nodejs-24
# ONLINECOMPILER_COMPILER_TYPESCRIPT=typescript-deno
# ONLINECOMPILER_COMPILER_GO=go-1.26
# ONLINECOMPILER_COMPILER_RUST=rust-1.93

ONLINECOMPILER_MAX_CONCURRENCY=4
ONLINECOMPILER_MEMORY_HEADROOM_MB=256
ONLINECOMPILER_MAX_MEMORY_MB=512

DEFAULT_TIME_LIMIT_MS=2000
MAX_TIME_LIMIT_MS=10000
DEFAULT_MEMORY_LIMIT_MB=256
MAX_MEMORY_LIMIT_MB=512
MIN_MEMORY_LIMIT_MB=64

JUDGE_TIME_LIMIT_MULTIPLIER=1.0
JUDGE_ENABLED=true
JUDGE_API_ONLY_MODE=false
```

Full template: [`code-sandbox/.env.example`](../../code-sandbox/.env.example).

The sandbox accepts the eight judge language ids below. `ONLINECOMPILER_COMPILER`
is still the Java fallback; set `ONLINECOMPILER_COMPILER_<LANGUAGE>` to pin a
different provider runtime for one language.

| Language | Id | Default compiler |
|---|---|---|
| Java | `java` | `openjdk-25` |
| Python | `python` | `python-3.14` |
| C++ | `cpp` | `g++-15` |
| C | `c` | `gcc-15` |
| JavaScript | `javascript` | `nodejs-24` |
| TypeScript | `typescript` | `typescript-deno` |
| Go | `go` | `go-1.26` |
| Rust | `rust` | `rust-1.93` |

Arena/tournament problems and assessment problems store one method signature
and generate an equivalent harness for every language. C and Rust support
scalar types plus `int[]` parameters; other array parameter types are rejected
when the problem is created.

No Docker variable is required. The Docker `ContainerPool` remains in the
codebase as a secondary backend, but the default `application.yaml` does not
configure or initialize it. Selecting `JUDGE_EXECUTION_BACKEND=docker` without
an explicit `JUDGE_DOCKER_HOST` or `JUDGE_DOCKER_SSH_HOST` now fails startup
instead of silently trying a local Docker socket.

### 3.4 API key handling

- Get the key from <https://api.onlinecompiler.io/compilers/api-keys/>.
- Paste it into the Pterodactyl **Startup** variables or into
  `code-sandbox/.env` for local runs. Never commit it.
- The key is a server-side secret. It is sent raw in the `Authorization` header
  (no `Bearer` prefix, as the provider documents), and is never logged, echoed
  in a verdict, or returned by `/api/sandbox/status`.
- `/api/sandbox/status` reports only `configured: true/false`.
- If the key is missing while `JUDGE_EXECUTION_BACKEND=onlinecompiler`, startup
  fails with:

  ```text
  ONLINECOMPILER_API_KEY is required when JUDGE_EXECUTION_BACKEND=onlinecompiler.
  Set JUDGE_API_ONLY_MODE=true only for an explicitly non-executing API.
  ```

- Rotate the key in the provider dashboard, then update the Pterodactyl
  variable and restart. Rotating invalidates the old key immediately.

---

## 4. HTTPS and the public API

Pterodactyl exposes the allocated port on the node. The sandbox API must be
reachable from Botkeep at an HTTPS URL, so put one of these in front of it:

- **Cloudflare Tunnel** on the node (`cloudflared tunnel --url http://node:PORT`)
  mapped to `sandbox.yourdomain.com`; or
- **Caddy/NGINX** on the node with a Let's Encrypt certificate.

Then point arena/assessment at it:

```dotenv
SANDBOX_URL=https://sandbox.yourdomain.com/api/sandbox/execute
```

Every request except `/actuator/health` requires `X-Internal-Token`. Never expose
the port without the token check.

The sandbox makes **outbound** HTTPS to `api.onlinecompiler.io`. If the node
uses an egress allowlist, allow `api.onlinecompiler.io:443`. No inbound port is
needed for the provider.

---

## 5. Verify the deployment

### 5.1 Status endpoint

```bash
curl -sS https://sandbox.yourdomain.com/api/sandbox/status \
  -H "X-Internal-Token: $INTERNAL_SECRET"
```

Expected (values vary):

```json
{
  "executionEnabled": true,
  "backend": "onlinecompiler",
  "dockerBackendActive": false,
  "apiOnlyMode": false,
  "requireDocker": true,
  "defaultTimeLimitMs": 2000,
  "maxTimeLimitMs": 10000,
  "defaultMemoryLimitMb": 256,
  "maxMemoryLimitMb": 512,
  "pool": {
    "desiredSize": 3,
    "available": 0,
    "dockerConnected": false
  },
  "onlineCompiler": {
    "configured": true,
    "baseUrl": "https://api.onlinecompiler.io",
    "compiler": "openjdk-25",
    "maxConcurrency": 4,
    "maxCodeBytes": 100000,
    "responseTimeoutMs": 45000,
    "memoryHeadroomMb": 256,
    "maxMemoryMb": 512
  }
}
```

`pool` stays at zero on purpose: the Docker backend is inactive.

### 5.2 Execute Java (AC)

Save as `ac.json`:

```json
{
  "code": "public class Main {\n    public static void main(String[] args) {\n        System.out.println(\"ALL_TESTS_PASSED\");\n    }\n}",
  "timeLimitMs": 2000,
  "memoryLimitKb": 262144
}
```

```bash
curl -i -X POST https://sandbox.yourdomain.com/api/sandbox/execute \
  -H "Content-Type: application/json" \
  -H "X-Internal-Token: $INTERNAL_SECRET" \
  --data-binary @ac.json
```

Expected: HTTP 200, `X-Judge-Verdict: AC`, body contains `ALL_TESTS_PASSED`.

Structured result:

```bash
curl -sS -X POST https://sandbox.yourdomain.com/api/sandbox/execute/result \
  -H "Content-Type: application/json" \
  -H "X-Internal-Token: $INTERNAL_SECRET" \
  --data-binary @ac.json
```

Check `verdict`, `timeMs`, `wallTimeMs`, `memoryUsedKb`, `exitCode`, `stdout`,
and `stderr`. `cpuTimeMs` is `-1` by design.

### 5.3 Standard input

`input` is optional and forwarded to the provider:

```json
{
  "code": "import java.util.*;\npublic class Main {\n  public static void main(String[] a) {\n    Scanner s = new Scanner(System.in);\n    String name = s.hasNextLine() ? s.nextLine() : \"nobody\";\n    System.out.println(\"Hello, \" + name + \"!\");\n  }\n}",
  "timeLimitMs": 2000,
  "memoryLimitKb": 262144,
  "input": "World"
}
```

Expected stdout contains `Hello, World!`.

### 5.4 Other verdicts

- **CE**: remove a semicolon → `verdict: "CE"`, compiler diagnostics in
  `stderr`.
- **WA**: print `WRONG_ANSWER|Test Case 1 Failed.` → `verdict: "WA"`.
- **RE**: `throw new RuntimeException("boom");` → `verdict: "RE"`.
- **TLE**: infinite loop with `timeLimitMs: 1000` → `verdict: "TLE"`.
- **MLE**: allocate far beyond the provider cap → `verdict: "MLE"` (exit 137).
- **Bad key**: temporarily set `ONLINECOMPILER_API_KEY=invalid` and restart →
  requests return `verdict: "IE"` with `OnlineCompiler rejected the API key`.

Run these against the live provider once, before trusting the judge for real
submissions.

---

## 6. Operations

```bash
# Pterodactyl: Console tab shows Spring Boot logs and one line per verdict:
#   OnlineCompiler result: verdict=AC time=250ms total=400ms memory=240000KB exit=0 status=success
```

- The API is synchronous: one HTTP request per submission. Expect roughly the
  provider queue time plus compile/run time, usually 1–5 seconds for Java.
- A local semaphore caps in-flight runs at `ONLINECOMPILER_MAX_CONCURRENCY`.
  Requests wait up to `ONLINECOMPILER_QUEUE_TIMEOUT_MS` for a slot, then fail
  with an `IE` asking the caller to retry.
- `429` and `503` are retried with jittered exponential backoff, honoring
  `Retry-After` when it is a number of seconds.
- No executor containers are created, so there is nothing to clean up after a
  crash.
- Logs contain verdicts, timings, exit codes, and bounded provider errors. They
  never contain the API key or the submitted source.
- Keep JVM heap at `-Xmx320m` for a 512 MB server. The sandbox holds one request
  body per in-flight submission (up to 100 KB code + 5 MB input), which fits
  comfortably.

---

## 7. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Startup fails with `ONLINECOMPILER_API_KEY is required` | Key missing on the onlinecompiler backend | Paste the key into the egg variable / `.env`; use `JUDGE_API_ONLY_MODE=true` only for a non-executing API |
| Startup fails with `Invalid boolean value '"false"'` | The panel or startup command passed the quotes as part of the value | Set `JUDGE_API_ONLY_MODE=false` (or delete it — the default is `false`) and remove quotes from the egg startup command. `code-sandbox` strips one matching pair of quotes, but unquoted values are the correct configuration |
| `OnlineCompiler rejected the API key (HTTP 401/403)` | Wrong, expired, or revoked key | Regenerate the key and restart |
| `OnlineCompiler rate/capacity limit reached (HTTP 429)` | More than 4 concurrent runs, more than 60/min, or provider congestion | Lower `ONLINECOMPILER_MAX_CONCURRENCY`, retry later, or upgrade the plan |
| `concurrency limit reached locally` | Local semaphore queue expired | Reduce submission concurrency or raise `ONLINECOMPILER_QUEUE_TIMEOUT_MS` |
| `OnlineCompiler did not respond within 45000ms` | Provider slow or network stall | Check <https://status.onlinecompiler.io>; the remote run may still have been billed. Do not blindly resubmit in a loop |
| `Could not reach OnlineCompiler` | DNS, egress firewall, or proxy | Allow `api.onlinecompiler.io:443`; verify with `curl -I https://api.onlinecompiler.io/` |
| Every result is `MLE` | Headroom too small for the JVM | Keep `ONLINECOMPILER_MEMORY_HEADROOM_MB=256`; lower problem memory; check the reported `memoryUsedKb` |
| Results are slower than Docker | Provider queue and shared sandbox | Keep `MAX_TIME_LIMIT_MS` below 30,000; do not "fix" this with a large multiplier |
| `CE` for code that worked locally | Provider compiler differs from the local toolchain, or the generated harness was edited | Check the compiler for the submitted language (`ONLINECOMPILER_COMPILER_<LANGUAGE>`); keep the generated harness intact and do not rename the `Solution`/method identifiers |
| `truncated: true` | Output exceeded 64 KB per stream | Expected; the harness protocol lines are small, so treat truncation as a program bug (output flood) |
| Startup fails with an HTTPS error | `ONLINECOMPILER_BASE_URL` uses plain HTTP | Use `https://api.onlinecompiler.io`; plain HTTP is accepted only for loopback tests with `ONLINECOMPILER_ALLOW_INSECURE_HTTP=true` |
| Docker pool shows `available: 0` | Docker backend is inactive by design | Set `JUDGE_EXECUTION_BACKEND=docker` plus an explicit `JUDGE_DOCKER_HOST` or `JUDGE_DOCKER_SSH_HOST`, then follow [`code-sandbox.md`](./code-sandbox.md) |

---

## 8. Security checklist

- [ ] `ONLINECOMPILER_API_KEY` is set only in the Pterodactyl Startup panel or
      an untracked `.env`; it is not in git, logs, screenshots, or chat.
- [ ] `/api/sandbox/status` shows `configured: true` but no key material.
- [ ] `ONLINECOMPILER_BASE_URL` is HTTPS and `ONLINECOMPILER_ALLOW_INSECURE_HTTP`
      is `false` in production.
- [ ] `INTERNAL_SECRET` is identical across services and rotated on exposure.
- [ ] Public access goes through HTTPS with `X-Internal-Token`; `/actuator/health`
      is the only unauthenticated path.
- [ ] `MAX_TIME_LIMIT_MS` is below the provider's 30 s ceiling.
- [ ] `ONLINECOMPILER_MAX_MEMORY_MB` matches your plan (512 on Free/Starter).
- [ ] Participant code leaves your infrastructure; this is approved by your
      data-handling policy.
- [ ] The provider key has the narrowest scope the dashboard offers and is
      rotated on schedule.
- [ ] The Docker backend, if enabled later, follows the security checklist in
      [`code-sandbox.md`](./code-sandbox.md).
