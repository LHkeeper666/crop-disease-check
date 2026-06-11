# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

智慧农业病虫害监测系统 — an intelligent agriculture pest/disease monitoring platform with a 2.5D digital twin dashboard, dual-pipeline YOLOv8 CV inference, event-driven work orders, and LLM-powered diagnostics.

## Architecture

Three-service architecture running on separate processes:

| Service | Stack | Default Port | Entry Point |
|---------|-------|-------------|-------------|
| Backend | Spring Boot 2.7 + Java 8 + MyBatis-Plus | 8080 | `src/main/java/com/agriculture/AgricultureApplication.java` |
| Frontend | Vue 3 + TypeScript + Vite + Tailwind CSS v4 | 5173 | `frontend/` |
| CV Inference | Python FastAPI + YOLOv8 + ONNX Runtime | 8000 | `docker-env/cv-inference/app/main.py` |

Communication flow:
```
摄像头(RTSP) → 后端(Java) → HLS → 前端(浏览器)
                ├── 定时抓帧 → Python推理服务(8000)
                ├── WebSocket推送 → 前端实时通知
                └── 邮件网关 → 专家回调
```

API base path: `/api/v1` (backend adds `/api` via `server.servlet.context-path`)

## Build & Run Commands

### Backend (Maven)
```bash
# Build
mvn clean package -DskipTests

# Run
mvn spring-boot:run

# Run with specific profile
mvn spring-boot:run -Dspring-boot.run.profiles=local
mvn spring-boot:run -Dspring-boot.run.profiles=docker
```

### Frontend (npm)
```bash
cd frontend

# Dev server
npm run dev

# Type check + build
npm run build

# Preview production build
npm run preview
```

### CV Inference Service (Python)
```bash
cd docker-env/cv-inference
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

### Docker (full stack)
```bash
cd docker-env
# Copy and edit environment file
cp .env.example .env
docker-compose up -d
```

### Database Setup
```bash
# Create schema and tables
mysql -u root -p < src/main/resources/db/create_table.sql

# Seed initial data
mysql -u root -p agriculture_db < src/main/resources/db/init_data.sql
```

Default credentials in `src/main/resources/application.yml`:
- DB: `agri` / `agri_pwd_2026` (MySQL `agriculture_db`)
- Redis: `agri_redis_2026`
- RabbitMQ: `agri` / `agri_mq_2026` (vhost `/agri`)
- MinIO: `agri_minio_admin` / `agri_minio_2026`

API docs (Knife4j): `http://localhost:8080/api/doc.html`

## Backend Package Structure

```
com.agriculture
├── controller/    # REST controllers (18 controllers)
├── service/       # Business interfaces
│   └── impl/      # Service implementations
├── dao/mapper/    # MyBatis-Plus mapper interfaces
├── entity/        # Database entities (maps to tables)
├── dto/           # Data Transfer Objects
├── vo/            # View Objects (Result wrapper)
├── config/        # WebMvcConfig, etc.
└── exception/     # BusinessException, GlobalExceptionHandler
```

Convention: Each module follows `controller → service → mapper` layering. Entity names match table names with underscore-to-camelCase mapping (e.g., `work_order` → `WorkOrder`).

## Frontend Structure

```
frontend/src/
├── views/         # Page components (Dashboard, Devices, WorkOrders, Reports, Agent)
├── components/    # Reusable UI (GlassCard, DataMetric, GlowButton, AppSidebar)
├── layouts/       # MainLayout (sidebar + content)
├── stores/        # Pinia stores (auth.ts, workorder.ts)
├── mock/          # Mock data for development
├── styles/        # Tailwind CSS main entry
└── main.ts        # Router + Pinia setup
```

Design system: Dark cyberpunk theme with glassmorphism. Key CSS classes: `glass`, `glow-red`, `glow-amber`, `pulse-green`. Custom colors: `cyber-green`, `sakura`, `amber`.

## Key Domain Concepts

### Role-Based Access Control (RBAC)
Four roles: `ADMIN` > `EXPERT` > `MANAGER` > `VISITOR`. JWT token stored in `localStorage` as `treeforge_token`.

### Dual CV Pipeline
Two YOLOv8 models run sequentially per image:
- `disease` model (38 classes, PlantVillage-based) — plant disease detection
- `pest` model (102 classes) — insect pest detection

Results annotated with color-coded bounding boxes: red=`[病]` (disease), blue=`[虫]` (pest).

### Work Order State Machine
```
PENDING → PROCESSING → DONE
                    → IGNORED
PENDING → ESCALATED (auto after timeout)
```
Experts interact via email Token callback (no login required).

### Database Conventions
- Primary keys: UUID strings (`VARCHAR(36)`)
- Soft delete: `deleted TINYINT DEFAULT 0` (MyBatis-Plus logical delete)
- Timestamps: `created_at` / `updated_at` with auto-update
- All tables use `utf8mb4` charset

## Conventions for AI-Generated Code

- Backend: Java 8 syntax only (no `var`, no records, no text blocks). Use Lombok `@Data`/`@Builder` on entities.
- Frontend: Vue 3 Composition API (`<script setup lang="ts">`). Use Tailwind v4 utility classes. No Element Plus — custom glassmorphism components.
- MyBatis-Plus: Use `LambdaQueryWrapper` for queries, `@TableName` annotation on entities.
- All API responses wrapped in `Result<T>` (code/message/data pattern).
- File paths in code use forward slashes; the project runs on Windows during development.

## Documentation

Detailed docs in `docs/`:
- `docs/接口文档.md` — Complete REST API specification
- `docs/数据库设计文档.md` — Schema and ER diagrams
- `docs/迭代一内容/软件需求规格说明书.md` — Full SRS with all 12 functional requirements
- `docs/迭代一内容/技术选型.md` — Technology selection rationale
