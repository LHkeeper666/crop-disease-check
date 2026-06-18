#!/bin/bash
# ============================================================
# 智慧农业系统 - 原生安装脚本（不依赖 Docker）
# 适用于容器环境或无法使用 Docker 的服务器
# 用法: bash init-native.sh
# ============================================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

# 配置
ROOT_PASS="agri_root_2026"
DB_NAME="agriculture_db"
CHARSET="utf8mb4"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SQL_DIR="$SCRIPT_DIR/../src/main/resources/db"

# 数据目录
DATA_DIR="/data/agriculture"
mkdir -p "$DATA_DIR"/{mysql,redis,rabbitmq,es,minio}

echo -e "${CYAN}========================================${NC}"
echo -e "${CYAN}  智慧农业系统 - 原生环境安装脚本${NC}"
echo -e "${CYAN}========================================${NC}"
echo ""

# ============================================================
echo -e "${CYAN}=== 1. 安装系统依赖 ===${NC}"
apt update
apt install -y \
    mysql-server \
    redis-server \
    rabbitmq-server \
    curl \
    wget \
    gnupg2 \
    lsb-release \
    jq \
    openjdk-17-jre-headless

echo -e "${GREEN}系统依赖安装完成${NC}"

# ============================================================
echo ""
echo -e "${CYAN}=== 2. 配置并启动 MySQL ===${NC}"

# 启动 MySQL
service mysql start || mysqld_safe --datadir="$DATA_DIR/mysql" &

# 等待 MySQL 就绪
echo "等待 MySQL 就绪..."
for i in $(seq 1 30); do
    if mysqladmin ping -h localhost --silent 2>/dev/null; then
        echo -e "${GREEN}MySQL 已就绪${NC}"
        break
    fi
    if [ "$i" -eq 30 ]; then
        echo -e "${RED}MySQL 启动超时${NC}"
        exit 1
    fi
    sleep 2
done

# 配置 root 密码和数据库
mysql -uroot -e "
ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY '$ROOT_PASS';
CREATE DATABASE IF NOT EXISTS $DB_NAME CHARACTER SET $CHARSET COLLATE ${CHARSET}_general_ci;
FLUSH PRIVILEGES;
" 2>/dev/null || mysql -uroot -p"$ROOT_PASS" -e "
CREATE DATABASE IF NOT EXISTS $DB_NAME CHARACTER SET $CHARSET COLLATE ${CHARSET}_general_ci;
FLUSH PRIVILEGES;
"

# 导入 SQL
echo "导入建表脚本..."
mysql -uroot -p"$ROOT_PASS" --default-character-set=$CHARSET "$DB_NAME" < "$SQL_DIR/create_table.sql"
echo -e "${GREEN}建表完成${NC}"

echo "导入初始数据..."
mysql -uroot -p"$ROOT_PASS" --default-character-set=$CHARSET "$DB_NAME" < "$SQL_DIR/init_data.sql"
echo -e "${GREEN}数据导入完成${NC}"

# 验证
mysql -uroot -p"$ROOT_PASS" --default-character-set=$CHARSET "$DB_NAME" \
    -e "SELECT id, name FROM inspection_plan LIMIT 3;"

# ============================================================
echo ""
echo -e "${CYAN}=== 3. 配置并启动 Redis ===${NC}"

# 备份原配置
cp /etc/redis/redis.conf /etc/redis/redis.conf.bak 2>/dev/null || true

# 配置 Redis
cat > /etc/redis/redis.conf << 'EOF'
bind 0.0.0.0
protected-mode no
port 6379
dir /data/agriculture/redis
daemonize yes
EOF

redis-server /etc/redis/redis.conf
echo -e "${GREEN}Redis 启动完成${NC}"

# ============================================================
echo ""
echo -e "${CYAN}=== 4. 配置并启动 RabbitMQ ===${NC}"

# 启动 RabbitMQ
service rabbitmq-server start || rabbitmq-server -detached

# 等待就绪
sleep 5

# 启用管理插件
rabbitmq-plugins enable rabbitmq_management 2>/dev/null || true

# 创建用户和虚拟主机
rabbitmqctl add_user agri agri_pass_2026 2>/dev/null || true
rabbitmqctl set_user_tags agri administrator 2>/dev/null || true
rabbitmqctl set_permissions -p / agri ".*" ".*" ".*" 2>/dev/null || true

echo -e "${GREEN}RabbitMQ 启动完成${NC}"
echo "  管理界面: http://localhost:15672 (guest/guest)"

# ============================================================
echo ""
echo -e "${CYAN}=== 5. 安装并启动 Elasticsearch ===${NC}"

ES_VERSION="7.17.9"
ES_DIR="/usr/share/elasticsearch"

if ! command -v elasticsearch &>/dev/null; then
    # 添加 Elasticsearch 仓库
    wget -qO - https://artifacts.elastic.co/GPG-KEY-elasticsearch | gpg --dearmor -o /usr/share/keyrings/elasticsearch.gpg
    echo "deb [signed-by=/usr/share/keyrings/elasticsearch.gpg] https://artifacts.elastic.co/packages/7.x/apt stable main" > /etc/apt/sources.list.d/elastic-7.x.list
    apt update
    apt install -y elasticsearch

    # 配置
    cat >> /etc/elasticsearch/elasticsearch.yml << EOF
network.host: 0.0.0.0
discovery.type: single-node
xpack.security.enabled: false
EOF
fi

# 启动 ES（后台运行）
su -s /bin/bash elasticsearch -c "/usr/share/elasticsearch/bin/elasticsearch -d -p /tmp/es.pid" 2>/dev/null || \
    /usr/share/elasticsearch/bin/elasticsearch -d -p /tmp/es.pid 2>/dev/null || true

echo "等待 Elasticsearch 就绪..."
for i in $(seq 1 30); do
    if curl -s http://localhost:9200/_cluster/health | grep -q '"status"'; then
        echo -e "${GREEN}Elasticsearch 已就绪${NC}"
        break
    fi
    sleep 2
done

# ============================================================
echo ""
echo -e "${CYAN}=== 6. 安装并启动 MinIO ===${NC}"

MINIO_DIR="/usr/local/bin"
if ! command -v minio &>/dev/null; then
    curl -SL https://dl.min.io/server/minio/release/linux-amd64/minio -o "$MINIO_DIR/minio"
    chmod +x "$MINIO_DIR/minio"
fi

# 启动 MinIO
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=minioadmin
export MINIO_ROOT_USER MINIO_ROOT_PASSWORD

nohup minio server "$DATA_DIR/minio" --address ":9000" --console-address ":9001" > /tmp/minio.log 2>&1 &
echo -e "${GREEN}MinIO 启动完成${NC}"

# ============================================================
# ============================================================
echo ""
echo -e "${CYAN}=== 7. 安装 Maven 并构建后端 ===${NC}"

PROJECT_DIR="$SCRIPT_DIR/.."

if ! command -v mvn &>/dev/null; then
    apt install -y maven
fi

echo "构建后端项目..."
cd "$PROJECT_DIR"
mvn clean package -DskipTests -q
echo -e "${GREEN}后端构建完成${NC}"

# ============================================================
echo ""
echo -e "${CYAN}=== 8. 启动后端服务 ===${NC}"

# 获取服务器公网 IP
PUBLIC_IP=$(curl -s ifconfig.me 2>/dev/null || curl -s ip.sb 2>/dev/null || echo "localhost")

# 后台启动 Spring Boot
nohup java -jar target/*.jar --spring.profiles.active=prod > /tmp/agri-backend.log 2>&1 &
BACKEND_PID=$!
echo "后端启动中，PID: $BACKEND_PID"

# 等待后端就绪
echo "等待后端服务就绪..."
for i in $(seq 1 60); do
    if curl -s http://localhost:8080/api/doc.html >/dev/null 2>&1; then
        echo -e "${GREEN}后端服务已就绪${NC}"
        break
    fi
    if [ "$i" -eq 60 ]; then
        echo -e "${YELLOW}后端启动较慢，请检查日志: tail -f /tmp/agri-backend.log${NC}"
    fi
    sleep 2
done

# ============================================================
echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  部署完成！${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "服务列表:"
echo "  MySQL:        localhost:3306 (root/$ROOT_PASS)"
echo "  Redis:        localhost:6379"
echo "  RabbitMQ:     localhost:5672 (管理界面: 15672)"
echo "  Elasticsearch: localhost:9200"
echo "  MinIO:        localhost:9000 (控制台: 9001)"
echo "  后端 API:     http://$PUBLIC_IP:8080/api"
echo "  API 文档:     http://$PUBLIC_IP:8080/api/doc.html"
echo ""
echo "MinIO 登录:"
echo "  用户名: minioadmin"
echo "  密码:   minioadmin"
echo ""
echo -e "${YELLOW}如需启动前端: cd $PROJECT_DIR/frontend && npm run dev${NC}"
