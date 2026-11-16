# code-sandbox on a Docker VPS

The sandbox is the only service that **cannot** run on Botkeep. It needs the
host Docker daemon to create warm executor containers, so it runs on a small
dedicated VPS behind Caddy.

---

## 1. Requirements

- Ubuntu 22.04 / 24.04 (or any Docker-capable Linux)
- 2 GB RAM minimum (`256 MB` JVM + `3 × 128 MB` warm containers + OS)
- 20 GB disk (Java images and build cache)
- Docker Engine + Compose plugin
- A DNS record: `sandbox.yourdomain.com` → VPS public IP
- Ports `22`, `80`, `443` open (do **not** open `8085`)
- Outbound HTTPS for image pulls and analytics audit calls

---

## 2. Install Docker

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker "$USER"
# log out and back in, then verify:
docker --version && docker compose version
```

---

## 3. Configure

```bash
git clone <your-repo> /opt/algoarena
cd /opt/algoarena/deploy/vps/code-sandbox

cp /opt/algoarena/code-sandbox/.env.example .env
nano .env
```

Fill in:

```dotenv
INTERNAL_SECRET=<same value as every Botkeep service>
ANALYTICS_SERVICE_URL=https://analytics.yourdomain.com
SERVER_PORT=8085
JAVA_OPTS=-Xms64m -Xmx256m -Xss256k -XX:+UseG1GC -XX:MaxGCPauseMillis=100
LOG_LEVEL_SANDBOX=INFO

# Caddy + Docker socket group
SANDBOX_DOMAIN=sandbox.yourdomain.com
DOCKER_GID=999
```

Find the host Docker group ID with:

```bash
getent group docker | cut -d: -f3
```

Set `DOCKER_GID` to that value. The current Dockerfile runs as root, so the
group is a safety net for future non-root images.

Lock down the file:

```bash
chmod 600 .env
```

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

The service pulls `eclipse-temurin:17-alpine` on first start:

```bash
docker pull eclipse-temurin:17-alpine
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

# Health endpoint is public (no token) -> 200 {"status":"UP"}
curl -i https://sandbox.yourdomain.com/actuator/health

# Any other path without the token -> 403
curl -i https://sandbox.yourdomain.com/

# Token-protected API is exercised by the execute call below.

# Execute Java
curl -i -X POST https://sandbox.yourdomain.com/api/sandbox/execute \
  -H "Content-Type: application/json" \
  -H "X-Internal-Token: $INTERNAL_SECRET" \
  -d '{"code":"public class Main { public static void main(String[] a){ System.out.println(\"ok\"); } }","timeLimitMs":5000,"memoryLimitKb":65536}'
```

Expected: the response body contains `ok`.

Check the warm pool:

```bash
docker ps --filter "name=warm_container_"
# 3 containers should be running
```

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

The sandbox destroys the executor container after every request and asynchronously
creates a fresh replacement. A brief gap is normal; the next request waits up to
10 seconds for a warm container.

---

## 7. Security

- This VPS is a **trust boundary**. The sandbox container can control the host
  Docker daemon, which is root-equivalent. Use a dedicated VPS with no other
  production secrets or databases.
- Only Caddy publishes ports 80/443. The sandbox is `expose`d on the internal
  Compose network, never published.
- Every request must carry `X-Internal-Token`. Rotate it together with all
  Botkeep services. The only exception is `/actuator/health` and its probe
  sub-paths, which are public so host-level probes work without the secret.
- Executor containers keep `--network none`, read-only root filesystems,
  `--memory=128m`, `--memory-swap=128m`, `--cpus=0.5`, and small `tmpfs` mounts.
  Do not relax those flags without a security review.
- Keep the host patched and Docker updated. A compromised sandbox container is a
  compromised host.
- If the sandbox is unavailable, arena/assessment submissions fail closed with a
  `SYSTEM_ERROR` message; the rest of the platform stays online.

---

## 8. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `No containers available` | Docker socket or pool refill failure | `docker ps`, `docker logs`, check `docker pull eclipse-temurin:17-alpine` |
| 502 from Caddy | Sandbox container unhealthy | `docker compose ps`, `docker compose logs code-sandbox` |
| Caddy cannot get a certificate | DNS not pointing at the VPS, or Cloudflare proxy | Set an A record to the VPS IP; use DNS-only mode |
| 403 even with a token | `INTERNAL_SECRET` mismatch | Copy the same value from the Botkeep services |
| OOM / host stalls | Too many warm containers or a 1 GB VPS | Use ≥2 GB RAM; lower the pool size in `ContainerPool.java` if needed |
| Disk full | Old Java images / build cache | `docker image prune -af`, `docker builder prune -f` |
