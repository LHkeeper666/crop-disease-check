# TreeForge 后端开发 TODO

> 基于前端现有功能提取的完整 API 接口与数据库字段需求，后端成员可按此文档逐一实现。

---

## 一、数据库表设计

### 1. 用户表 `users`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(36) PK | 用户ID |
| username | VARCHAR(50) UNIQUE | 登录用户名 |
| password_hash | VARCHAR(255) | 密码哈希 |
| name | VARCHAR(50) | 显示姓名 |
| role | ENUM('ADMIN','EXPERT','MANAGER','VISITOR') | 角色 |
| phone | VARCHAR(20) | 手机号 |
| email | VARCHAR(100) | 邮箱 |
| avatar | VARCHAR(255) | 头像URL |
| company_id | VARCHAR(36) FK → companies.id | 所属企业 |
| approved | BOOLEAN DEFAULT false | 是否审核通过 |
| status | ENUM('ACTIVE','DISABLED') DEFAULT 'ACTIVE' | 账号状态 |
| last_login_at | DATETIME | 最后登录时间 |
| created_at | DATETIME | 注册时间 |
| updated_at | DATETIME | 更新时间 |

### 2. 企业表 `companies`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(36) PK | 企业ID |
| name | VARCHAR(100) | 企业名称 |
| invite_code | VARCHAR(20) UNIQUE | 邀请码 |
| created_at | DATETIME | 创建时间 |

### 3. 摄像头表 `cameras`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(36) PK | 摄像头ID |
| name | VARCHAR(50) | 摄像头名称 |
| status | ENUM('ONLINE','OFFLINE','FAULT') | 状态 |
| grid_labels | VARCHAR(100) | 覆盖网格（逗号分隔，如 "A1,A2,A3"） |
| rtsp_url | VARCHAR(255) | RTSP流地址 |
| company_id | VARCHAR(36) FK → companies.id | 所属企业 |
| created_at | DATETIME | 创建时间 |

### 4. 网格区域表 `grids`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(36) PK | 网格ID |
| label | VARCHAR(10) | 网格编号（A1, B3等） |
| greenhouse_id | VARCHAR(36) FK → greenhouses.id | 所属温室 |
| score | DECIMAL(3,2) | 当前风险评分 0.00-1.00 |
| company_id | VARCHAR(36) FK → companies.id | 所属企业 |

### 5. 温室表 `greenhouses`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(36) PK | 温室ID |
| sector_id | VARCHAR(20) | 区域编号（如 GH-A1） |
| crop_species | VARCHAR(100) | 作物种类（拉丁学名） |
| planting_date | DATE | 定植日期 |
| location | VARCHAR(50) | 经纬度坐标 |
| area | VARCHAR(20) | 面积 |
| status | ENUM('ACTIVE','MAINTENANCE','INACTIVE') | 状态 |
| company_id | VARCHAR(36) FK → companies.id | 所属企业 |

### 6. 工单表 `work_orders`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(36) PK | 工单ID |
| title | VARCHAR(200) | 工单标题 |
| severity | ENUM('CRITICAL','HIGH','MEDIUM','LOW') | 严重程度 |
| status | ENUM('PENDING','PROCESSING','DONE','IGNORED') | 处理状态 |
| type | ENUM('disease','pest') | 类型（病害/虫害） |
| grid_label | VARCHAR(10) | 关联网格 |
| pest_name | VARCHAR(50) | 病虫害名称 |
| confidence | DECIMAL(3,2) | 检测置信度 0.00-1.00 |
| assigned_to | VARCHAR(36) FK → users.id | 指派人 |
| created_by | VARCHAR(36) FK → users.id | 创建人 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |
| company_id | VARCHAR(36) FK → companies.id | 所属企业 |

### 7. 环境监测表 `environment_data`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(36) PK | 记录ID |
| greenhouse_id | VARCHAR(36) FK → greenhouses.id | 关联温室 |
| air_temp | DECIMAL(5,1) | 空气温度 °C |
| soil_moisture | DECIMAL(5,1) | 土壤湿度 % |
| humidity | DECIMAL(5,1) | 空气湿度 % |
| light_level | INT | 光照强度 lux |
| co2 | INT | CO₂浓度 ppm |
| soil_ph | DECIMAL(3,1) | 土壤pH |
| ec | DECIMAL(4,2) | 电导率 mS/cm |
| nitrogen | INT | 氮 mg/kg |
| phosphorus | INT | 磷 mg/kg |
| potassium | INT | 钾 mg/kg |
| energy_current | DECIMAL(6,2) | 当前功耗 Kw |
| energy_max | DECIMAL(6,2) | 最大负载 Kw |
| recorded_at | DATETIME | 记录时间 |
| company_id | VARCHAR(36) FK → companies.id | 所属企业 |

### 8. 日报表 `daily_reports`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(36) PK | 报告ID |
| report_date | DATE | 报告日期 |
| detections | INT | 识别总次数 |
| disease_count | INT | 病害次数 |
| pest_count | INT | 虫害次数 |
| handled_rate | DECIMAL(3,2) | 处理率 |
| summary | TEXT | AI生成的报告摘要 |
| greenhouse_id | VARCHAR(36) FK → greenhouses.id | 关联温室 |
| company_id | VARCHAR(36) FK → companies.id | 所属企业 |
| created_at | DATETIME | 创建时间 |

### 9. AI会话表 `agent_sessions`（可选）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(36) PK | 会话ID |
| user_id | VARCHAR(36) FK → users.id | 用户 |
| provider | VARCHAR(20) | 供应商（deepseek/xiaomi-mimo） |
| model | VARCHAR(50) | 模型名 |
| created_at | DATETIME | 创建时间 |

### 10. AI消息表 `agent_messages`（可选）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(36) PK | 消息ID |
| session_id | VARCHAR(36) FK → agent_sessions.id | 关联会话 |
| role | ENUM('user','assistant','system') | 角色 |
| content | TEXT | 消息内容 |
| created_at | DATETIME | 创建时间 |

---

## 二、API 接口清单

### 认证模块 `/api/auth`

| 方法 | 路径 | 说明 | 请求体 | 响应 |
|------|------|------|--------|------|
| POST | `/api/auth/login` | 登录 | `{ username, password }` | `{ token, user: UserInfo, pending: bool }` |
| POST | `/api/auth/register` | 注册 | `{ email, username, password }` | `{ success, message }` |
| POST | `/api/auth/send-otp` | 发送邮箱验证码 | `{ email }` | `{ success }` |
| POST | `/api/auth/verify-otp` | 验证邮箱验证码 | `{ email, code }` | `{ success }` |
| GET  | `/api/auth/me` | 获取当前用户信息 | — | `{ user: UserInfo }` |
| POST | `/api/auth/logout` | 登出 | — | `{ success }` |

### 企业模块 `/api/companies`

| 方法 | 路径 | 说明 | 请求体 | 响应 |
|------|------|------|--------|------|
| POST | `/api/companies/join` | 通过邀请码加入企业 | `{ inviteCode }` | `{ success, companyName, message }` |
| GET  | `/api/companies/:id` | 获取企业信息 | — | `{ company }` |

### 用户管理 `/api/users`

| 方法 | 路径 | 说明 | 请求体 | 响应 |
|------|------|------|--------|------|
| GET  | `/api/users` | 获取企业下所有用户 | query: `?search=xxx` | `{ users: UserInfo[] }` |
| GET  | `/api/users/pending` | 获取待审核用户 | — | `{ users: UserInfo[] }` |
| PUT  | `/api/users/:id` | 编辑用户 | `{ name?, role?, phone?, email? }` | `{ user }` |
| PUT  | `/api/users/:id/status` | 启用/禁用用户 | `{ approved: bool }` | `{ success }` |
| POST | `/api/users/:id/reset-password` | 重置密码 | — | `{ success, tempPassword }` |
| PUT  | `/api/users/:id/approve` | 审核通过并分配角色 | `{ role }` | `{ user }` |

### 摄像头管理 `/api/cameras`

| 方法 | 路径 | 说明 | 请求体 | 响应 |
|------|------|------|--------|------|
| GET  | `/api/cameras` | 获取摄像头列表 | query: `?search=xxx` | `{ cameras }` |
| POST | `/api/cameras` | 新增摄像头 | `{ name, gridLabels, rtspUrl }` | `{ camera }` |
| PUT  | `/api/cameras/:id` | 编辑摄像头 | `{ name?, gridLabels?, rtspUrl?, status? }` | `{ camera }` |
| DELETE | `/api/cameras/:id` | 删除摄像头 | — | `{ success }` |

### 网格区域 `/api/grids`

| 方法 | 路径 | 说明 | 请求体 | 响应 |
|------|------|------|--------|------|
| GET  | `/api/grids` | 获取网格列表（含风险评分） | — | `{ grids }` |
| GET  | `/api/grids/:label` | 获取单个网格详情 | — | `{ grid, recentOrders, environmentData }` |

### 温室管理 `/api/greenhouses`

| 方法 | 路径 | 说明 | 请求体 | 响应 |
|------|------|------|--------|------|
| GET  | `/api/greenhouses` | 获取温室列表 | — | `{ greenhouses }` |
| GET  | `/api/greenhouses/:id` | 获取温室详情（含元数据） | — | `{ greenhouse }` |

### 环境数据 `/api/environment`

| 方法 | 路径 | 说明 | 请求体 | 响应 |
|------|------|------|--------|------|
| GET  | `/api/environment/current` | 获取当前环境数据 | query: `?greenhouseId=xxx` | `{ airTemp, soilMoisture, humidity, lightLevel, co2, soilPh, ec, n, p, k, energyCurrent, energyMax }` |
| GET  | `/api/environment/history` | 获取历史环境数据 | query: `?greenhouseId=xxx&from=xxx&to=xxx` | `{ records[] }` |

### 工单管理 `/api/workorders`

| 方法 | 路径 | 说明 | 请求体 | 响应 |
|------|------|------|--------|------|
| GET  | `/api/workorders` | 获取工单列表 | query: `?status=xxx&severity=xxx&search=xxx` | `{ workorders }` |
| GET  | `/api/workorders/:id` | 获取工单详情 | — | `{ workorder }` |
| POST | `/api/workorders` | 创建工单 | `{ title, gridLabel, pestName, type, severity, assignedTo, confidence }` | `{ workorder }` |
| PUT  | `/api/workorders/:id/status` | 更新工单状态 | `{ status }` | `{ workorder }` |
| PUT  | `/api/workorders/:id/severity` | 调整工单严重程度 | `{ severity }` | `{ workorder }` |
| DELETE | `/api/workorders/:id` | 删除工单 | — | `{ success }` |

### 报表统计 `/api/reports`

| 方法 | 路径 | 说明 | 请求体 | 响应 |
|------|------|------|--------|------|
| GET  | `/api/reports/daily` | 获取日报列表 | query: `?page=1&size=10` | `{ reports, total }` |
| GET  | `/api/reports/daily/:id` | 获取日报详情 | — | `{ report }` |
| POST | `/api/reports/generate` | 生成今日日报 | — | `{ report }` |
| GET  | `/api/reports/stats` | 获取统计数据概览 | — | `{ totalReports, todayReports, pendingAudit, processed, highRiskAlerts, diseaseDistribution[], pestDistribution[], dailyTrend[], top5Diseases[], top5Pests[] }` |
| GET  | `/api/reports/export` | 导出Excel | query: `?from=xxx&to=xxx` | 文件流 |

### AI Agent `/api/agent`（可选，前端可直连供应商API）

| 方法 | 路径 | 说明 | 请求体 | 响应 |
|------|------|------|--------|------|
| POST | `/api/agent/chat` | 转发AI对话 | `{ provider, model, messages[] }` | `{ content }` |
| POST | `/api/agent/report` | AI生成日报 | `{ greenhouseId }` | `{ report }` |

---

## 三、接口字段对照表（前端实际使用）

### 登录响应 `UserInfo`
```
前端字段: id, username, name, role, phone, email, avatar, companyId, approved
后端映射: users 表对应字段
```

### 工单 `WorkOrder`
```
前端字段: id, title, severity, status, type, gridLabel, pestName, confidence, assignedToName, createdAt, updatedAt
后端映射: work_orders 表，assignedToName 需 JOIN users 表获取 name
```

### 环境数据 `mockEnvironmentData`
```
前端字段: airTemp.value, airTemp.unit, airTemp.status
          soilMoisture.value, soilMoisture.unit, soilMoisture.status
          humidity.value, humidity.unit, humidity.status
          lightLevel.value, lightLevel.unit, lightLevel.status
后端映射: environment_data 表最新一条记录，status 根据阈值计算：
  - airTemp: <10 或 >35 → warning, 正常 → normal
  - soilMoisture: <30 或 >80 → warning
  - humidity: <40 或 >85 → warning
  - lightLevel: <500 → warning
```

### 能耗数据 `mockEnergyData`
```
前端字段: current, max, unit, trend
后端映射: environment_data 表的 energy_current, energy_max
  - trend: 与上一条记录比较 (rising/stable/falling)
```

### 生长指标 `mockGrowthMetrics`
```
前端字段: label, value, unit, color
后端映射: environment_data 表的 co2, soil_ph, ec, air_temp, nitrogen, phosphorus, potassium
  - color 由前端根据值范围决定，后端不需要
```

### 温室元数据 `mockGreenhouseMeta`
```
前端字段: sectorId, cropSpecies, plantingDate, location, area, status
后端映射: greenhouses 表
```

### 网格热力图 `mockGridHeatmap`
```
前端字段: label, score, pest, type
后端映射: grids 表 + 该网格最新的活跃工单(work_orders)的 pestName, type
```

### 摄像头 `mockCameras`
```
前端字段: id, name, status, grid, rtspUrl
后端映射: cameras 表，grid 对应 grid_labels 字段
```

### 统计概览 `mockStatsOverview`
```
前端字段: totalReports, todayReports, pendingAudit, processed, highRiskAlerts
          diseaseDistribution[], pestDistribution[], dailyTrend[]
后端映射:
  - totalReports: SELECT COUNT(*) FROM daily_reports WHERE company_id = ?
  - todayReports: SELECT COUNT(*) FROM work_orders WHERE DATE(created_at) = CURDATE()
  - pendingAudit: SELECT COUNT(*) FROM work_orders WHERE status = 'PENDING'
  - processed: SELECT COUNT(*) FROM work_orders WHERE status = 'DONE'
  - highRiskAlerts: SELECT COUNT(*) FROM work_orders WHERE severity IN ('CRITICAL','HIGH') AND status IN ('PENDING','PROCESSING')
  - diseaseDistribution: SELECT pest_name, COUNT(*) FROM work_orders WHERE type='disease' GROUP BY pest_name
  - pestDistribution: SELECT pest_name, COUNT(*) FROM work_orders WHERE type='pest' GROUP BY pest_name
  - dailyTrend: SELECT DATE(created_at), COUNT(*) FROM work_orders WHERE created_at >= 7天前 GROUP BY DATE(created_at)
```

### 日报表 `mockDailyReports`
```
前端字段: id, date, detections, disease, pest, handledRate
后端映射: daily_reports 表
```

---

## 四、后端开发优先级

### P0 — 核心功能（必须先做）
1. 用户注册/登录（JWT认证）
2. 企业邀请码验证 + 加入企业
3. 用户管理（CRUD、角色、审核）
4. 工单 CRUD + 状态流转
5. 环境数据写入 + 查询最新值

### P1 — 数据展示
6. 网格列表 + 风险评分计算
7. 摄像头管理 CRUD
8. 温室元数据管理
9. 统计概览接口（各维度聚合查询）
10. 7日趋势数据

### P2 — 报表与导出
11. 日报生成（基于当日数据聚合）
12. Excel 导出

### P3 — AI 集成（可选，前端已直连）
13. Agent 代理接口（如果需要后端转发）

---

## 五、阈值参考（status 计算规则）

```python
# 环境数据状态阈值
AIR_TEMP_WARNING_LOW = 10      # °C
AIR_TEMP_WARNING_HIGH = 35     # °C
SOIL_MOISTURE_WARNING_LOW = 30 # %
SOIL_MOISTURE_WARNING_HIGH = 80 # %
HUMIDITY_WARNING_LOW = 40      # %
HUMIDITY_WARNING_HIGH = 85     # %
LIGHT_LEVEL_WARNING_LOW = 500  # lux

# 风险评分 → 状态色
RISK_HIGH = 0.8    # 红色 CRITICAL
RISK_MEDIUM = 0.5  # 橙色 HIGH
RISK_LOW = 0.0     # 绿色 MEDIUM/LOW
```

---

## 六、多租户隔离

所有查询都必须加 `company_id` 过滤：
- 用户只能看到自己企业下的数据
- 工单、摄像头、网格、温室、环境数据、报表全部按 `company_id` 隔离
- 管理员只能管理自己企业的用户

```sql
-- 示例：获取工单列表
SELECT * FROM work_orders
WHERE company_id = :current_user_company_id
  AND status = :filter_status
ORDER BY created_at DESC;
```
