# aimon-backend

Clean, focused backend for AI-MON v0.2 — a voice-driven AI companion with push-to-talk, personality, memory, and kid-safe filtering.

**Refactored from:** `backyard` (90 files, 11K lines) → `aimon-backend` (47 files, ~4K lines)

## Features

- **WebSocket v4 Protocol:** Push-to-talk with interrupt support
- **Audio Pipeline:** OPUS streaming input, PCM16 output
- **Speech:** Google STT, VieNeu TTS (primary) + Google TTS (fallback)
- **AI:** LiteLLM proxy for OpenAI/Anthropic models
- **Memory:** PowerMem 3-layer memory system
- **Safety:** Kid Mode content filtering

## Architecture

```
Client (Pi)
  ↓ WebSocket v4 (push-to-talk)
AimonWebSocket
  ↓ OPUS frames
AudioPipelineService → STT (Google)
  ↓ transcript
ConversationProcessService → LLM (LiteLLM)
  ↓ streaming response + memory
ResponseStreamService → TTS (VieNeu/Google)
  ↓ PCM16 audio
Client (Pi)
```

## Tech Stack

- **Runtime:** Java 21 + Quarkus 3.24.5
- **Database:** PostgreSQL 16 + pgvector
- **Build:** Maven
- **Container:** Docker + Docker Compose

## Quick Start

### Prerequisites

- Java 21
- Docker + Docker Compose
- NVIDIA GPU (for VieNeu TTS)
- Google Cloud credentials (for STT/TTS)

### Setup

1. **Clone and configure:**
   ```bash
   cd aimon-backend
   cp .env.example .env
   # Edit .env with your API keys
   ```

2. **Place Google credentials:**
   ```bash
   cp /path/to/your/google-credentials.json .
   ```

3. **Build:**
   ```bash
   ./mvnw clean package
   ```

4. **Start services:**
   ```bash
   docker compose up -d
   ```

5. **Check logs:**
   ```bash
   docker compose logs -f aimon-backend
   ```

### WebSocket Endpoint

```
ws://localhost:8080/ws/audio/{robotId}
```

## Development

### Local Dev (without Docker)

```bash
# Start dependencies
docker compose up postgres memoryservice litellm vieneu-tts -d

# Run backend in dev mode
./mvnw quarkus:dev
```

### Run Tests

```bash
./mvnw test
```

### Build JVM Image

```bash
./mvnw package
docker build -f Dockerfile.jvm -t aimon/aimon-backend:latest .
```

## Project Structure

```
src/main/java/dev/aimon/
├── client/           # REST clients (LiteLLM, PowerMem)
├── config/           # Application config
├── dto/              # Data transfer objects
├── model/            # Domain models (RobotSession, ConversationSession)
├── service/
│   ├── ai/          # LLM services
│   ├── audio/       # Audio pipeline, OPUS codec
│   ├── conversation/# Conversation orchestration
│   ├── memory/      # PowerMem integration
│   ├── stt/         # Speech-to-text
│   └── tts/         # Text-to-speech (VieNeu, Google)
└── websocket/       # WebSocket v4 handler
```

## WebSocket v4 Protocol

### Client → Server

- `hello`: Handshake with protocol version
- `audio_start`: Begin audio streaming
- (binary): OPUS frames while button held
- `audio_stop`: End audio streaming, trigger processing
- `interrupt`: Cancel current response
- `ping`: Keepalive

### Server → Client

- `hello_ack`: Session established
- `asr_result`: Speech-to-text result
- `llm_stream`: LLM token chunks (streaming)
- `tts_start`: TTS sentence beginning
- (binary): PCM16 audio chunks
- `tts_stop`: TTS sentence complete
- `turn_end`: Conversation turn complete
- `error`: Error message
- `pong`: Keepalive response

## Configuration

Key settings in `src/main/resources/application.properties`:

```properties
# LiteLLM
ai.litellm.base-url=http://litellm:4000
ai.litellm.model=gpt-4o-mini

# Google STT
ai.google-stt.enabled=true
ai.google-stt.language-code=vi-VN

# VieNeu TTS (primary)
ai.vieneu-tts.base-url=http://vieneu-tts:5001
ai.vieneu-tts.voice-id=Ngoc

# Google TTS (fallback)
ai.google-tts.enabled=true

# PowerMem
quarkus.rest-client.powermem-api.url=http://memoryservice:8003

# Database
quarkus.datasource.jdbc.url=jdbc:postgresql://postgres:5432/aimon
```

## Docker Services

| Service | Port | Purpose |
|---------|------|---------|
| postgres | 5432 | Database (pgvector) |
| memoryservice | 8003 | PowerMem MCP server |
| litellm | 4000 | LLM proxy |
| vieneu-tts | 5001 | Vietnamese TTS (GPU) |
| aimon-backend | 8080 | Main backend |

## Troubleshooting

### Build fails with compilation errors

```bash
./mvnw clean compile
```

### Database migration fails

```bash
# Reset database
docker compose down -v
docker compose up -d
```

### VieNeu TTS not starting (GPU)

- Ensure NVIDIA Docker runtime installed
- Check GPU availability: `nvidia-smi`
- Or disable VieNeu TTS: `AI_VIENEU_TTS_ENABLED=false`

### WebSocket connection refused

- Check backend logs: `docker compose logs aimon-backend`
- Verify port 8080 not in use: `netstat -an | grep 8080`

## Performance Targets

- **Latency:** < 4s (90th percentile) per turn
- **Memory:** Persistent across sessions
- **Interrupt:** < 200ms from interrupt to TTS cancellation

## License

Proprietary - AI-MON Project
