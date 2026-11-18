# O(1) Outpost — Independent Deployment on Botkeep

This directory is the source of truth for deploying each service independently.
It is written for **Botkeep** (managed Java hosting). The **code-sandbox** runs
on any Java host (Pterodactyl or a VPS) and sends submissions to
**OnlineCompiler.io over HTTPS**; it does **not** need a Docker host. The
original warm Docker `ContainerPool` remains as an optional secondary backend.

---

## 1. Final topology

```text
                              Internet
                                 │
              ┌──────────────────┴──────────────────┐
              │                                     │
     app.yourdomain.com                    api.yourdomain.com
     Vercel / React SPA                    Botkeep: gateway-service
              │                                     │
              │                    ┌────────────────┼────────────────┐
              │                    │                │                │
              │             auth.yourdomain   arena.yourdomain   assessment.yourdomain
              │             Botkeep: auth    Botkeep: arena     Botkeep: assessment
              │                                     │                │
              │                                     └───────┬────────┘
              │                                             │
              │                                    sandbox.yourdomain.com
              │                                    Pterodactyl/VPS: code-sandbox
              │                                    (outbound HTTPS → OnlineCompiler.io)
              │                                             │
              └───────────────────────────► analytics.yourdomain.com
                                            Botkeep: analytics
```

All inter-service traffic uses HTTPS. The gateway injects the shared
`X-Internal-Token` header; every downstream service rejects requests without it.

---

## 2. Workload allocation (5 Botkeep slots)

| Botkeep slot | Service | Public domain | Runtime | Heap |
|---|---|---|---|---|
| 1 | `gateway-service` | `api.yourdomain.com` | Java 21 | 384 MB |
| 2 | `auth-service` | `auth.yourdomain.com` | Java 21 | 320 MB |
| 3 | `arena-service` | `arena.yourdomain.com` | Java 21 | 384 MB |
| 4 | `assessment-service` | `assessment.yourdomain.com` | Java 21 | 320 MB |
| 5 | `analytics-service` | `analytics.yourdomain.com` | Java 21 | 256 MB |

Outside Botkeep:

| Component | Host | Public domain | Why |
|---|---|---|---|
| `code-sandbox` | Pterodactyl or any Java 21 host | `sandbox.yourdomain.com` | Sends submissions to the OnlineCompiler.io HTTPS API; no Docker host needed |
| Frontend | Vercel (or Botkeep static) | `app.yourdomain.com` | Vite SPA; Vercel is already configured in the repo |
| PostgreSQL | Neon | managed | Auth + Analytics |
| MongoDB | Atlas | managed | Arena + Assessment |
| Redis | Upstash | managed | Gateway rate limiting + Auth OTP |
| OnlineCompiler.io | Provider SaaS | `api.onlinecompiler.io` | Remote multi-language execution backend (outbound only) |

> If you want the frontend on Botkeep too, it consumes a **sixth** workload
> slot. With exactly five slots, keep the frontend on Vercel or add a plan.

---

## 3. Secrets matrix

| Variable | Gateway | Auth | Arena | Assessment | Analytics | Sandbox |
|---|:---:|:---:|:---:|:---:|:---:|:---:|
| `JWT_SECRET` | ✅ | ✅ | | | | |
| `INTERNAL_SECRET` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | | ✅ | | | ✅ | |
| `*_MONGO_URI` | | | ✅ | ✅ | | |
| `REDIS_*` | ✅ | ✅ | | | | |
| `MAIL_*` | | ✅ | | | | |
| `ANALYTICS_SERVICE_URL` | ✅ | ✅ | ✅ | ✅ | | ✅ |
| `GATEWAY_URL` | | | | | ✅ | |
| `SANDBOX_URL` | | | ✅ | ✅ | ✅ (health) | |
| `ONLINECOMPILER_API_KEY` | | | | | | ✅ |
| `CORS_ALLOWED_ORIGINS` | ✅ | | | | | |
| `ARENA_ALLOWED_ORIGINS` | | | ✅ | | | |

**Hard rules**

1. `JWT_SECRET` must be **identical** on gateway and auth. Use a base64 value.
2. `INTERNAL_SECRET` must be **identical** on all six services.
3. Every service needs its own environment block. Do not share one global `.env`.
4. Never commit a filled `.env`. Use the Botkeep Environment panel.
5. `SERVER_PORT` is assigned by Botkeep. Do not hardcode it in a start command.

Generate secrets:

```bash
openssl rand -base64 48     # JWT_SECRET
openssl rand -hex 48        # INTERNAL_SECRET
```

---

## 4. Step 1 — Build the JARs

Each service has its own Maven wrapper. Build from the repository root:

### Windows

```powershell
.\deploy\scripts\build-jars.ps1
```

### Linux / macOS / CI

```bash
chmod +x deploy/scripts/build-jars.sh
./deploy/scripts/build-jars.sh
```

Output:

```text
deploy/artifacts/
  gateway-service/app.jar
  auth-service/app.jar
  arena-service/app.jar
  assessment-service/app.jar
  analytics-service/app.jar
  code-sandbox/app.jar
```

Optionally create one ZIP per Botkeep service:

```powershell
.\deploy\scripts\package-botkeep.ps1
```

Output: `deploy/artifacts/botkeep-<service>.zip` containing `app.jar`,
`START.txt`, and the service's `env.example`.

> The GitHub Actions workflow `.github/workflows/build-jars.yml` builds the
> same artifacts whenever a service changes and on manual dispatch. Download the
> artifact for each service and upload it to the matching Botkeep workload.

---

## 5. Step 2 — Create the Botkeep workloads

For each of the five backend services:

1. Create a **Java** workload. Select **Java 21**.
2. Allocate **2 GB RAM** (your 5 × 2 GB plan).
3. Upload the service ZIP (or the built `app.jar`).
4. Set the **start command** from [`botkeep/start-commands.md`](./botkeep/start-commands.md).
5. Add the environment variables from the service's `.env.example`:
   - `gateway-service/.env.example`
   - `auth-service/.env.example`
   - `arena-service/.env.example`
   - `assessment-service/.env.example`
   - `analytics-service/.env.example`
6. Start the workload and watch **Console** until the Spring Boot startup banner
   and `Started ...Application` line appear.
7. Open **Domains**, attach the custom subdomain, and complete DNS verification.

Important Botkeep behaviour:

- The platform passes `SERVER_PORT`; Spring Boot binds to it automatically.
- The platform provides one process per workload. Do not try to start two JVMs.
- The platform proxies HTTPS. Enable `forward-headers-strategy` (already set in
  the gateway) so client IPs and CORS work.
- Startup is not instant. A Spring Boot service can take 20–60 seconds.

### Deployment order

1. `auth-service`
2. `arena-service`
3. `assessment-service`
4. `analytics-service`
5. `code-sandbox` (Pterodactyl/VPS + OnlineCompiler key)
6. `gateway-service` (last, so all downstream URLs already exist)
7. Frontend

Each service only needs the URLs of services it calls, but deploying the
downstream services first makes the gateway's startup checks cleaner.

---

## 6. Step 3 — Deploy the code-sandbox

The default backend sends each submission to the OnlineCompiler.io HTTPS API, so
there is **no Docker host to deploy**. Run the JAR on Pterodactyl (or any Java 21
host) and give it an API key:

- **Primary guide:** [`pterodactyl/onlinecompiler.md`](./pterodactyl/onlinecompiler.md)

Pterodactyl settings in one line: Java 21, one port, 512 MB RAM, the standard
JAR, and `JUDGE_EXECUTION_BACKEND=onlinecompiler` plus `ONLINECOMPILER_API_KEY`
in the Startup variables. The server needs outbound HTTPS to
`api.onlinecompiler.io:443` and nothing inbound from the provider.

DNS: point `sandbox.yourdomain.com` at the host through Caddy, NGINX, or a
Cloudflare Tunnel. Only 80/443 need to be public.

Verify:

```bash
curl -sS https://sandbox.yourdomain.com/api/sandbox/status \
  -H "X-Internal-Token: $INTERNAL_SECRET"
# -> backend: "onlinecompiler", onlineCompiler.configured: true

curl -H "X-Internal-Token: $INTERNAL_SECRET" https://sandbox.yourdomain.com/
# -> 200
curl https://sandbox.yourdomain.com/
# -> 403 (direct access without the internal token is rejected)
```

### Optional secondary backend — warm Docker pool

The original Docker `ContainerPool` code is preserved but inactive by default.
It is used only when you explicitly set `JUDGE_EXECUTION_BACKEND=docker` and
provide a reachable Docker Engine. Selecting `docker` without an explicit
`JUDGE_DOCKER_HOST` or `JUDGE_DOCKER_SSH_HOST` fails startup instead of falling
back to a local socket. It still requires a Docker host, so it is not part of the
default deployment.

- **Self-hosted Docker VPS** (sandbox container + local Docker socket):
  [`vps/code-sandbox/README.md`](./vps/code-sandbox/README.md)
- **Pterodactyl app container + remote Docker host** (SSH or mutual TLS):
  [`pterodactyl/code-sandbox.md`](./pterodactyl/code-sandbox.md)

The Pterodactyl Docker layout is the fallback when you want to keep execution on
your own hardware: the app image has no Docker CLI and never mounts
`docker.sock`; it talks to the Engine API over SSH or mTLS.

Short version for a Docker VPS:

```bash
# On the VPS
git clone <your-repo> /opt/algoarena
cd /opt/algoarena/deploy/vps/code-sandbox
cp ../../../code-sandbox/.env.example .env
# edit .env: INTERNAL_SECRET, ANALYTICS_SERVICE_URL, SANDBOX_DOMAIN, DOCKER_GID
# The Compose file selects JUDGE_EXECUTION_BACKEND=docker and supplies
# JUDGE_DOCKER_HOST=unix:///var/run/docker.sock for the local socket.
docker compose up -d --build
```

---

## 7. Step 4 — Deploy the frontend

Vercel is already configured in `O-one-outpost-web/vercel.json`.

1. Import the repo in Vercel.
2. Set **Root Directory** to `O-one-outpost-web`.
3. Build command: `npm run build`; output: `dist`.
4. Add environment variables (Production + Preview):
   - `VITE_API_GATEWAY_URL=https://api.yourdomain.com`
   - `VITE_ARENA_WS_URL=wss://api.yourdomain.com`
5. Deploy, then add `app.yourdomain.com` as a custom domain.
6. Add the same frontend origin to:
   - gateway `CORS_ALLOWED_ORIGINS`
   - arena `ARENA_ALLOWED_ORIGINS`

> Vite bakes environment variables at build time. After changing them you must
> redeploy, not just restart.

If you host the frontend on Botkeep static hosting instead, use the same two
variables before `npm run build` and copy `dist/` to the static site.

---

## 8. Step 5 — Verify end to end

### Gateway and public route

```bash
curl -i https://api.yourdomain.com/actuator/health
# 200 {"status":"UP"}

curl -i https://api.yourdomain.com/api/problems/daily
# 200 (public daily question) or 404 if none is scheduled
```

### Per-service health probes

Every service now exposes `/actuator/health` without the internal token, so a
per-host load balancer or platform probe can use it directly. Only the health
endpoint is exempt; all API routes still require `X-Internal-Token`.

```bash
for svc in auth arena assessment analytics; do
  printf "%s: " "$svc"
  curl -s -o /dev/null -w "%{http_code}\n" "https://$svc.yourdomain.com/actuator/health"
done
# auth: 200
# arena: 200
# assessment: 200
# analytics: 200

curl -s https://sandbox.yourdomain.com/actuator/health
# {"status":"UP"}
```

`/actuator/health` returns `503` when a required dependency (Postgres, MongoDB,
Redis) is down. The services also expose the standard probe groups:

```bash
curl -s https://arena.yourdomain.com/actuator/health/liveness   # app is alive
curl -s https://arena.yourdomain.com/actuator/health/readiness  # dependencies are ready
```

Use `liveness` for a restart probe and `readiness` for a load-balancer probe.
Only `/actuator/health` and its sub-paths are exempt; `/actuator` and every
other actuator path still return `403` without the internal token.

### Direct service access is blocked

```bash
curl -i https://auth.yourdomain.com/
# 403 {"message":"Direct access forbidden. Use the gateway."}

curl -i https://analytics.yourdomain.com/
# 403
```

Only `/actuator/health` is exempt from the internal-token check. Every other
path, including `/`, is still rejected.

### Auth flow

```bash
curl -i -X POST https://api.yourdomain.com/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","password":"strong-password","role":"USER"}'
```

Check email for the OTP, then:

```bash
curl -i -X POST https://api.yourdomain.com/api/auth/verify \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","otp":"123456"}'

curl -i -X POST https://api.yourdomain.com/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","password":"strong-password","role":"USER"}'
```

### WebSocket

Open the browser DevTools → Network. After login, the arena/lobby page should
open SockJS requests to:

```text
https://api.yourdomain.com/ws-arena/info
wss://api.yourdomain.com/ws-arena/...
```

If those requests go to `arena.yourdomain.com` instead, your frontend build used
a stale `VITE_ARENA_WS_URL`. Rebuild and redeploy.

### Sandbox

```bash
curl -i -X POST https://sandbox.yourdomain.com/api/sandbox/execute \
  -H "Content-Type: application/json" \
  -H "X-Internal-Token: $INTERNAL_SECRET" \
  -d '{"code":"public class Main { public static void main(String[] a){ System.out.println(\"ok\"); } }","timeLimitMs":5000,"memoryLimitKb":65536}'
```

The response should contain `ok`. Without `X-Internal-Token` it must return 403.

### Analytics dashboard

Log in as an admin, open `/overseer`, and confirm the health tiles are green and
the request/WebSocket counters move. The analytics SSE stream is proxied through
the gateway at `/api/analytics/dashboard/stream`.

---

## 9. Environment file rules

Each service's `.env.example` is the single source of truth for its variables.

- For **local Docker Compose**, copy to `<service>/.env`.
- For **Botkeep**, paste the key/value pairs into the workload Environment panel.
- For the **sandbox host**, copy `code-sandbox/.env.example` for the shared
  values, then paste the OnlineCompiler values into the Pterodactyl Startup
  panel or add the Docker endpoint from the VPS guide. The shared template never
  enables or contacts Docker by itself.
- For **Vercel**, paste the two `VITE_*` values into the project settings.

Never put production credentials in a GitHub repository, a ZIP uploaded to
support, or a chat message. Rotate any credential that has been exposed.

---

## 10. Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Gateway exits immediately (`mapping values are not allowed here`) | YAML indentation / wrong prefix | Use the current `application.yaml`; the prefix must be `spring.cloud.gateway.server.webflux` |
| Gateway starts on 8080 but Botkeep shows 502 | `SERVER_PORT` not honoured | Do not hardcode `server.port` in the start command; let Spring read `SERVER_PORT` |
| Every downstream request is 403 | `INTERNAL_SECRET` mismatch | Use the same value on all six services. `/actuator/health` is exempt, so a 200 there with 403 elsewhere confirms the filter is working as intended |
| Auth returns 401 after login | `JWT_SECRET` differs between gateway and auth | Copy the same base64 value to both |
| CORS error in browser | Frontend origin not allowed | Add it to `CORS_ALLOWED_ORIGINS` and restart gateway |
| WebSocket fails | Wrong `ARENA_WS_URL` or Botkeep proxy issue | Use `wss://arena.yourdomain.com` in the gateway; use `wss://api.yourdomain.com` in the frontend |
| Redis connection fails | TLS mismatch | Upstash: `REDIS_SSL=true`; plain Redis: `false` |
| Mongo/Postgres timeout | Provider IP allowlist | Allow Botkeep egress / `0.0.0.0/0`, or add the VPS IP |
| Sandbox `No containers available` | Docker backend selected, but Docker connection, image pull, or host memory failed | Check `/api/sandbox/status`; for the default OnlineCompiler backend the pool stays inactive on purpose |
| Startup fails with `ONLINECOMPILER_API_KEY is required` | OnlineCompiler backend active without a key | Paste the key into the Pterodactyl Startup panel / `.env`; `JUDGE_API_ONLY_MODE=true` disables execution |
| Sandbox returns `IE` with `OnlineCompiler rejected the API key` | Wrong/revoked key | Regenerate the key and restart |
| Sandbox returns `IE` with HTTP 429 | Provider rate/capacity limit (4 concurrent, 60/min on Free) | Lower `ONLINECOMPILER_MAX_CONCURRENCY`, retry later, or upgrade |
| Java submissions are all `MLE` | Provider memory includes JVM runtime files | Keep `ONLINECOMPILER_MEMORY_HEADROOM_MB=256`; keep problem memory ≤ 256 MB on the 512 MB plan |
| Analytics tiles are all DOWN | Service URLs are localhost or missing `/` base URL | Use the public HTTPS domains |
| SSE stream disconnects immediately | Proxy buffering | Verify Botkeep forwards `text/event-stream` without buffering |
| JVM killed / restarts | Heap too large for the workload | Lower `-Xmx` or increase the workload RAM |

---

## 11. Security notes

- The gateway is the only service intended for public API traffic. The other
  Botkeep domains are publicly routable but protected by `X-Internal-Token`.
  `/actuator/health` is the single intentional exception so per-host probes can
  run without the secret; it exposes status only (`show-details: never`), never
  dependency details. Treat the internal secret as a production credential.
- The sandbox is a trust boundary. On the default OnlineCompiler backend,
  submitted source leaves your infrastructure and is processed by the provider;
  confirm this matches your data-handling policy and keep
  `ONLINECOMPILER_API_KEY` only in the host environment. If you switch to the
  secondary Docker backend, that host's Docker socket is root-equivalent: use a
  dedicated VPS with no other secrets, keep ports 80/443 open, and never expose
  8085 or 2375. Prefer SSH/mTLS to a dedicated Docker host when the sandbox runs
  on Pterodactyl.
- Executor containers keep `--network none`, read-only root filesystems, memory,
  CPU and PID caps, a non-root user, dropped capabilities, and
  `no-new-privileges`. Do not relax these settings.
- Use HTTPS for every inter-service URL. Do not use `http://` for Botkeep
  domains.
- Keep `.env` files out of git. The repository `.gitignore` already excludes
  `.env` and `.env.prod`; only `.env.example` templates are tracked.
- Take Botkeep backups and independent database backups. The platform is a beta
  and does not provide an uptime SLA.
