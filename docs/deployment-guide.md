# AI-MON Deployment Guide

**Last Updated:** 2026-02-15
**Version:** v0.2 Production Ready
**Target:** Docker Compose (Development & Testing) | Kubernetes (Future)

---

## Prerequisites

### System Requirements

**Development Machine:**
- OS: Linux, macOS, or Windows (WSL2)
- Docker: 20.10+ with Docker Compose 2.0+
- Java: 21+ (for local builds)
- Maven: 3.9+
- Git: 2.0+

**Production Server:**
- CPU: 4+ cores (2 cores minimum for light load)
- RAM: 8GB+ (6GB minimum)
- Storage: 20GB SSD (for database, logs)
- GPU: NVIDIA (optional, for VieNeu TTS on same machine)

**Raspberry Pi (Client):**
- Raspberry Pi Zero 2 or equivalent
- 1GB+ RAM
- 4GB+ SD card
- WiFi connectivity to server

### External Dependencies

1. **Google Cloud Project**
   - Service account with STT/TTS permissions
   - JSON credentials file

2. **LLM Provider** (one of):
   - OpenAI API key (recommended: GPT-4o-mini)
   - Anthropic (Claude)
   - Other LiteLLM-supported provider

3. **Network**
   - Open ports: 8080 (aimon-backend), 5432 (postgres), 5001 (vieneu-tts)
   - Firewall: Allow traffic between services
   - DNS: Resolvable hostnames for external APIs

---

## Quick Start (Docker Compose)

### 1. Clone & Setup

```bash
cd aimon-backend
cp .env.example .env
# Edit .env with your credentials
```

### 2. Place Google Credentials

```bash
# Copy your Google Cloud service account JSON to the project root
cp /path/to/google-credentials.json ./google-credentials.json

# Verify it exists
ls -la google-credentials.json
```

### 3. Build Backend

```bash
./mvnw clean package -DskipTests
# Result: target/quarkus-app/ directory created
```

### 4. Start Services

```bash
docker compose up -d
```

**Services Starting:**
```
postgres:5432        ✓ Database
memoryservice:8003   ✓ PowerMem MCP
litellm:4000        ✓ LLM Proxy
vieneu-tts:5001     ✓ Vietnamese TTS (GPU)
aimon-backend:8080  ✓ Main backend
```

### 5. Verify Deployment

```bash
# Check all containers running
docker compose ps

# Check logs
docker compose logs -f aimon-backend

# Health check
curl http://localhost:8080/health

# WebSocket endpoint available
curl -i -N -H "Connection: Upgrade" -H "Upgrade: websocket" \
  http://localhost:8080/ws/audio/test-robot
```

### 6. Stop Services

```bash
docker compose down

# Or with data cleanup
docker compose down -v
```

---

## Configuration

### Environment Variables (.env)

**File:** `.env.example` → copy to `.env` and fill in values

```bash
# ============================================
# LITELLM Configuration
# ============================================
AI_LITELLM_BASE_URL=http://litellm:4000
AI_LITELLM_API_KEY=your-litellm-key-or-empty
AI_LITELLM_MODEL=gpt-4o-mini

# ============================================
# Google Cloud Configuration
# ============================================
GOOGLE_APPLICATION_CREDENTIALS=/app/google-credentials.json
AI_GOOGLE_STT_LANGUAGE_CODE=vi-VN
AI_GOOGLE_STT_ENABLED=true
AI_GOOGLE_TTS_ENABLED=true

# ============================================
# VieNeu TTS Configuration
# ============================================
AI_VIENEU_TTS_BASE_URL=http://vieneu-tts:5001
AI_VIENEU_TTS_VOICE_ID=Ngoc
AI_VIENEU_TTS_ENABLED=true

# ============================================
# PowerMem Configuration
# ============================================
QUARKUS_REST_CLIENT_POWERMEM_API_URL=http://memoryservice:8003

# ============================================
# Database Configuration
# ============================================
QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://postgres:5432/aimon
QUARKUS_DATASOURCE_USERNAME=aimon
QUARKUS_DATASOURCE_PASSWORD=aimon_secure_password
QUARKUS_DATASOURCE_JDBC_MAX_SIZE=10
QUARKUS_DATASOURCE_JDBC_MIN_SIZE=2

# ============================================
# Application Configuration
# ============================================
QUARKUS_LOG_LEVEL=INFO
QUARKUS_LOG_CATEGORY_DEV_AIMON_LEVEL=DEBUG
FEATURE_KID_MODE_ENABLED=true
FEATURE_MEMORY_ENABLED=true

# ============================================
# Quarkus Profile
# ============================================
QUARKUS_PROFILE=prod
```

### Docker Compose Configuration

**File:** `docker-compose.yml`

```yaml
services:
  postgres:
    image: postgres:16-bullseye
    container_name: aimon-postgres
    environment:
      POSTGRES_DB: aimon
      POSTGRES_USER: ${QUARKUS_DATASOURCE_USERNAME:-aimon}
      POSTGRES_PASSWORD: ${QUARKUS_DATASOURCE_PASSWORD:-aimon_password}
    ports:
      - "5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U aimon"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - aimon-network

  memoryservice:
    image: memoryservice:latest
    container_name: aimon-memoryservice
    ports:
      - "8003:8003"
    environment:
      DATABASE_URL: postgresql://aimon:${QUARKUS_DATASOURCE_PASSWORD:-aimon_password}@postgres:5432/aimon
    depends_on:
      postgres:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8003/health"]
      interval: 10s
      timeout: 5s
      retries: 3
    networks:
      - aimon-network

  litellm:
    image: ghcr.io/berriai/litellm:main
    container_name: aimon-litellm
    ports:
      - "4000:4000"
    environment:
      OPENAI_API_KEY: ${OPENAI_API_KEY:-}
      ANTHROPIC_API_KEY: ${ANTHROPIC_API_KEY:-}
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:4000/health"]
      interval: 10s
      timeout: 5s
      retries: 3
    networks:
      - aimon-network

  vieneu-tts:
    image: vieneu-tts:gpu
    container_name: aimon-vieneu-tts
    ports:
      - "5001:5001"
    runtime: nvidia
    environment:
      CUDA_VISIBLE_DEVICES: "0"
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:5001/health"]
      interval: 10s
      timeout: 5s
      retries: 3
    networks:
      - aimon-network

  aimon-backend:
    build:
      context: .
      dockerfile: Dockerfile.jvm
    container_name: aimon-backend
    ports:
      - "8080:8080"
    environment:
      QUARKUS_DATASOURCE_JDBC_URL: ${QUARKUS_DATASOURCE_JDBC_URL:-jdbc:postgresql://postgres:5432/aimon}
      QUARKUS_DATASOURCE_USERNAME: ${QUARKUS_DATASOURCE_USERNAME:-aimon}
      QUARKUS_DATASOURCE_PASSWORD: ${QUARKUS_DATASOURCE_PASSWORD:-aimon_password}
      AI_LITELLM_BASE_URL: ${AI_LITELLM_BASE_URL:-http://litellm:4000}
      AI_VIENEU_TTS_BASE_URL: ${AI_VIENEU_TTS_BASE_URL:-http://vieneu-tts:5001}
      QUARKUS_REST_CLIENT_POWERMEM_API_URL: ${QUARKUS_REST_CLIENT_POWERMEM_API_URL:-http://memoryservice:8003}
      GOOGLE_APPLICATION_CREDENTIALS: ${GOOGLE_APPLICATION_CREDENTIALS:-/app/google-credentials.json}
      QUARKUS_PROFILE: ${QUARKUS_PROFILE:-prod}
    depends_on:
      postgres:
        condition: service_healthy
      memoryservice:
        condition: service_healthy
      litellm:
        condition: service_healthy
    volumes:
      - ./google-credentials.json:/app/google-credentials.json:ro
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/health"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - aimon-network

volumes:
  postgres-data:
    driver: local

networks:
  aimon-network:
    driver: bridge
```

---

## Database Setup

### Automated Migrations

Quarkus uses Flyway for automatic migrations. On startup:

1. Connects to PostgreSQL
2. Checks `flyway_schema_history` table
3. Runs pending migrations from `src/main/resources/db/migration/`
4. Updates schema version

**Migration Files:**
```
src/main/resources/db/migration/
├── V1__Initial_schema.sql           # Core tables
├── V2__Powermem_integration.sql     # Memory service tables
└── V3__Add_banned_keywords.sql      # Safety features
```

### Manual Migration (if needed)

```bash
# Connect to PostgreSQL
docker exec -it aimon-postgres psql -U aimon -d aimon

# View migrations applied
SELECT * FROM flyway_schema_history;

# Manually run SQL (not recommended)
\i /path/to/migration.sql
\q
```

### Database Schema

**Core Tables:**

| Table | Purpose | Rows |
|-------|---------|------|
| `parents` | Parent/guardian profiles | ~100 |
| `users` | Child user profiles | ~1000 |
| `banned_keywords` | Content safety (Kid Mode) | ~500 |
| PowerMem tables | Memory service (managed by memoryservice) | varies |

**Connection String:**
```
jdbc:postgresql://postgres:5432/aimon
User: aimon
Password: (from .env)
```

---

## Google Cloud Setup

### 1. Create Service Account

```bash
# Create project (if not exists)
gcloud projects create aimon-project

# Set active project
gcloud config set project aimon-project

# Create service account
gcloud iam service-accounts create aimon-backend \
  --display-name="AI-MON Backend"

# Grant roles (STT + TTS)
gcloud projects add-iam-policy-binding aimon-project \
  --member=serviceAccount:aimon-backend@aimon-project.iam.gserviceaccount.com \
  --role=roles/cloudtexttospeech.client

gcloud projects add-iam-policy-binding aimon-project \
  --member=serviceAccount:aimon-backend@aimon-project.iam.gserviceaccount.com \
  --role=roles/speech.client
```

### 2. Generate Credentials JSON

```bash
# Create key
gcloud iam service-accounts keys create google-credentials.json \
  --iam-account=aimon-backend@aimon-project.iam.gserviceaccount.com

# Verify
cat google-credentials.json

# Place in aimon-backend directory
cp google-credentials.json /path/to/aimon-backend/
```

### 3. Enable APIs

```bash
gcloud services enable \
  speech.googleapis.com \
  texttospeech.googleapis.com \
  cloudkms.googleapis.com
```

---

## Health Checks & Monitoring

### Quarkus Health Endpoints

```bash
# Overall health
curl http://localhost:8080/health

# Readiness (all services up)
curl http://localhost:8080/health/ready

# Liveness (process alive)
curl http://localhost:8080/health/live
```

**Response Example:**
```json
{
  "status": "UP",
  "checks": [
    {
      "name": "Database connection",
      "status": "UP"
    },
    {
      "name": "LiteLLM availability",
      "status": "UP"
    }
  ]
}
```

### Container Health Checks

```bash
# View container health status
docker compose ps

# Check individual service
docker inspect --format='{{.State.Health.Status}}' aimon-backend

# View logs for failed service
docker compose logs aimon-backend
```

### Manual Service Testing

```bash
# Test PostgreSQL
docker exec aimon-postgres pg_isready -U aimon

# Test LiteLLM
curl http://localhost:4000/health

# Test memoryservice
curl http://localhost:8003/health

# Test VieNeu TTS
curl http://localhost:5001/health

# Test aimon-backend
curl http://localhost:8080/health
```

---

## Troubleshooting

### Service Won't Start

**Problem:** Container exits immediately

**Solutions:**
```bash
# Check logs
docker compose logs aimon-backend

# Common causes:
# 1. Database not ready
docker compose logs postgres

# 2. Missing credentials
ls -la google-credentials.json

# 3. Port already in use
lsof -i :8080

# 4. Memory/resource constraints
docker stats
```

### Database Connection Failed

**Problem:** `FATAL: role "aimon" does not exist`

**Solutions:**
```bash
# Verify database container is healthy
docker compose ps postgres

# Check database exists
docker exec aimon-postgres psql -U postgres -c "\l"

# Recreate database
docker compose down -v
docker compose up -d postgres
docker compose up -d aimon-backend
```

### WebSocket Connection Refused

**Problem:** `Connection refused` when connecting to `ws://localhost:8080/ws/audio/{robotId}`

**Solutions:**
```bash
# Verify backend is running
docker compose ps aimon-backend

# Check if listening on port 8080
netstat -an | grep 8080

# Tail logs for errors
docker compose logs -f aimon-backend | grep -i websocket

# Test HTTP first
curl http://localhost:8080/health
```

### Out of Memory

**Problem:** Backend container crashes with OOM

**Solutions:**
```bash
# Increase JVM heap (in docker-compose.yml)
environment:
  JAVA_OPTS: "-Xms512m -Xmx2g"

# Or specify at startup
docker compose up aimon-backend -e JAVA_OPTS="-Xmx2g"

# Check current usage
docker stats aimon-backend
```

### GPU Not Detected (VieNeu TTS)

**Problem:** `No GPUs detected` in VieNeu TTS logs

**Solutions:**
```bash
# Verify NVIDIA Docker runtime installed
docker run --rm --gpus all nvidia/cuda:11.8.0-runtime-ubuntu22.04 nvidia-smi

# If not installed:
# On Ubuntu: sudo apt-get install -y nvidia-docker2
# On other OS: Follow NVIDIA documentation

# Verify compose uses correct runtime
docker compose config | grep runtime:

# Fallback: Disable VieNeu, use Google TTS
# In .env: AI_VIENEU_TTS_ENABLED=false
```

### High Latency

**Problem:** Conversation turns take >4s

**Root causes & solutions:**

```bash
# 1. Network latency
ping postgres
ping memoryservice
ping litellm

# 2. Slow STT (Google Cloud)
# Check audio quality, sample rate
# Monitor Google Cloud quota usage

# 3. LLM generation slow
# Check LiteLLM logs
docker compose logs litellm | tail -50

# 4. TTS bottleneck
# Monitor VieNeu service
docker compose logs vieneu-tts

# 5. Database slow
# Check active connections
docker exec aimon-postgres psql -U aimon -c "SELECT * FROM pg_stat_activity;"

# Profile with timing
curl -v http://localhost:8080/health
```

---

## Logs & Debugging

### View Logs

```bash
# All services
docker compose logs -f

# Single service
docker compose logs -f aimon-backend

# Last 100 lines
docker compose logs --tail=100 aimon-backend

# Filter by time
docker compose logs --since 30m aimon-backend

# Search logs
docker compose logs aimon-backend | grep -i error
```

### Log Configuration

**File:** `src/main/resources/application.properties`

```properties
quarkus.log.level=INFO
quarkus.log.category."dev.aimon".level=DEBUG
quarkus.log.category."io.quarkus.websockets".level=DEBUG

# Log format
quarkus.log.console.format=%d{yyyy-MM-dd HH:mm:ss,SSS} %-5p [%c{2.}] (%t) %s%e%n

# File logging (optional)
quarkus.log.file.enabled=true
quarkus.log.file.path=/var/log/aimon/aimon-backend.log
quarkus.log.file.rotation.max-file-size=10M
quarkus.log.file.rotation.max-backup-index=5
```

### Debug Mode

```bash
# Start in debug mode (local dev)
./mvnw quarkus:dev -Ddebug

# Or in Docker
docker compose exec aimon-backend java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 -jar app/quarkus-run.jar

# Connect IDE debugger to port 5005
```

---

## Performance Tuning

### JVM Options

```bash
# In docker-compose.yml or .env
JAVA_OPTS="-Xms1g -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

### Database Connection Pool

```properties
# application.properties
quarkus.datasource.jdbc.max-size=20
quarkus.datasource.jdbc.min-size=5
quarkus.datasource.jdbc.connection-timeout=30s
```

### WebSocket Optimization

```properties
# application.properties
quarkus.http.io-thread-pool-size=8
quarkus.http.worker-thread-pool-size=40
```

---

## Scaling Considerations

### Horizontal Scaling (Multiple Backends)

**Setup:**
```
Load Balancer (nginx/HAProxy)
  ├── aimon-backend:8080 (instance 1)
  ├── aimon-backend:8080 (instance 2)
  └── aimon-backend:8080 (instance 3)
       ↓
       PostgreSQL (shared)
```

**Docker Compose (multiple backends):**
```bash
# Scale to 3 instances
docker compose up -d --scale aimon-backend=3

# Load balancer routes WebSocket to correct instance
# (sticky sessions required)
```

### Vertical Scaling (Larger Machine)

**Increase resources:**
```bash
# JVM heap
JAVA_OPTS="-Xms4g -Xmx8g"

# Connection pool
quarkus.datasource.jdbc.max-size=50

# Thread pools
quarkus.http.worker-thread-pool-size=100
```

---

## Backup & Restore

### Database Backup

```bash
# Backup PostgreSQL
docker exec aimon-postgres pg_dump -U aimon aimon > aimon-backup.sql

# Compressed backup
docker exec aimon-postgres pg_dump -U aimon aimon | gzip > aimon-backup.sql.gz

# Backup with verbose output
docker exec aimon-postgres pg_dump -U aimon -v aimon > aimon-backup.sql
```

### Database Restore

```bash
# Restore from backup
docker exec -i aimon-postgres psql -U aimon aimon < aimon-backup.sql

# Or from compressed backup
gunzip < aimon-backup.sql.gz | docker exec -i aimon-postgres psql -U aimon aimon
```

### Volume Backup

```bash
# Backup database volume
docker run --rm -v aimon-postgres-data:/data -v $(pwd):/backup ubuntu tar czf /backup/postgres-backup.tar.gz -C /data .

# Restore volume backup
docker run --rm -v aimon-postgres-data:/data -v $(pwd):/backup ubuntu tar xzf /backup/postgres-backup.tar.gz -C /data
```

---

## Security Hardening

### Network Security

```yaml
# In docker-compose.yml
networks:
  aimon-network:
    driver: bridge
    driver_opts:
      com.docker.network.driver.mtu: 1500

# Firewall rules (example ufw)
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow 8080/tcp  # aimon-backend
sudo ufw allow 22/tcp    # SSH (if remote)
```

### Secrets Management

**Use Docker secrets (Swarm mode):**
```bash
echo "google-credentials-json" | docker secret create google-creds -

# Or Kubernetes secrets:
kubectl create secret generic google-creds --from-file=google-credentials.json
```

### TLS/HTTPS

**Nginx reverse proxy:**
```nginx
upstream aimon {
    server aimon-backend:8080;
}

server {
    listen 443 ssl;
    server_name aimon.example.com;

    ssl_certificate /etc/ssl/certs/cert.pem;
    ssl_certificate_key /etc/ssl/private/key.pem;

    location / {
        proxy_pass http://aimon;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

---

## Production Checklist

- [ ] `.env` configured with real credentials
- [ ] Google Cloud service account created & credentials placed
- [ ] Database backup strategy in place
- [ ] Logs aggregated to external service (optional)
- [ ] Health checks configured & monitored
- [ ] Firewall rules configured
- [ ] TLS certificates installed (if using HTTPS)
- [ ] Database backups automated (cron job)
- [ ] Monitoring alerts set up (latency, errors, memory)
- [ ] Runbooks created for common failures
- [ ] Load testing completed (target: 10+ concurrent sessions)
- [ ] Security audit completed

---

## Support & Rollback

### Version Management

```bash
# Tag release
git tag -a v0.2.0 -m "Production release"
git push origin v0.2.0

# Deploy specific version
docker pull aimon/aimon-backend:v0.2.0
# Update docker-compose.yml to use specific tag
docker compose up -d
```

### Rollback Procedure

```bash
# If new deployment fails:
1. Note the previous working version tag
2. Update docker-compose.yml to previous tag
3. Restore database backup if schema changed:
   docker exec -i aimon-postgres psql -U aimon aimon < backup.sql
4. Restart services:
   docker compose down
   docker compose up -d
5. Verify health:
   curl http://localhost:8080/health
```

---

## References

- Docker Docs: https://docs.docker.com/compose/
- Quarkus Deployment: https://quarkus.io/guides/deploying-to-production
- PostgreSQL Administration: https://www.postgresql.org/docs/current/
- Google Cloud Services: https://cloud.google.com/docs
