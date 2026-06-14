# Docker 环境配置指南

## 1. 环境变量文件 (.env)

### 1.1 文件位置

在 `docker-env/` 目录下创建 `.env` 文件：

```
2026-Software-Development/
├── docker-env/
│   ├── .env                  ← 在这里创建
│   ├── docker-compose.yml
│   ├── init-db.ps1
│   ├── cv-inference/
│   ├── backend/
│   └── frontend/
```

### 1.2 文件内容模板

```bash
# LLM API 配置
LLM_API_KEY=你的API密钥
LLM_BASE_URL=https://api.deepseek.com

# 代理配置 (可选，不设置则自动使用国内镜像源)
# 如需使用代理，请先在 Clash 等工具中开启 Allow LAN (允许局域网连接)
# DOCKER_BUILD_PROXY=http://host.docker.internal:7890
```

> **注意**: `.env` 文件包含敏感信息，已加入 `.gitignore`，不会被提交到仓库。首次克隆项目后需要手动创建。

---

## 2. 代理配置

Docker 构建镜像时需要下载依赖（pip、npm），根据网络环境选择以下方式：

### 方式一：使用代理（推荐有代理的开发者）

**前提条件**: Clash / V2Ray 等代理工具需要开启 **Allow LAN**（允许局域网连接），否则 Docker 容器无法访问宿主机代理。

1. 在 `.env` 中取消注释并设置代理地址：

```bash
DOCKER_BUILD_PROXY=http://host.docker.internal:7890
```

2. 确保代理端口与实际一致（默认 7890，Clash 默认端口）。

### 方式二：使用国内镜像源（无代理时自动生效）

不需要任何配置。当 `.env` 中没有设置 `DOCKER_BUILD_PROXY` 时，Dockerfile 会自动使用清华 PyPI 镜像源。

### 工作原理

```
.env 中有 DOCKER_BUILD_PROXY?
    ├── 是 → 传入 HTTP_PROXY/HTTPS_PROXY → pip/npm 走代理
    └── 否 → args 为空 → pip 自动走 pypi.tuna.tsinghua.edu.cn 镜像
```

---

## 3. 端口说明

| 服务             | 端口   | 用途             |
|-----------------|--------|------------------|
| MySQL           | 3306   | 数据库           |
| Redis           | 6379   | 缓存             |
| RabbitMQ        | 5672   | 消息队列 (AMQP)  |
| RabbitMQ 管理台  | 15672  | 管理界面         |
| ElasticSearch   | 9200   | 全文检索         |
| MinIO API       | 9000   | 对象存储 S3 接口 |
| MinIO 管理台     | 9001   | 管理界面         |
| CV 推理服务      | 8000   | YOLO 推理接口    |
| 后端 API        | 8080   | Spring Boot 服务 |
| 前端 Dev Server | 3000   | Vue 开发服务器   |

---

## 4. 快速启动

### 4.1 一键初始化（推荐）

```powershell
cd docker-env
.\init-db.ps1
```

脚本会自动完成：
1. 停止现有服务
2. 启动中间件（MySQL、Redis、RabbitMQ、ES、MinIO）
3. 等待 MySQL 就绪
4. 导入建表脚本和初始数据
5. 构建并启动 CV 推理服务
6. 等待推理服务健康检查通过

初始化完成后，在 IDEA 中启动后端，在 `frontend/` 目录执行 `npm run dev` 启动前端。

### 4.2 手动启动

```powershell
cd docker-env

# 仅启动中间件（本地开发用）
docker compose --profile infra up -d

# 启动全部服务（部署用）
docker compose --profile infra --profile app up -d

# 停止全部服务
docker compose --profile infra --profile app down
```

---

## 5. 常见问题

### Q: 构建 CV 推理服务时报 ProxyError / Connection refused

**原因**: Docker 容器无法访问宿主机的代理。

**排查步骤**:
1. 确认代理工具已开启 **Allow LAN**
2. 确认代理端口正确（Clash 默认 7890）
3. 检查 `.env` 中 `DOCKER_BUILD_PROXY` 的端口是否匹配

**快速验证**:
```powershell
# 测试容器内是否能访问代理
docker run --rm alpine/curl curl -x http://host.docker.internal:7890 https://www.baidu.com
```

### Q: Docker Desktop 覆盖了 .env 中的代理配置

**原因**: Docker Desktop 会读取 Windows 系统代理设置（注册表 `ProxyServer`），自动注入 `HTTP_PROXY` 环境变量。

**解决**: 项目使用自定义变量名 `DOCKER_BUILD_PROXY` 来避免冲突，无需额外处理。

### Q: pip install 很慢但没报错

取消 `.env` 中 `DOCKER_BUILD_PROXY` 的注释，改为使用国内镜像源，或确保代理工具已开启 Allow LAN。

### Q: init-db.ps1 报错 "MySQL 启动超时"

检查 Docker Desktop 是否分配了足够的内存（建议至少 4GB）。MySQL + ES + RabbitMQ 同时启动需要较多资源。

---

## 6. 自定义配置

### 6.1 修改数据库密码

在 `.env` 中添加：

```bash
MYSQL_ROOT_PASSWORD=自定义root密码
MYSQL_USER=自定义用户名
MYSQL_PASSWORD=自定义用户密码
```

### 6.2 修改 Redis 密码

```bash
REDIS_PASSWORD=自定义Redis密码
```

### 6.3 使用 GPU 推理

编辑 `docker-compose.yml`，取消 cv-inference 服务中 GPU 相关注释：

```yaml
deploy:
  resources:
    reservations:
      devices:
        - driver: nvidia
          count: 1
          capabilities: [gpu]
```
