-- ========================================
-- 智慧农业病虫害监测系统 - 建表脚本
-- 版本: V2.0 (匹配接口文档)
-- ========================================

CREATE DATABASE IF NOT EXISTS agriculture_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE agriculture_db;

-- ========================================
-- 1. 用户模块
-- ========================================

-- 用户表
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
    id            VARCHAR(36) PRIMARY KEY COMMENT '用户UUID',
    username      VARCHAR(64) NOT NULL UNIQUE COMMENT '用户名',
    password      VARCHAR(128) NOT NULL COMMENT '密码(BCrypt)',
    name          VARCHAR(64) COMMENT '真实姓名',
    role          VARCHAR(20) NOT NULL DEFAULT 'VISITOR' COMMENT '角色: ADMIN/EXPERT/MANAGER/VISITOR',
    phone         VARCHAR(20) COMMENT '手机号',
    email         VARCHAR(128) COMMENT '邮箱',
    avatar        VARCHAR(255) COMMENT '用户头像路径',
    company_id    VARCHAR(36) COMMENT '所属企业ID',
    approved      TINYINT DEFAULT 0 COMMENT '是否已通过审批加入企业: 0=否 1=是',
    status        VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/DISABLED',
    last_login_at DATETIME COMMENT '最后登录时间',
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_role (role),
    INDEX idx_status (status),
    INDEX idx_company (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';


-- ========================================
-- 1.5 企业/租户模块
-- ========================================

-- 企业表
DROP TABLE IF EXISTS company;
CREATE TABLE company (
    id            VARCHAR(36) PRIMARY KEY COMMENT '企业UUID',
    name          VARCHAR(128) NOT NULL COMMENT '企业名称',
    invite_code   VARCHAR(32) NOT NULL UNIQUE COMMENT '邀请码',
    expire_at     DATETIME COMMENT '邀请码过期时间',
    member_limit  INT DEFAULT 50 COMMENT '成员上限',
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_invite_code (invite_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='企业表';


-- ========================================
-- 2. 摄像头模块
-- ========================================

-- 摄像头表
DROP TABLE IF EXISTS camera;
CREATE TABLE camera (
    id                  VARCHAR(36) PRIMARY KEY COMMENT '摄像头UUID',
    name                VARCHAR(128) NOT NULL COMMENT '摄像头名称',
    rtsp_url            VARCHAR(512) COMMENT 'RTSP主码流地址',
    rtsp_url_sub        VARCHAR(512) DEFAULT NULL COMMENT 'RTSP子码流地址',
    location_x          DECIMAL(10,6) COMMENT '经度',
    location_y          DECIMAL(10,6) COMMENT '纬度',
    direction           DECIMAL(5,1) COMMENT '朝向角度',
    capture_resolution  VARCHAR(20) DEFAULT NULL COMMENT '抓拍分辨率（为空则使用源流分辨率）',
    capture_quality     INT DEFAULT 85 COMMENT '抓拍JPEG质量(1-100)',
    reconnect_interval  INT DEFAULT 30 COMMENT '断流重连间隔(秒)',
    status              VARCHAR(20) DEFAULT 'OFFLINE' COMMENT '状态: ONLINE/OFFLINE/FAULT',
    last_frame_at       DATETIME COMMENT '最后抓拍时间',
    last_online_at      DATETIME DEFAULT NULL COMMENT '最近一次在线时间',
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT DEFAULT 0 COMMENT '逻辑删除'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='摄像头表';


-- ========================================
-- 3. 温室管理模块
-- ========================================

-- 温室/大棚表
DROP TABLE IF EXISTS greenhouse;
CREATE TABLE greenhouse (
    id            VARCHAR(36) PRIMARY KEY COMMENT '温室UUID',
    sector_id     VARCHAR(32) NOT NULL COMMENT '区域编号(如GH-A1)',
    crop_species  VARCHAR(128) COMMENT '作物种类(拉丁学名或中文名)',
    planting_date DATE COMMENT '定植日期',
    location      VARCHAR(64) COMMENT '地理位置坐标',
    area          DECIMAL(10,2) COMMENT '面积(m²)',
    status        VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/INACTIVE/MAINTENANCE',
    company_id    VARCHAR(36) COMMENT '所属企业ID',
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_sector_id (sector_id),
    INDEX idx_status (status),
    INDEX idx_company (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='温室/大棚表';


-- ========================================
-- 4. 网格区域模块
-- ========================================

-- 网格/地块表
DROP TABLE IF EXISTS grid;
CREATE TABLE grid (
    id              VARCHAR(36) PRIMARY KEY COMMENT '网格UUID',
    label           VARCHAR(32) NOT NULL COMMENT '网格编号(A1/B3等)',
    greenhouse_id   VARCHAR(36) COMMENT '大棚ID',
    polygon_coords  JSON COMMENT '多边形坐标点',
    area_m2         DECIMAL(10,2) COMMENT '面积(平方米)',
    crop_type       VARCHAR(64) COMMENT '作物类型',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_label (label)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='网格区域表';


-- 摄像头覆盖网格关联表
DROP TABLE IF EXISTS camera_grid;
CREATE TABLE camera_grid (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    camera_id   VARCHAR(36) NOT NULL COMMENT '摄像头ID',
    grid_id     VARCHAR(36) NOT NULL COMMENT '网格ID',
    UNIQUE KEY uk_camera_grid (camera_id, grid_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='摄像头覆盖网格关联';


-- ========================================
-- 4. 病虫害知识库
-- ========================================

-- 病害信息表 (id 对应 YOLOv8 病害模型 class index, 0-37)
DROP TABLE IF EXISTS disease_info;
CREATE TABLE disease_info (
    id            INT PRIMARY KEY COMMENT '病害ID(模型class index)',
    disease_name  VARCHAR(128) NOT NULL COMMENT '病害名称(英文)',
    name_cn       VARCHAR(128) COMMENT '病害名称(中文)',
    description   TEXT COMMENT '描述',
    conditions    TEXT COMMENT '发生条件',
    prevention    TEXT COMMENT '防治方法',
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT DEFAULT 0 COMMENT '逻辑删除'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='病害信息表';


-- 虫害信息表 (id 对应 YOLOv8 虫害模型 class index, 0-101)
DROP TABLE IF EXISTS pest_info;
CREATE TABLE pest_info (
    id          INT PRIMARY KEY COMMENT '虫害ID(模型class index)',
    pest_name   VARCHAR(128) NOT NULL COMMENT '虫害名称',
    description TEXT COMMENT '描述',
    conditions  TEXT COMMENT '发生条件',
    prevention  TEXT COMMENT '防治方法',
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT DEFAULT 0 COMMENT '逻辑删除'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='虫害信息表';


-- ========================================
-- 5. 上报与识别模块
-- ========================================

-- 图像上报记录表
DROP TABLE IF EXISTS report;
CREATE TABLE report (
    id            VARCHAR(36) PRIMARY KEY COMMENT '上报UUID',
    user_id       VARCHAR(36) NOT NULL COMMENT '上报用户ID',
    grid_id       VARCHAR(36) NOT NULL COMMENT '网格/地块ID',
    crop_type     VARCHAR(64) COMMENT '农作物品种',
    image_urls    JSON COMMENT '图片URL数组',
    found_at      DATETIME NOT NULL COMMENT '发现时间',
    description   VARCHAR(500) COMMENT '补充描述',
    status        VARCHAR(30) DEFAULT 'PENDING_RECOGNITION' COMMENT '状态: PENDING_RECOGNITION/PENDING/AUDITED/REJECTED',
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_user (user_id),
    INDEX idx_grid (grid_id),
    INDEX idx_status (status),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图像上报记录表';


-- 识别结果表 (一行对应一张图片的一次推理，包含多种病害+虫害检出)
DROP TABLE IF EXISTS inference;
CREATE TABLE inference (
    id                  VARCHAR(36) PRIMARY KEY COMMENT '识别UUID',
    report_id           VARCHAR(36) NOT NULL COMMENT '关联上报ID',
    company_id          VARCHAR(36) COMMENT '所属企业ID',
    disease_ids         JSON COMMENT '病害ID数组 [0,3,15]，对应 disease_info.id',
    pest_ids            JSON COMMENT '虫害ID数组 [22,45]，对应 pest_info.id',
    detections          JSON COMMENT '完整检测结果数组(含class_id/class_name/name_cn/confidence/bbox/pipeline)',
    annotated_image_url VARCHAR(512) COMMENT '标注图存储路径/URL',
    total_elapsed_ms    DECIMAL(10,2) COMMENT '双模型总推理耗时(ms)',
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_report (report_id),
    INDEX idx_company (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='识别结果表';


-- 审核记录表
DROP TABLE IF EXISTS audit_record;
CREATE TABLE audit_record (
    id            VARCHAR(36) PRIMARY KEY COMMENT '审核UUID',
    report_id     VARCHAR(36) NOT NULL COMMENT '关联上报ID',
    auditor_id    VARCHAR(36) NOT NULL COMMENT '审核人ID',
    audit_result  VARCHAR(20) NOT NULL COMMENT '审核结果: APPROVED/REJECTED',
    comment       VARCHAR(500) COMMENT '审核意见',
    audited_at    DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '审核时间',
    INDEX idx_report (report_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审核记录表';


-- ========================================
-- 6. 防治方案模块
-- ========================================

-- 防治方案表
DROP TABLE IF EXISTS prevention_plan;
CREATE TABLE prevention_plan (
    id            VARCHAR(36) PRIMARY KEY COMMENT '方案UUID',
    report_id     VARCHAR(36) NOT NULL COMMENT '关联上报ID',
    content       TEXT NOT NULL COMMENT '方案内容',
    suggest_time  DATE COMMENT '建议执行时间',
    author_id     VARCHAR(36) COMMENT '制定人ID',
    version       INT DEFAULT 1 COMMENT '版本号',
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_report (report_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='防治方案表';


-- 防治方案版本历史表
DROP TABLE IF EXISTS prevention_plan_version;
CREATE TABLE prevention_plan_version (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    plan_id       VARCHAR(36) NOT NULL COMMENT '方案ID',
    content       TEXT NOT NULL COMMENT '方案内容',
    suggest_time  DATE COMMENT '建议执行时间',
    version       INT NOT NULL COMMENT '版本号',
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_plan (plan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='防治方案版本历史';


-- ========================================
-- 7. 工单模块
-- ========================================

-- 工单表
DROP TABLE IF EXISTS work_order;
CREATE TABLE work_order (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '工单ID',
    title           VARCHAR(256) NOT NULL COMMENT '工单标题',
    severity        VARCHAR(20) NOT NULL COMMENT '严重程度: LOW/MEDIUM/HIGH/CRITICAL',
    status          VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态: PENDING/PROCESSING/DONE/IGNORED/ESCALATED',
    type            VARCHAR(20) COMMENT '类型: disease/pest',
    grid_label      VARCHAR(10) COMMENT '关联网格编号(如A1/B3)',
    pest_name       VARCHAR(50) COMMENT '病虫害名称',
    confidence      DECIMAL(3,2) COMMENT '检测置信度(0.00-1.00)',
    inference_id    VARCHAR(36) COMMENT '关联识别ID',
    assigned_to     VARCHAR(36) COMMENT '指派给用户ID',
    created_by      VARCHAR(36) COMMENT '创建人ID',
    expert_comment  VARCHAR(500) COMMENT '专家备注',
    callback_token  VARCHAR(128) COMMENT '回调Token',
    token_expire_at DATETIME COMMENT 'Token过期时间',
    token_used      TINYINT DEFAULT 0 COMMENT 'Token是否已使用',
    company_id      VARCHAR(36) COMMENT '所属企业ID',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_status (status),
    INDEX idx_severity (severity),
    INDEX idx_assigned (assigned_to),
    INDEX idx_token (callback_token),
    INDEX idx_company (company_id),
    INDEX idx_grid (grid_label)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单表';


-- 工单状态历史表
DROP TABLE IF EXISTS work_order_history;
CREATE TABLE work_order_history (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    workorder_id  BIGINT NOT NULL COMMENT '工单ID',
    status        VARCHAR(20) NOT NULL COMMENT '状态',
    operator_id   VARCHAR(36) COMMENT '操作人ID',
    operator_name VARCHAR(64) COMMENT '操作人名称',
    comment       VARCHAR(500) COMMENT '备注',
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_workorder (workorder_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单状态历史';


-- ========================================
-- 8. 巡检模块
-- ========================================

-- 巡检计划表
DROP TABLE IF EXISTS inspection_plan;
CREATE TABLE inspection_plan (
    id                  VARCHAR(36) PRIMARY KEY COMMENT '计划UUID',
    name                VARCHAR(128) NOT NULL COMMENT '计划名称',
    cron_expression     VARCHAR(64) COMMENT 'Cron表达式',
    active_hours_start  VARCHAR(10) COMMENT '生效开始时间(HH:mm)',
    active_hours_end    VARCHAR(10) COMMENT '生效结束时间(HH:mm)',
    is_active           TINYINT DEFAULT 1 COMMENT '是否启用',
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='巡检计划表';


-- 巡检计划-摄像头关联表
DROP TABLE IF EXISTS inspection_camera;
CREATE TABLE inspection_camera (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    plan_id     VARCHAR(36) NOT NULL COMMENT '计划ID',
    camera_id   VARCHAR(36) NOT NULL COMMENT '摄像头ID',
    UNIQUE KEY uk_plan_camera (plan_id, camera_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='巡检计划摄像头关联';


-- 巡检日志表
DROP TABLE IF EXISTS inspection_log;
CREATE TABLE inspection_log (
    id              VARCHAR(36) PRIMARY KEY COMMENT '日志UUID',
    plan_id         VARCHAR(36) COMMENT '计划ID',
    camera_id       VARCHAR(36) NOT NULL COMMENT '摄像头ID',
    capture_time    DATETIME NOT NULL COMMENT '抓拍时间',
    image_url       VARCHAR(512) COMMENT '图片URL',
    disease_count   INT DEFAULT 0 COMMENT '病害数量',
    pest_count      INT DEFAULT 0 COMMENT '虫害数量',
    max_confidence  DECIMAL(5,4) COMMENT '最高置信度',
    duration_ms     INT COMMENT '耗时(毫秒)',
    status          VARCHAR(20) DEFAULT 'SUCCESS' COMMENT '状态: SUCCESS/FAILED',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_camera (camera_id),
    INDEX idx_capture_time (capture_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='巡检日志表';


-- ========================================
-- 9. 日度报告模块
-- ========================================

-- 日度报告表
DROP TABLE IF EXISTS daily_report;
CREATE TABLE daily_report (
    id              VARCHAR(36) PRIMARY KEY COMMENT '报告UUID',
    report_date     DATE NOT NULL COMMENT '报告日期',
    detections      INT DEFAULT 0 COMMENT '识别总次数',
    disease_count   INT DEFAULT 0 COMMENT '病害次数',
    pest_count      INT DEFAULT 0 COMMENT '虫害次数',
    handled_rate    DECIMAL(3,2) DEFAULT 0.00 COMMENT '处理率(0.00-1.00)',
    greenhouse_id   VARCHAR(36) COMMENT '关联温室ID',
    summary_json    JSON COMMENT '统计数据JSON',
    html_content    LONGTEXT COMMENT '报告HTML内容(AI生成)',
    email_sent      TINYINT DEFAULT 0 COMMENT '是否已发送邮件',
    email_sent_at   DATETIME COMMENT '邮件发送时间',
    company_id      VARCHAR(36) COMMENT '所属企业ID',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_date (report_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日度报告表';


-- ========================================
-- 10. 农业大脑对话模块
-- ========================================

-- 农业大脑对话表
DROP TABLE IF EXISTS ai_conversation;
CREATE TABLE ai_conversation (
    id          VARCHAR(36) PRIMARY KEY COMMENT '对话UUID',
    user_id     VARCHAR(36) NOT NULL COMMENT '用户ID',
    title       VARCHAR(256) COMMENT '对话标题',
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='农业大脑对话表';


-- 农业大脑消息表
DROP TABLE IF EXISTS ai_message;
CREATE TABLE ai_message (
    id              VARCHAR(36) PRIMARY KEY COMMENT '消息UUID',
    conversation_id VARCHAR(36) NOT NULL COMMENT '对话ID',
    user_id         VARCHAR(36) NOT NULL COMMENT '用户ID',
    role            VARCHAR(20) NOT NULL COMMENT '角色: USER/ASSISTANT',
    content         TEXT NOT NULL COMMENT '消息内容',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_conversation (conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='农业大脑消息表';


-- 农业大脑配置表
DROP TABLE IF EXISTS ai_config;
CREATE TABLE ai_config (
    id           VARCHAR(36) PRIMARY KEY COMMENT '配置UUID',
    config_key   VARCHAR(50) NOT NULL UNIQUE COMMENT '配置键: apiKey/model/provider',
    config_value TEXT COMMENT '配置值',
    updated_at   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='农业大脑配置表';


-- ========================================
-- 11. 系统日志模块
-- ========================================

-- 操作日志表
DROP TABLE IF EXISTS sys_log;
CREATE TABLE sys_log (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '日志ID',
    user_id     VARCHAR(36) COMMENT '操作用户ID',
    username    VARCHAR(64) COMMENT '用户名',
    operation   VARCHAR(64) COMMENT '操作类型',
    method      VARCHAR(256) COMMENT '请求方法',
    params      TEXT COMMENT '请求参数',
    ip          VARCHAR(64) COMMENT 'IP地址',
    duration    BIGINT COMMENT '执行时长(ms)',
    status      TINYINT DEFAULT 1 COMMENT '状态: 0=失败 1=成功',
    error_msg   TEXT COMMENT '错误信息',
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_user (user_id),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';


-- ========================================
-- 12. 环境数据模块
-- ========================================

-- 环境数据记录表
DROP TABLE IF EXISTS environment_record;
CREATE TABLE environment_record (
    id              VARCHAR(36) PRIMARY KEY COMMENT '记录UUID',
    greenhouse_id   VARCHAR(36) NOT NULL COMMENT '温室ID',
    company_id      VARCHAR(36) COMMENT '所属企业ID',
    air_temp        DECIMAL(5,2) COMMENT '空气温度(°C)',
    soil_moisture   DECIMAL(5,2) COMMENT '土壤湿度(%)',
    humidity        DECIMAL(5,2) COMMENT '空气湿度(%)',
    light_level     DECIMAL(10,2) COMMENT '光照强度(lux)',
    co2             DECIMAL(8,2) COMMENT 'CO₂浓度(ppm)',
    soil_ph         DECIMAL(4,2) COMMENT '土壤pH值',
    ec              DECIMAL(6,2) COMMENT 'EC电导率(mS/cm)',
    nitrogen        DECIMAL(8,2) COMMENT '氮含量(mg/kg)',
    phosphorus      DECIMAL(8,2) COMMENT '磷含量(mg/kg)',
    potassium       DECIMAL(8,2) COMMENT '钾含量(mg/kg)',
    energy_current  DECIMAL(10,2) COMMENT '当前功耗(Kw)',
    energy_max      DECIMAL(10,2) COMMENT '最大负载(Kw)',
    recorded_at     DATETIME NOT NULL COMMENT '数据采集时间',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    deleted         TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_greenhouse_time (greenhouse_id, recorded_at),
    INDEX idx_company (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='环境数据记录表';
