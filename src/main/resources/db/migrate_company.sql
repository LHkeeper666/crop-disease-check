ALTER TABLE sys_user
ADD COLUMN avatar VARCHAR(255) COMMENT '用户头像路径' AFTER email,
ADD COLUMN company_id VARCHAR(36) COMMENT '所属企业ID' AFTER avatar,
ADD COLUMN approved TINYINT DEFAULT 0 COMMENT '是否已通过审批加入企业' AFTER company_id,
ADD INDEX idx_company (company_id);

CREATE TABLE IF NOT EXISTS company (
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
