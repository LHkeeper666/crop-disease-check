# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Smart Agriculture Pest & Disease Monitoring System (智慧农业病虫害无人值守监测系统). A three-tier application with a Java backend, Python CV inference service, and Vue 3 frontend.

## Build & Run Commands

### Backend (Spring Boot, from project root)
```bash
mvn clean package                    # Build JAR
mvn spring-boot:run                  # Run locally (uses application-local.yml)
mvn test                             # Run all tests
mvn -Dtest=CodeGeneratorTest#generateCode test   # Generate Entity/Mapper/Service/Controller from DB
```

### Frontend (from frontend/)
```bash
npm install
npm run dev          # Dev server on port 3000 (proxies /api → localhost:8080)
npm run build        # Production build (vue-tsc + vite)
```

### CV Inference Service (from inference-service/)
```bash
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000
```

### Docker Compose (from docker-env/)

Uses profiles to separate infrastructure from application services:

```bash
# Development: start middleware only (MySQL, Redis, RabbitMQ, ES, MinIO)
docker compose --profile infra up -d

# Deployment: start all services (middleware + backend + frontend + CV inference)
docker compose --profile infra --profile app up -d

# Stop all
docker compose --profile infra --profile app down
```

Build args use `host.docker.internal:7897` for in-container proxy access.

### Database Init
SQL scripts at `src/main/resources/db/`: run `create_table.sql` then `init_data.sql` against MySQL `agriculture_db`.
PowerShell shortcut: `docker-env/init-db.ps1` (starts middleware only via `--profile infra`, waits for MySQL, imports SQL).

## Architecture

```
┌─────────────┐     ┌──────────────┐     ┌───────────────────┐
│  Vue 3 SPA  │────▶│    Nginx     │────▶│  Spring Boot API   │
│  (port 3000)│     │  (port 80)   │     │  (port 8080)       │
└─────────────┘     └──────┬───────┘     └──┬──┬──┬──┬──┬────┘
                           │                │  │  │  │  │
                    /cv/   │           ┌────┘  │  │  │  └──────┐
                           ▼           ▼       ▼  ▼  ▼         ▼
                    ┌──────────┐   MySQL   Redis  RabbitMQ  ES  MinIO
                    │ FastAPI  │
                    │ (8000)   │
                    └──────────┘
```

**Backend** (`src/`): Spring Boot 3.2.5, Java 17, MyBatis-Plus. Modular architecture with domain-driven package structure. Context path: `/api`.

Backend modules (`com.agriculture.modules.*`): auth, user, company, camera, grid, greenhouse, environment, report, inference, workorder, inspection, dailyReport, statistics, agriBrain, pestDiseaseInfo. Common code in `com.agriculture.common.*` (config, exception, interceptor, annotation, aspect, util, websocket, vo, service).

**Frontend** (`frontend/`): Vue 3 + TypeScript + Vite + TailwindCSS + Pinia. Currently uses mock data in `stores/` — real API integration pending.

**CV Inference** (`inference-service/`): FastAPI + Ultralytics YOLOv8. Dual pipeline: disease model (38 classes) + pest model (102 classes). Endpoints under `/api/v1/detect`.

## Key Patterns

- **Auth**: JWT tokens validated by `JwtInterceptor`. Public endpoints excluded in `WebMvcConfig`. Token stored as `treeforge_token` in localStorage on frontend.
- **RBAC**: `@RequireRole` annotation + `PermissionAspect` (AOP). Roles: admin, expert, manager, staff.
- **Response wrapper**: All endpoints return `Result<T>` (code/message/data). See `common/vo/Result.java`.
- **API docs**: Knife4j/Swagger at `http://localhost:8080/api/doc.html` when running.
- **Database**: 18 tables. Logical deletion via MyBatis-Plus `@TableLogic`. See `docs/数据库设计文档.md` for full schema.
- **API spec**: Full REST API documentation in `docs/接口文档.md` (51KB, 14 sections). Base path: `/api/v1`.

## Docker Profiles

| Profile | Services | Use Case |
|---------|----------|----------|
| `infra` | MySQL, Redis, RabbitMQ, ES, MinIO | Local development (run backend/frontend locally) |
| `app` | Backend, Frontend, CV Inference, Nginx | Full deployment |

**Development workflow**: `.\init-db.ps1` → start backend in IDEA → `npm run dev` in frontend/

## Docker Environment

All Dockerfiles reference code from the main project:
- Backend: builds from project root `src/` and `pom.xml`
- Frontend: builds from `frontend/` directory
- CV Inference: builds from `inference-service/` directory
- Models: mounted from `models/` directory (disease + pest)

## Conventions

- **Git branches**: `feature/US001-login`, `fix/BUG002-query-error`
- **Language**: Code comments and docs are in Chinese. Entity/field names in English.
- **DB generation**: Use `CodeGeneratorTest` to scaffold new Entity/Mapper/Service/Controller from existing tables.
- **Frontend proxy**: `/api` → backend, `/proxy/deepseek` → DeepSeek API, `/proxy/xiaomi` → Xiaomi API (configured in `vite.config.ts`).
- **SSE**: AI streaming responses use Server-Sent Events. Nginx config has special `/api/ai/` location with buffering disabled and 300s timeout.
- **WebSocket**: Work order real-time updates via `/ws/` path.
