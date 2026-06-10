# ============================================================
# 智慧农业系统 - Docker 启动 & 数据库初始化脚本
# 用法: 在 docker-env 目录下执行  .\init-db.ps1
# ============================================================

$ErrorActionPreference = "Stop"
$containerName = "agri-monitor-mysql"
$rootPass = "agri_root_2026"
$dbName = "agriculture_db"
$charset = "utf8mb4"

# SQL 文件路径 (相对于 docker-env 目录)
$createTableSql = "..\src\main\resources\db\create_table.sql"
$initDataSql    = "..\src\main\resources\db\init_data.sql"

Write-Host "=== 1. 启动 Docker Compose ===" -ForegroundColor Cyan
docker-compose up -d

Write-Host ""
Write-Host "=== 2. 等待 MySQL 就绪 ===" -ForegroundColor Cyan
$maxRetries = 30
for ($i = 1; $i -le $maxRetries; $i++) {
    $health = docker inspect --format='{{.State.Health.Status}}' $containerName 2>$null
    if ($health -eq "healthy") {
        Write-Host "MySQL 已就绪" -ForegroundColor Green
        break
    }
    if ($i -eq $maxRetries) {
        Write-Host "MySQL 启动超时，请检查 docker logs $containerName" -ForegroundColor Red
        exit 1
    }
    Write-Host "  等待中... ($i/$maxRetries)"
    Start-Sleep -Seconds 2
}

Write-Host ""
Write-Host "=== 3. 导入建表脚本 (character-set=$charset) ===" -ForegroundColor Cyan
Get-Content $createTableSql -Encoding UTF8 | docker exec -i $containerName `
    mysql -uroot -p"$rootPass" --default-character-set=$charset $dbName
if ($LASTEXITCODE -ne 0) {
    Write-Host "建表脚本导入失败!" -ForegroundColor Red
    exit 1
}
Write-Host "建表完成" -ForegroundColor Green

Write-Host ""
Write-Host "=== 4. 导入初始数据 (character-set=$charset) ===" -ForegroundColor Cyan
Get-Content $initDataSql -Encoding UTF8 | docker exec -i $containerName `
    mysql -uroot -p"$rootPass" --default-character-set=$charset $dbName
if ($LASTEXITCODE -ne 0) {
    Write-Host "初始数据导入失败!" -ForegroundColor Red
    exit 1
}
Write-Host "数据导入完成" -ForegroundColor Green

Write-Host ""
Write-Host "=== 5. 验证数据编码 ===" -ForegroundColor Cyan
docker exec $containerName `
    mysql -uroot -p"$rootPass" --default-character-set=$charset $dbName `
    -e "SELECT id, name FROM inspection_plan LIMIT 3;"

Write-Host ""
Write-Host "=== 初始化完成 ===" -ForegroundColor Green
Write-Host "  MySQL:       localhost:3306"
Write-Host "  Redis:       localhost:6379"
Write-Host "  RabbitMQ:    localhost:15672"
Write-Host "  ES:          localhost:9200"
Write-Host "  MinIO:       localhost:9001"
Write-Host "  后端:        localhost:8080"
Write-Host "  前端:        localhost:3000"
