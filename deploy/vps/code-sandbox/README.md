# code-sandbox on a Docker VPS (secondary backend)

> **Secondary backend.** The primary deployment uses
> [OnlineCompiler.io](../pterodactyl/onlinecompiler.md) and needs no Docker
> host. This guide is for running the warm container pool on your own hardware:
> set `JUDGE_EXECUTION_BACKEND=docker` (the Compose file already does).

The sandbox creates hardened, disposable Java executor containers. It talks to
the Docker **Engine API** directly through `docker-java`; the application image
contains **no Docker CLI** and never runs submitted Java on the host.

This guide covers the simple self-hosted layout: the sandbox container and the
Docker daemon are on the same VPS and the sandbox mounts the host socket. For a
Pterodactyl app container talking to a separate Docker host over SSH or mutual
TLS, use [`deploy/pterodactyl/code-sandbox.md`](../../pterodactyl/code-sandbox.md).

---

## 1. Requirements

- Ubuntu 22.04 / 24.04 (or any Docker-capable Linux)
- **4 GB RAM** for the default pool (`JUDGE_POOL_SIZE=3`,
  `MAX_MEMORY_LIMIT_MB=512`). A 2 GB VPS works with
  `JUDGE_POOL_SIZE=2` and `MAX_MEMORY_LIMIT_MB=256`.
- 20 GB disk for the Java image and build cache
- Docker Engine + Compose plugin
- A DNS record: `sandbox.yourdomain.com` → VPS public IP
- Ports `22`, `80`, `443` open (do **not** open `8085` or `2375`)
- Outbound HTTPS for image pulls and analytics audit calls

Host sizing:

```text
RAM ≈ 1 GB (OS + dockerd + page cache)
    + pool size × (max memory limit + memory overhead)
```

The executor containers run on this host, not in the sandbox container. The
sandbox JVM itself needs only ~384 MB.

---

## 2. Install Docker

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] \
https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
```

The Docker CLI is installed **on the host for operations only**. The sandbox
image does not include it and does not invoke it for the local socket.

Pre-pull the executor image (optional):

```bash
sudo docker pull eclipse-temurin:17-alpine
```

---

## 3. Configure

```bash
git clone <your-repo> /opt/algoarena
cd /opt/algoarena/deploy/vps/code-sandbox

cp /opt/algoarena/code-sandbox/.env.example .env
nano .env
```

The shared template is OnlineCompiler-first and deliberately contains no active
Docker settings. This Compose stack selects the Docker backend and supplies
`JUDGE_DOCKER_HOST=unix:///var/run/docker.sock` by default, so the local-socket
layout works without extra Docker keys. Add the endpoint explicitly when Docker
runs on another host.

Minimum production values:

```dotenv
INTERNAL_SECRET=<same value as every other service>
ANALYTICS_SERVICE_URL=https://analytics.yourdomain.com
SERVER_PORT=8085
JAVA_OPTS=-Xms64m -Xmx256m -Xss256k -XX:+UseG1GC -XX:MaxGCPauseMillis=100 -XX:+ExitOnOutOfMemoryError
LOG_LEVEL_SANDBOX=INFO

# Local daemon through the mounted socket.
JUDGE_DOCKER_HOST=unix:///var/run/docker.sock

# Pool and limits. Raise JUDGE_POOL_SIZE only with host RAM.
JUDGE_POOL_SIZE=3
DEFAULT_TIME_LIMIT_MS=2000
MAX_TIME_LIMIT_MS=10000
DEFAULT_MEMORY_LIMIT_MB=256
MAX_MEMORY_LIMIT_MB=512

# Caddy + Docker socket group
SANDBOX_DOMAIN=sandbox.yourdomain.com
DOCKER_GID=999
```

The judge no longer falls back to a local Docker socket on its own. Selecting
`JUDGE_EXECUTION_BACKEND=docker` requires `JUDGE_DOCKER_HOST` or
`JUDGE_DOCKER_SSH_HOST`; the Compose file provides the former for this layout.
These Docker settings must be real process environment variables (Compose
`environment:` or the host environment); the default `application.yaml` no
longer maps them from a locally imported `.env` file.

Find the host Docker group ID:

```bash
getent group docker | cut -d: -f3
```

Set `DOCKER_GID` to that value. The image runs as the unprivileged `appuser`;
Compose adds the Docker group as a supplementary group so the process can open
`/var/run/docker.sock`. The socket is root-equivalent on the host: keep this VPS
dedicated.

Lock the file down:

```bash
chmod 600 .env
```

### Remote daemon instead of the socket

If Docker runs elsewhere, remove the `/var/run/docker.sock` volume and
`group_add` from `docker-compose.yml`, then use either:

```dotenv
# SSH (preferred)
JUDGE_DOCKER_HOST=ssh://judge@docker-host.example.com:22
JUDGE_DOCKER_SSH_KEY_PATH=/home/container/.ssh/id_ed25519
JUDGE_DOCKER_SSH_KNOWN_HOSTS=/home/container/.ssh/known_hosts

# or mutual TLS
JUDGE_DOCKER_HOST=tcp://docker-host.example.com:2376
JUDGE_DOCKER_TLS_VERIFY=1
JUDGE_DOCKER_CERT_PATH=/app/certs
```

Plain remote `tcp://host:2375` is rejected at startup. See the Pterodactyl
guide for key/certificate generation and host hardening.

---

## 4. Start

### Option A — build the image on the VPS (recommended while iterating)

```bash
# In docker-compose.yml, uncomment the build: block and comment the image: line.
docker compose up -d --build
```

### Option B — pull a prebuilt image

```bash
# Build and push from a machine with the repo:
docker build -t <your-dockerhub-user>/o-one-outpost-sandbox:latest ./code-sandbox
docker push <your-dockerhub-user>/o-one-outpost-sandbox:latest

# On the VPS:
SANDBOX_IMAGE=<your-dockerhub-user>/o-one-outpost-sandbox:latest docker compose up -d
```

Caddy obtains a Let's Encrypt certificate automatically. If you use Cloudflare,
set the `sandbox` record to **DNS only** (grey cloud) so the HTTP-01 challenge
can complete, or switch Caddy to a DNS challenge.

---

## 5. Verify

```bash
docker compose ps
docker compose logs -f code-sandbox
docker compose logs -f caddy
```

Health endpoint (public, no token):

```bash
curl -i https://sandbox.yourdomain.com/actuator/health
# 200 {"status":"UP"}
```

Operator status (token required):

```bash
curl -sS https://sandbox.yourdomain.com/api/sandbox/status \
  -H "X-Internal-Token: $INTERNAL_SECRET"
```

Expected: `"dockerConnected": true`, `"imageReady": true`, and
`"available"` equal to `"desiredSize"`.

Execute Java:

```bash
curl -i -X POST https://sandbox.yourdomain.com/api/sandbox/execute \
  -H "Content-Type: application/json" \
  -H "X-Internal-Token: $INTERNAL_SECRET" \
  -d '{"code":"public class Main { public static void main(String[] a){ System.out.println(\"ALL_TESTS_PASSED\"); } }","timeLimitMs":2000,"memoryLimitKb":262144}'
```

Expected: HTTP 200, `X-Judge-Verdict: AC`, body contains `ALL_TESTS_PASSED`.

Check the warm pool and confirm no executor leaks:

```bash
docker ps --filter "label=algoarena.sandbox.managed=true"
docker ps -a --filter "label=algoarena.sandbox.managed=true"
```

There should be exactly `JUDGE_POOL_SIZE` running containers, and no stopped
ones left behind. Each request destroys its container and the pool creates a
replacement in the background.

Check host resources:

```bash
free -h
docker stats --no-stream
```

---

## 6. Operations

```bash
# Logs
docker compose logs -f --tail=200

# Restart after changing .env
docker compose up -d --force-recreate

# Update the image
docker compose pull && docker compose up -d

# Clean old executor images/build cache
docker image prune -f
docker builder prune -f

# Disk usage
df -h
docker system df
```

The service destroys the executor after every request. A short gap is normal;
the next request waits up to `JUDGE_POOL_BORROW_TIMEOUT_MS` (10 seconds) for a warm
replacement.

Graceful shutdown (`docker compose stop`, SIGTERM) runs the pool's `@PreDestroy`
cleanup and removes every managed container. After a hard kill, the next startup
sweeps stale containers belonging to the same host/port owner label.

---

## 7. Security

- This VPS is a **trust boundary**. The sandbox container can control the host
  Docker daemon, which is root-equivalent. Use a dedicated VPS with no other
  production secrets or databases.
- Only Caddy publishes ports 80/443. The sandbox is `expose`d on the internal
  Compose network, never published.
- Every request must carry `X-Internal-Token`. Rotate it together with all other
  services. The only exception is `/actuator/health`, which is public so probes
  work without the secret.
- Executor containers are created with:
  - `--network none` and private IPC
  - read-only root filesystem
  - size-limited tmpfs at `/app` and `/tmp`
  - memory + swap cap, CPU quota, PID limit
  - non-root user `10001:10001`
  - all capabilities dropped, `no-new-privileges`
  - `init` as PID 1 to reap orphaned processes
- Submitted source is written to the tmpfs over `docker exec` stdin; no bind
  mounts, no host directories, no Docker CLI.
- The container wall-clock deadline is authoritative. A timed-out run kills the
  container; Java thread interruption is never relied on.
- Keep the host patched and Docker updated. A compromised sandbox container is a
  compromised host.
- If the sandbox is unavailable, arena/assessment submissions fail closed with a
  `SYSTEM_ERROR` message; the rest of the platform stays online.

---

## 8. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Startup fails with `Unable to connect to Docker Engine` | Socket permissions, wrong `JUDGE_DOCKER_HOST`, missing daemon | `docker version` on the host; check `DOCKER_GID` and that `/var/run/docker.sock` exists |
| `permission denied ... docker.sock` | Container user is not in the Docker group | Set `DOCKER_GID` to `getent group docker \| cut -d: -f3`, recreate the container |
| `No execution container is available` | Pool refill failure, host RAM/disk pressure | `docker ps -a`, logs; lower `JUDGE_POOL_SIZE` or `MAX_MEMORY_LIMIT_MB` |
| 502 from Caddy | Sandbox container unhealthy | `docker compose ps`, `docker compose logs code-sandbox` |
| Caddy cannot get a certificate | DNS not pointing at the VPS, or Cloudflare proxy | Set an A record to the VPS IP; use DNS-only mode |
| 403 even with a token | `INTERNAL_SECRET` mismatch | Copy the same value from the other services |
| `container rootfs is marked read-only` in logs | Old archive-copy code path | Update to the current image; source is streamed over exec stdin |
| OOM / host stalls | Too many warm containers or too little RAM | Use ≥4 GB, or lower pool size and memory limits |
| Disk full | Old Java images / build cache | `docker image prune -af`, `docker builder prune -f` |
| Every submission returns `TIME_LIMIT_EXCEEDED` | Host is much slower than the reference environment | Calibrate explicitly with `JUDGE_TIME_LIMIT_MULTIPLIER`; never auto-tune per request |
