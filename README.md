# 🚀 O(1) Outpost (formerly AlgoArena)

> **A scalable, microservices-driven platform for real-time competitive programming, algorithmic assessments, and secure code execution.**

![Docker](https://img.shields.io/badge/Docker-Enabled-blue?logo=docker)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.x-brightgreen?logo=spring)
![React](https://img.shields.io/badge/React-Vite-blue?logo=react)
![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)

---

## 📖 Table of Contents
1. [About the Project](#-about-the-project)
2. [Architecture Overview](#-architecture-overview)
3. [Prerequisites](#-prerequisites)
4. [Local Development Guide](#-local-development-guide)
5. [Production Deployment Guide](#-production-deployment-guide-botkeep--docker-vps)
6. [Security & Sandbox Architecture](#-security--sandbox-architecture)

---

## 🎯 About the Project

**O(1) Outpost** is a robust platform designed to facilitate algorithmic coding challenges, technical assessments, and real-time coding arenas. Built with a focus on scalability and security, it leverages a distributed microservices architecture to isolate responsibilities, ensuring high availability and secure execution of user-submitted code.

---

## 🏗️ Architecture Overview

The system is broken down into independent, highly cohesive microservices that can be deployed separately.

### Tech Stack
- **Frontend**: React.js, Vite
- **Backend Core**: Java 17/21, Spring Boot, Spring Cloud Gateway
- **Databases & Caching**: MongoDB, PostgreSQL, Redis
- **DevOps & Deployment**: Botkeep (managed Java hosting), Docker, Docker Compose, Vercel

### Microservices Ecosystem

| Service | Port | Description |
|---|---|---|
| **API Gateway** | `8080` | The central entry point. Handles routing, rate-limiting (via Redis), and filters requests before they hit internal services. |
| **Auth Service** | `8081` | Manages user registration, login, and JWT-based authentication. |
| **Analytics Service** | `8082` | Aggregates user metrics, match histories, and platform statistics. |
| **Assessment Service**| `8083` | Manages candidate test sessions, tracking progress, and evaluations. |
| **Arena Service** | `8084` | Manages real-time coding matches, daily questions, and WebSockets for live collaborative events. |
| **Code Sandbox** | `8085` | A highly secure, isolated execution engine. It dynamically provisions "warm" Docker containers to compile and run untrusted user code safely. |

---

## ⚙️ Prerequisites

Before you begin, ensure you have the following installed:
* **Docker & Docker Compose**: Essential for running the backend infrastructure.
* **Node.js (v20+)**: Required for running and building the frontend locally.
* **Java (17/21) & Maven**: Only required if you intend to run the Spring Boot services natively outside of Docker.

---

## 🛠️ Local Development Guide

Follow these steps to get a local development environment up and running.

### 1. Environment Configuration
Security is a priority; therefore, no secrets are committed to the repository. You must create `.env` files in each service directory.

* **Required `.env` locations**:
  * `gateway-service/.env`
  * `auth-service/.env`
  * `analytics-service/.env`
  * `assessment-service/.env`
  * `arena-service/.env`
  * `code-sandbox/.env`
  * `O-one-outpost-web/.env`

* **Networking Note**: When running locally via Docker Desktop, ensure your inter-service URLs (like `AUTH_SERVICE_URL`) point to `http://host.docker.internal:<PORT>` so the containers can route traffic correctly.

### 2. Bootstrapping the Backend
We utilize `docker-compose.yml` to orchestrate the entire backend locally.

```bash
# From the root directory, build and start all containers in detached mode
docker compose up --build -d

# To view logs for a specific service (e.g., the gateway)
docker logs -f gateway
```

> **Note on the Sandbox**: The Code Sandbox mounts `/var/run/docker.sock`. Ensure your Docker daemon allows this mount, as the sandbox needs it to spawn ephemeral containers for code execution.

### 3. Starting the Frontend
Open a new terminal window and start the Vite development server:

```bash
cd O-one-outpost-web
npm install
npm run dev
```
Your frontend will now be accessible at `http://localhost:5173`.

---

## ☁️ Production Deployment Guide (Botkeep + Docker VPS)

Production is split into independently deployable services:

- **Five Botkeep Java workloads** (2 GB each): gateway, auth, arena, assessment, analytics.
- **One Docker VPS** for `code-sandbox`, because the sandbox mounts the Docker socket and spawns executor containers.
- **Vercel** for the React frontend.
- **Managed data services**: MongoDB Atlas, Neon PostgreSQL, Upstash Redis, Brevo SMTP.

Each service has its own `.env.example` and is configured through the Botkeep Environment panel. There is no shared `.env.prod`.

### 1. Build the JARs

```powershell
.\deploy\scripts\build-jars.ps1    # Windows
```

```bash
./deploy/scripts/build-jars.sh     # Linux / macOS
```

Artifacts land in `deploy/artifacts/<service>/app.jar`. CI also builds them whenever a service changes through `.github/workflows/build-jars.yml`.

### 2. Create the Botkeep workloads

For each backend service:

1. Create a **Java** workload and select **Java 21**.
2. Allocate **2 GB RAM**.
3. Upload `app.jar` (or the ZIP produced by `deploy/scripts/package-botkeep.ps1`).
4. Use the start command from `deploy/botkeep/start-commands.md`.
5. Paste the variables from that service's `.env.example` into the Environment panel.
6. Attach the custom domain (`api`, `auth`, `arena`, `assessment`, `analytics`).

### 3. Deploy the sandbox VPS

Follow `deploy/vps/code-sandbox/README.md`. Caddy terminates TLS for `sandbox.yourdomain.com`; Docker runs the sandbox orchestrator and its warm executor containers.

### 4. Deploy the frontend

Set `VITE_API_GATEWAY_URL=https://api.yourdomain.com` and `VITE_ARENA_WS_URL=wss://api.yourdomain.com` in Vercel, then deploy.

> Full topology, secret matrix, deployment order, verification steps, and troubleshooting: [`deploy/README.md`](deploy/README.md).

### Legacy Docker deployment

The root `docker-compose.prod.yml` and `.github/workflows/deploy.yml` still describe the original single-host Docker/AWS EC2 flow. They are kept for reference and are not used by the Botkeep topology.

---

## 🛡️ Security & Sandbox Architecture

Executing untrusted user code is inherently dangerous. O(1) Outpost mitigates this risk through a robust **Code Sandbox Architecture**:

1. **Warm Container Pool**: To eliminate cold-start latency, the sandbox maintains a pool of pre-warmed, idle Docker containers (`eclipse-temurin:17-alpine`).
2. **Ephemeral Execution**: When a user submits code, it is injected into a warm container, executed, and the container is instantly destroyed and replaced.
3. **Hardened Restrictions**:
   - `--network none`: No internet access for executing code.
   - Memory and CPU are strictly capped (`--memory=128m`, `--cpus=0.5`).
   - Read-only filesystems with small, temporary `tmpfs` mounts to prevent malicious disk writes.

---

## 📄 License
Distributed under the MIT License. See `LICENSE` for more information.
