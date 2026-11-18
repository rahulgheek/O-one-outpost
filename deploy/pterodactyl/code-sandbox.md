# code-sandbox on Pterodactyl (secondary Docker Engine backend)

> **This is the secondary backend.** The primary deployment uses
> [OnlineCompiler.io](./onlinecompiler.md) and needs no Docker host. The warm
> container pool described here is preserved but inactive by default. To use it,
> set `JUDGE_EXECUTION_BACKEND=docker` explicitly.

This guide deploys `code-sandbox` as a Pterodactyl **Java 21** server while the
executor containers run on a **separate Docker host**. The application never
installs Docker, never mounts `docker.sock`, and never runs submitted Java in the
Pterodactyl container.

```text
Pterodactyl node                         Dedicated Docker host
┌───────────────────────────┐            ┌──────────────────────────────┐
│ code-sandbox container    │  SSH 22    │ dockerd                      │
│  - Spring Boot API :PORT  │───────────▶│  - eclipse-temurin:17-alpine │
│  - docker-java client     │  or mTLS   │  - warm executor containers  │
│  - NO docker CLI/socket   │   2376     │    (--network none, RO root) │
└───────────────────────────┘            └──────────────────────────────┘
            ▲
            │ HTTPS + X-Internal-Token
   arena-service / assessment-service
```

> The Docker API is root-equivalent on the Docker host. Use a dedicated host
> with no databases, secrets, or other workloads. A compromised judge key is a
> compromised host.

---

## 1. Docker host requirements

- Ubuntu 22.04 / 24.04, **4 GB RAM** for the default configuration
  (`JUDGE_POOL_SIZE=3`, `MAX_MEMORY_LIMIT_MB=512`).
- 20 GB free disk for the Java image and build cache.
- Docker Engine 24+ with the Compose plugin (Compose is optional).
- OpenSSH server, or Docker's TLS port `2376` restricted to the Pterodactyl
  node IP. **Never expose `2375`.**
- No inbound access to executor containers; they use `--network none`.

Sizing formula:

```text
Docker host RAM ≈ 1 GB (OS + dockerd + page cache)
                + pool size × (max memory limit + memory overhead)
```

Examples: `3 × (512 + 64) MB + 1 GB ≈ 2.7 GB` → use 4 GB. For a 2 GB host set
`JUDGE_POOL_SIZE=2` and `MAX_MEMORY_LIMIT_MB=256`.

---

## 2. Install Docker on the Docker host (not in Pterodactyl)

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl openssh-server
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] \
https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
sudo systemctl enable --now docker
```

Pre-pull the judge image (optional; the sandbox also pulls it when
`JUDGE_IMAGE_PULL_POLICY=if-missing`):

```bash
sudo docker pull eclipse-temurin:17-alpine
```

### Verify the daemon is not exposed insecurely

```bash
sudo ss -lntp | grep -E '2375|2376' || echo "no TCP Docker listener"
```

If `2375` appears anywhere, stop and remove that listener before continuing.

---

## 3. Connection method A — SSH (recommended)

The sandbox runs `docker system dial-stdio` on the Docker host through SSH, so
the **Docker CLI must exist on the Docker host** (it does after step 2). The
Pterodactyl container needs no Docker CLI.

### 3.1 Create a dedicated account

```bash
sudo adduser --disabled-password --gecos "" judge
sudo usermod -aG docker judge
sudo -u judge docker version
```

`judge` is now root-equivalent through the Docker socket. Do not reuse this
account for anything else.

### 3.2 Create and install the key

On your workstation:

```bash
ssh-keygen -t ed25519 -a 100 -f judge_key -C "algoarena-code-sandbox"
```

Install the public key on the Docker host:

```bash
sudo install -d -m 700 -o judge -g judge /home/judge/.ssh
sudo install -m 600 -o judge -g judge judge_key.pub /home/judge/.ssh/authorized_keys
```

Restrict the key to the tunnel command (optional but recommended). Replace the
`authorized_keys` content with:

```text
command="docker system dial-stdio",no-agent-forwarding,no-port-forwarding,no-pty,no-user-rc,no-X11-forwarding ssh-ed25519 AAAA... algoarena-code-sandbox
```

> If you add a forced `command=`, the sandbox must not need a shell on that
> account. The judge only uses `docker system dial-stdio`.

### 3.3 Put the files in the Pterodactyl server

Upload to the server's file manager (or SFTP):

```text
/home/container/.ssh/id_ed25519     # private key (never the .pub)
/home/container/.ssh/known_hosts
```

Generate `known_hosts` from a trusted machine:

```bash
ssh-keyscan -H -p 22 docker-host.example.com > known_hosts
```

Set permissions over SFTP:

```bash
chmod 700 /home/container/.ssh
chmod 600 /home/container/.ssh/id_ed25519
chmod 644 /home/container/.ssh/known_hosts
```

### 3.4 Environment variables

Add these as Pterodactyl egg variables (see section 5.3):

```dotenv
JUDGE_DOCKER_SSH_HOST=docker-host.example.com
JUDGE_DOCKER_SSH_USER=judge
JUDGE_DOCKER_SSH_PORT=22
JUDGE_DOCKER_SSH_KEY_PATH=/home/container/.ssh/id_ed25519
JUDGE_DOCKER_SSH_KNOWN_HOSTS=/home/container/.ssh/known_hosts
JUDGE_DOCKER_SSH_STRICT_HOST_KEY_CHECKING=true
```

`JUDGE_DOCKER_SSH_HOST` takes precedence over `JUDGE_DOCKER_HOST`. Do not set
`JUDGE_DOCKER_HOST` to a `tcp://` address when using SSH.

For a quick non-production test you may set
`JUDGE_DOCKER_SSH_STRICT_HOST_KEY_CHECKING=false`, but never in production.

---

## 4. Connection method B — mutual TLS

Use this when SSH egress is blocked but outbound TCP works.

### 4.1 Create a CA and certificates

On the Docker host:

```bash
mkdir -p ~/docker-certs && cd ~/docker-certs

# CA
openssl genrsa -out ca-key.pem 4096
openssl req -new -x509 -days 3650 -key ca-key.pem -sha256 -out ca.pem \
  -subj "/CN=algoarena-docker-ca"

# Server certificate (add every DNS name/IP clients use)
openssl genrsa -out server-key.pem 4096
openssl req -new -key server-key.pem -out server.csr -subj "/CN=docker-host.example.com"
cat > server-ext.cnf <<'EOF'
subjectAltName = DNS:docker-host.example.com,IP:203.0.113.10
extendedKeyUsage = serverAuth
EOF
openssl x509 -req -days 3650 -sha256 -in server.csr \
  -CA ca.pem -CAkey ca-key.pem -CAcreateserial -out server-cert.pem -extfile server-ext.cnf

# Client certificate (this one goes to Pterodactyl)
openssl genrsa -out key.pem 4096
openssl req -new -key key.pem -out client.csr -subj "/CN=algoarena-sandbox"
cat > client-ext.cnf <<'EOF'
extendedKeyUsage = clientAuth
EOF
openssl x509 -req -days 3650 -sha256 -in client.csr \
  -CA ca.pem -CAkey ca-key.pem -CAcreateserial -out cert.pem -extfile client-ext.cnf
```

### 4.2 Configure dockerd with TLS only

Create `/etc/systemd/system/docker.service.d/override.conf`:

```ini
[Service]
ExecStart=
ExecStart=/usr/sbin/dockerd -H fd:// -H tcp://0.0.0.0:2376 \
  --tlsverify \
  --tlscacert=/etc/docker/certs/ca.pem \
  --tlscert=/etc/docker/certs/server-cert.pem \
  --tlskey=/etc/docker/certs/server-key.pem
```

```bash
sudo install -d -m 700 /etc/docker/certs
sudo cp ca.pem server-cert.pem server-key.pem /etc/docker/certs/
sudo chmod 600 /etc/docker/certs/server-key.pem
sudo systemctl daemon-reload
sudo systemctl restart docker
sudo ss -lntp | grep 2376
```

Restrict the port to the Pterodactyl node:

```bash
sudo ufw allow from <PTERODACTYL_NODE_IP> to any port 2376 proto tcp
sudo ufw deny 2376/tcp
```

Verify from the Pterodactyl node (or a machine with the client certs):

```bash
docker --tlsverify \
  --tlscacert=ca.pem --tlscert=cert.pem --tlskey=key.pem \
  -H=tcp://docker-host.example.com:2376 version
```

### 4.3 Upload client certificates

```text
/home/container/certs/ca.pem
/home/container/certs/cert.pem
/home/container/certs/key.pem
```

```bash
chmod 600 /home/container/certs/key.pem
```

Environment:

```dotenv
JUDGE_DOCKER_HOST=tcp://docker-host.example.com:2376
JUDGE_DOCKER_TLS_VERIFY=1
JUDGE_DOCKER_CERT_PATH=/home/container/certs
```

Plain remote `tcp://...:2375` is rejected at startup by design. The only
exception is `JUDGE_DOCKER_ALLOW_INSECURE_TCP=true` with a loopback address, intended
for local development.

---

## 5. Create the Pterodactyl server

### 5.1 Server settings

1. Create a server using a **Java 21** egg (for example “Java 21” from the
   community egg index).
2. Allocate one port. This is the HTTP port the gateway/arena/assessment use.
3. Set memory to **512 MB** (the JVM uses `-Xmx320m`; executor containers do
   not consume Pterodactyl memory).
4. Upload `deploy/artifacts/code-sandbox/app.jar` to `/home/container/app.jar`.
   Build it with `deploy/scripts/build-jars.ps1 -Services code-sandbox`.
5. Keep the server disk at 2 GB or more (the JAR is ~40 MB plus logs).

### 5.2 Startup command

Use this exact command in the egg's startup field:

```text
java -Xms64m -Xmx320m -Xss256k -XX:+UseG1GC -XX:MaxGCPauseMillis=100 -XX:+ExitOnOutOfMemoryError -Dserver.port={{SERVER_PORT}} -jar app.jar
```

Notes:

- `{{SERVER_PORT}}` is Pterodactyl's allocated port; do not hard-code 8085.
- `-Xmx320m` assumes a 512 MB server. If you allocate 1 GB, use `-Xmx640m`.
- Do not start two JVMs in one server.
- The image `eclipse-temurin:17-alpine` is pulled by the **Docker host**, not by
  the Pterodactyl container.

### 5.3 Environment variables

Pterodactyl only passes variables declared on the egg. As an administrator, add
each variable below to the egg (`Admin → Nests → <nest> → <egg> → Variables`)
with the **environment variable** name exactly as shown. Then set the values on
the server's **Startup** tab.

Minimum production set:

```dotenv
INTERNAL_SECRET=<shared secret, identical to gateway/arena/assessment>
ANALYTICS_SERVICE_URL=https://analytics.yourdomain.com
LOG_LEVEL_SANDBOX=INFO

# This guide is the secondary backend; select it explicitly.
JUDGE_EXECUTION_BACKEND=docker

JUDGE_IMAGE=eclipse-temurin:17-alpine
JUDGE_IMAGE_PULL_POLICY=if-missing
JUDGE_POOL_SIZE=3

DEFAULT_TIME_LIMIT_MS=2000
MAX_TIME_LIMIT_MS=10000
DEFAULT_MEMORY_LIMIT_MB=256
MAX_MEMORY_LIMIT_MB=512
MIN_MEMORY_LIMIT_MB=64

JUDGE_TIME_LIMIT_MULTIPLIER=1.0
JUDGE_EXECUTION_WALL_CLOCK_GRACE_MS=500
JUDGE_EXECUTION_COMPILE_TIMEOUT_MS=10000
JUDGE_EXECUTION_SOURCE_WRITE_TIMEOUT_MS=10000
JUDGE_EXECUTION_CPU_LIMIT=0.5
JUDGE_EXECUTION_PIDS_LIMIT=64
JUDGE_POOL_CONTAINER_USER=10001:10001
JUDGE_POOL_WORK_DIR=/app
JUDGE_EXECUTION_WORK_DIR_SIZE_MB=64
JUDGE_EXECUTION_TMP_DIR_SIZE_MB=16
JUDGE_EXECUTION_SHM_SIZE_MB=16

JUDGE_POOL_WARMUP_ON_STARTUP=true
JUDGE_POOL_WARMUP_BLOCKING=true
JUDGE_POOL_STARTUP_TIMEOUT_MS=10000
JUDGE_POOL_BORROW_TIMEOUT_MS=10000
JUDGE_REQUIRE_DOCKER=true
JUDGE_API_ONLY_MODE=false
JUDGE_ENABLED=true
```

Plus the SSH or mTLS variables from sections 3.4 / 4.3.

The Docker backend requires either `JUDGE_DOCKER_HOST` or
`JUDGE_DOCKER_SSH_HOST`. Selecting `docker` without one aborts startup instead
of silently falling back to a local socket. Set these as real process
environment variables in the Pterodactyl Startup panel; the default
`application.yaml` no longer maps Docker settings from a locally imported
`.env` file.

`JUDGE_REQUIRE_DOCKER=true` makes the server fail fast at startup with a clear
message if the daemon is unreachable. Pterodactyl will show the crash/restart.
Set it to `false` only if you want the API to start degraded and reconnect in
the background. `JUDGE_API_ONLY_MODE=true` is only for UI deployments with no
code execution and does not contact Docker.

### 5.4 HTTPS and the public API

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
the port without the token check; the token is the only application-level gate.

---

## 6. Verify the deployment

### 6.1 Status endpoint

```bash
curl -sS https://sandbox.yourdomain.com/api/sandbox/status \
  -H "X-Internal-Token: $INTERNAL_SECRET"
```

Expected (values vary):

```json
{
  "executionEnabled": true,
  "backend": "docker",
  "dockerBackendActive": true,
  "apiOnlyMode": false,
  "requireDocker": true,
  "image": "eclipse-temurin:17-alpine",
  "pool": {
    "desiredSize": 3,
    "available": 3,
    "total": 3,
    "dockerConnected": true,
    "imageReady": true
  }
}
```

If `available` is below `desiredSize`, wait for the refill or read the log;
the pool retries with exponential backoff.

### 6.2 Execute Java

Save this as `ac.json`:

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

Check that the container was replaced and no executor leaked:

```bash
# On the Docker host
docker ps --filter "label=algoarena.sandbox.managed=true"
docker ps -a --filter "label=algoarena.sandbox.managed=true"
```

---

## 7. Operations

```bash
# Pterodactyl: Console tab shows Spring Boot logs and judge verdicts.
# Docker host:
docker ps --filter "label=algoarena.sandbox.managed=true"
docker ps -a --filter "label=algoarena.sandbox.managed=true"
docker stats --no-stream
docker image prune -f
```

- The pool replaces a container after every request; a short refill window is
  normal. Requests wait up to `JUDGE_POOL_BORROW_TIMEOUT_MS` for a replacement.
- On graceful shutdown (SIGTERM from Pterodactyl), `@PreDestroy` removes every
  managed container. After a hard kill, the next startup removes stale
  containers owned by the same host/port label.
- Rotate the SSH key or client certificate by uploading the new files and
  restarting the server.

---

## 8. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Startup fails with `Unable to connect to Docker Engine` | Wrong host/port, firewall, bad key/cert | Test SSH or `docker --tlsverify ... version` from the node; check `JUDGE_DOCKER_SSH_*` or `JUDGE_DOCKER_CERT_PATH` |
| `JUDGE_DOCKER_SSH_KNOWN_HOSTS is not set` | Strict host-key checking is on | Upload `known_hosts`; do not disable strict checking in production |
| `Refusing an unauthenticated Docker daemon` | `JUDGE_DOCKER_HOST=tcp://host:2375` | Use SSH or mTLS; never open 2375 |
| `No execution container is available` | Pool cannot create containers | Check Docker host memory/disk, image pull, and `docker ps -a` for errors |
| `docker: command not found` on the Docker host | SSH tunnel needs the CLI on the daemon host | Install `docker-ce-cli` there (not in Pterodactyl) |
| Pool is stuck at `available: 0` | Pull failure or health check failure | `docker pull eclipse-temurin:17-alpine`; read Pterodactyl Console |
| Executor killed with exit 137 | Memory limit exceeded | Increase `MAX_MEMORY_LIMIT_MB`/request limit or reduce the submission's allocation |
| TLE is too strict | Reference environment is slower | Calibrate explicitly with `JUDGE_TIME_LIMIT_MULTIPLIER`; never auto-tune |
| Server restarts in a loop | `JUDGE_REQUIRE_DOCKER=true` and Docker is down | Fix Docker first, or set `false` for degraded startup |

---

## 9. Security checklist

- [ ] No Docker CLI or socket inside the Pterodactyl container.
- [ ] `2375` is closed; only SSH or mTLS `2376` is reachable.
- [ ] Docker host is dedicated and has no production databases/secrets.
- [ ] SSH key is unique, `known_hosts` is pinned, strict checking is on.
- [ ] Docker host firewall allows the connection only from the Pterodactyl node.
- [ ] `INTERNAL_SECRET` is identical across services and rotated on exposure.
- [ ] Executors use `--network none`, read-only rootfs, tmpfs `/app` and `/tmp`,
      non-root user, dropped capabilities, `no-new-privileges`, PID/memory/CPU
      limits. Do not relax these flags.
- [ ] `JUDGE_EXECUTION_MAX_OUTPUT_BYTES`, `JUDGE_MAX_CODE_BYTES`, and the pool size are
      sized for the node's memory and disk.
