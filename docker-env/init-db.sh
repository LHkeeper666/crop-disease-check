#!/bin/bash
# ============================================================
# 智慧农业系统 - Docker 中间件启动 & 数据库初始化脚本
# 用法: 在 docker-env 目录下执行  bash init-db.sh
# ============================================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# 配置
CONTAINER_NAME="agri-monitor-mysql"
ROOT_PASS="agri_root_2026"
DB_NAME="agriculture_db"
CHARSET="utf8mb4"

# SQL 文件路径 (相对于 docker-env 目录)
CREATE_TABLE_SQL="../src/main/resources/db/create_table.sql"
INIT_DATA_SQL="../src/main/resources/db/init_data.sql"

echo -e "${CYAN}=== 0. 停止现有服务 ===${NC}"
docker compose --profile infra --profile app down

echo ""
echo -e "${CYAN}=== 1. 启动中间件 (Docker Compose --profile infra) ===${NC}"
docker compose --profile infra up -d

echo ""
echo -e "${CYAN}=== 2. 等待 MySQL 就绪 ===${NC}"
MAX_RETRIES=30
for i in $(seq 1 $MAX_RETRIES); do
    HEALTH=$(docker inspect --format='{{.State.Health.Status}}' "$CONTAINER_NAME" 2>/dev/null || echo "unknown")
    if [ "$HEALTH" = "healthy" ]; then
        echo -e "${GREEN}MySQL 已就绪${NC}"
        break
    fi
    if [ "$i" -eq "$MAX_RETRIES" ]; then
        echo -e "${RED}MySQL 启动超时，请检查 docker logs $CONTAINER_NAME${NC}"
        exit 1
    fi
    echo "  等待中... ($i/$MAX_RETRIES)"
    sleep 2
done

echo ""
echo -e "${CYAN}=== 3. 导入建表脚本 (character-set=$CHARSET) ===${NC}"
docker cp "$(realpath "$CREATE_TABLE_SQL")" "${CONTAINER_NAME}:/tmp/create_table.sql"
if ! docker exec "$CONTAINER_NAME" \
    mysql -uroot -p"$ROOT_PASS" --default-character-set="$CHARSET" "$DB_NAME" -e "SOURCE /tmp/create_table.sql;"; then
    echo -e "${RED}建表脚本导入失败!${NC}"
    exit 1
fi
echo -e "${GREEN}建表完成${NC}"

echo ""
echo -e "${CYAN}=== 4. 导入初始数据 (character-set=$CHARSET) ===${NC}"
docker cp "$(realpath "$INIT_DATA_SQL")" "${CONTAINER_NAME}:/tmp/init_data.sql"
if ! docker exec "$CONTAINER_NAME" \
    mysql -uroot -p"$ROOT_PASS" --default-character-set="$CHARSET" "$DB_NAME" -e "SOURCE /tmp/init_data.sql;"; then
    echo -e "${RED}初始数据导入失败!${NC}"
    exit 1
fi
echo -e "${GREEN}数据导入完成${NC}"

echo ""
echo -e "${CYAN}=== 5. 验证数据编码 ===${NC}"
docker exec "$CONTAINER_NAME" \
    mysql -uroot -p"$ROOT_PASS" --default-character-set="$CHARSET" "$DB_NAME" \
    -e "SELECT id, name FROM inspection_plan LIMIT 3;"

echo ""
echo -e "${CYAN}=== 6. 构建 CV 推理服务镜像 ===${NC}"
docker compose --profile infra --profile app build cv-inference
if [ $? -ne 0 ]; then
    echo -e "${RED}CV 推理服务镜像构建失败!${NC}"
    exit 1
fi
echo -e "${GREEN}镜像构建完成${NC}"

echo ""
echo -e "${CYAN}=== 7. 启动 CV 推理服务 ===${NC}"
docker compose --profile infra --profile app up -d cv-inference

echo ""
echo -e "${CYAN}=== 8. 等待推理服务就绪 ===${NC}"
MAX_RETRIES=12
URL="http://127.0.0.1:8000/api/v1/health"

for i in $(seq 1 $MAX_RETRIES); do
    RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 5 "$URL" 2>/dev/null || echo "000")
    if [ "$RESPONSE" = "200" ]; then
        BODY=$(curl -s "$URL")
        CODE=$(echo "$BODY" | jq -r '.code // empty' 2>/dev/null)
        STATUS=$(echo "$BODY" | jq -r '.data.status // empty' 2>/dev/null)
        if [ "$CODE" = "200" ] && [ "$STATUS" = "ready" ]; then
            echo -e "${GREEN}CV 推理服务已就绪${NC}"
            break
        else
            echo -e "${YELLOW}  服务已响应但状态未就绪: $STATUS${NC}"
        fi
    else
        echo "  请求失败，HTTP状态码: $RESPONSE"
    fi

    if [ "$i" -eq "$MAX_RETRIES" ]; then
        echo -e "${RED}CV 推理服务启动超时，请检查 docker logs agri-monitor-cv${NC}"
        exit 1
    fi
    echo "  等待中... ($i/$MAX_RETRIES)"
    sleep 5
done

echo ""
echo -e "${GREEN}=== 9. 初始化完成 ===${NC}"
echo "  MySQL:       localhost:3306"
echo "  Redis:       localhost:6379"
echo "  RabbitMQ:    localhost:15672"
echo "  ES:          localhost:9200"
echo "  MinIO:       localhost:9001"
echo "  CV推理服务:   localhost:8000"
echo ""
echo -e "${YELLOW}请在 IDEA 中启动后端项目 (端口 8080)${NC}"
echo -e "${YELLOW}前端请在终端执行: cd frontend && npm run dev (端口 3000)${NC}"
