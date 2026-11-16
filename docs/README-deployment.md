# O One Outpost — Production Deployment (legacy Docker/EC2)

> **Legacy:** the current production topology is documented in
> [`deploy/README.md`](../deploy/README.md) (Botkeep + sandbox VPS). This file
> describes the original single-host Docker/EC2 deployment and is kept for
> reference.

## Architecture Overview

```
Internet → [EC2 Security Group: 80/443 only]
              ↓
         ┌─────────────┐
         │   Gateway    │ :8080 (published as :80)
         │  (Spring CG) │
         └──────┬───────┘
     ┌──────┬───┴───┬──────────┬──────────┐
     ↓      ↓       ↓          ↓          ↓
  ┌──────┐┌──────┐┌──────┐ ┌──────────┐┌──────────┐
  │ Auth ││Arena ││Assess││ Analytics ││ Sandbox  │
  │ :8081││:8084 ││:8083 ││   :8082   ││  :8085   │
  └──────┘└──────┘└──────┘ └──────────┘└────┬─────┘
     Internal Docker Network only              ↓
                                     docker.sock
                                         ↓
                                  ┌─────────────┐
                                  │ Ephemeral    │
                                  │ Containers   │
                                  │ (128m/0.5cpu)│
                                  └─────────────┘
```

## Internal-Only Trust Model

### How it works

1. **Network isolation** (primary): Only the gateway has a published port (`80:8080`). All other services use `expose:` only — they live on an internal Docker bridge network and are unreachable from outside the EC2 host.

2. **Shared secret header** (belt-and-suspenders): The gateway injects an `X-Internal-Token` header into every forwarded request. Each downstream service validates this header against a shared secret (`INTERNAL_SECRET` env var). Requests missing or mismatching the header get a `403 Forbidden`. The only exception is `/actuator/health` (and its probe sub-paths), which is deliberately public so per-host health checks work without the secret.

3. **Dev-friendly**: When `INTERNAL_SECRET` is not set (empty string), both the gateway injection and downstream validation are no-ops. Local development works without any extra config.

### Files involved

| Component | File | Purpose |
|-----------|------|---------|
| Gateway | `InternalTokenInjectionFilter.java` | GlobalFilter — injects `X-Internal-Token` header |
| Auth | `InternalAuthFilter.java` | Servlet Filter — validates the header |
| Arena | `InternalAuthFilter.java` | Same |
| Assessment | `InternalAuthFilter.java` | Same |
| Analytics | `InternalAuthFilter.java` | Same |
| Sandbox | `InternalAuthFilter.java` | Same |

## Code-Sandbox Isolation

### Approach: Docker-in-Docker via socket mount

The sandbox service runs inside a Docker container but mounts the host's `/var/run/docker.sock`. This allows it to launch ephemeral execution containers on the host's Docker daemon.

**Trade-off accepted**: Socket mount grants the sandbox container effective root access to the host Docker. This is acceptable because:
- The sandbox has no published port — only reachable via the gateway
- Requests are authenticated by the shared internal secret
- Ephemeral containers are heavily constrained

### Ephemeral container constraints

| Setting | Value | Purpose |
|---------|-------|---------|
| `--memory` | 128m | Hard memory cap |
| `--memory-swap` | 128m | No swap (equals memory = swap disabled) |
| `--cpus` | 0.5 | Half a core max |
| `--network` | none | No network access |
| `--read-only` | yes | Read-only root filesystem |
| `--tmpfs /app` | 32m | Writable tmpfs for code compilation |
| `--tmpfs /tmp` | 16m | Writable tmpfs for temp files |
| Pool size | 3 | Pre-warmed containers (fits in 4GB budget) |
| Timeout | Per-request `timeLimitMs` | Process killed on timeout |
| Cleanup | Auto-remove after each use | Never reused (prevents leaking) |

### Container lifecycle

1. On startup: pool pre-warms 3 containers (`tail -f /dev/null`)
2. On request: borrow a container, pipe code via stdin, compile + run
3. On completion: container is killed (`docker rm -f`), a fresh replacement is spawned async
4. On shutdown: all warm containers are cleaned up

## CI/CD Pipeline

**Trigger**: Push to `main` branch

1. **Build**: Each service's Dockerfile is built in parallel (matrix strategy)
2. **Push**: Images tagged `latest` + `sha-<commit>` → Docker Hub
3. **Deploy**: SSH into EC2 → `docker compose pull && up -d`

### Required GitHub Secrets

| Secret | Value |
|--------|-------|
| `DOCKERHUB_USERNAME` | `rahulgheek` |
| `DOCKERHUB_TOKEN` | Docker Hub access token |
| `EC2_HOST` | Elastic IP of your EC2 instance |
| `EC2_SSH_KEY` | Private SSH key (PEM format) |

## JVM Memory Budget (4 GB total)

| Component | Heap (`-Xmx`) | Container limit | Notes |
|-----------|---------------|-----------------|-------|
| Gateway | 384m | 512m | Heaviest — all traffic + rate limiting |
| Auth | 320m | 448m | JWT + Redis + PostgreSQL |
| Arena | 384m | 512m | WebSocket + MongoDB |
| Assessment | 320m | 448m | MongoDB CRUD |
| Analytics | 256m | 384m | PostgreSQL, low traffic |
| Sandbox | 256m | 384m | Orchestrator only |
| **Total** | **~1920m** | **~2688m** | Leaves ~1.3 GB for OS + ephemeral containers |
