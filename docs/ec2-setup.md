# O One Outpost — EC2 Setup Guide

## 1. Launch EC2 Instance

1. Go to AWS Console → EC2 → **Launch Instance**
2. **Name**: `o-one-outpost`
3. **AMI**: Ubuntu 24.04 LTS (64-bit x86)
4. **Instance type**: `t3.medium` (2 vCPU, 4 GB RAM)
5. **Key pair**: Create or select an existing SSH key pair
6. **Network**: Default VPC, public subnet, Auto-assign public IP = **Enable**
7. **Security Group** — create new with these rules:

| Type  | Port  | Source          | Purpose          |
|-------|-------|-----------------|------------------|
| SSH   | 22    | My IP           | Admin access     |
| HTTP  | 80    | 0.0.0.0/0       | Gateway traffic  |
| HTTPS | 443   | 0.0.0.0/0       | Future TLS       |

> **Important**: Do NOT open ports 8081-8085. Services are internal-only via Docker networking.

8. **Storage**: 20 GB gp3 (default is fine)
9. **Launch** the instance

## 2. Attach Elastic IP

1. EC2 Console → **Elastic IPs** → Allocate Elastic IP address
2. Select the new IP → **Actions** → **Associate Elastic IP address**
3. Choose your `o-one-outpost` instance
4. Note the IP: `___.___.___.___ ` ← update your Vercel frontend's API URL to point here

## 3. Install Docker Engine

SSH into the instance:
```bash
ssh -i your-key.pem ubuntu@YOUR_ELASTIC_IP
```

Install Docker Engine + Compose plugin:
```bash
# Add Docker's official GPG key and repo
sudo apt-get update
sudo apt-get install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# Install Docker
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# Add ubuntu user to docker group (no sudo needed for docker commands)
sudo usermod -aG docker ubuntu

# IMPORTANT: Log out and back in for group change to take effect
exit
```

SSH back in and verify:
```bash
docker --version
docker compose version
```

## 4. Deploy the Application

```bash
# Create project directory
mkdir -p ~/o-one-outpost
cd ~/o-one-outpost

# Copy docker-compose.prod.yml to the server
# (from your local machine:)
# scp -i your-key.pem docker-compose.prod.yml ubuntu@YOUR_ELASTIC_IP:~/o-one-outpost/

# Create .env.prod from the template and fill in your real secrets
nano .env.prod

# Find the Docker group ID (needed for code-sandbox socket access)
getent group docker | cut -d: -f3
# Set DOCKER_GID in .env.prod to this value

# Pull the execution image for the sandbox
docker pull eclipse-temurin:17-alpine

# Pull and start all services
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
```

## 5. Verify

```bash
# Check all containers are running
docker compose -f docker-compose.prod.yml ps

# Check logs for any service
docker compose -f docker-compose.prod.yml logs -f gateway-service

# Check memory usage
free -h
docker stats --no-stream

# Test gateway is accessible
curl http://localhost/api/auth/login

# Confirm internal services are NOT accessible from outside
# (from your LOCAL machine, NOT the EC2):
# curl http://YOUR_ELASTIC_IP:8081  → should timeout/refuse
# curl http://YOUR_ELASTIC_IP:80/api/auth/login  → should work
```

## 6. Manual Redeploy (after CI pushes new images)

```bash
cd ~/o-one-outpost
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d --remove-orphans
docker image prune -f
```
