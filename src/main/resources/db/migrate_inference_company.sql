-- ========================================
-- 迁移脚本：为 inference 表添加 company_id 字段
-- 执行顺序：先 ALTER TABLE，再 UPDATE 回填数据
-- ========================================

-- 1. 添加 company_id 字段
ALTER TABLE inference
ADD COLUMN company_id VARCHAR(36) COMMENT '所属企业ID' AFTER report_id,
ADD INDEX idx_company (company_id);

-- 2. 回填现有数据：通过 Report → Grid → Greenhouse 关联查询
UPDATE inference i
INNER JOIN report r ON i.report_id = r.id
INNER JOIN grid g ON r.grid_id = g.id
INNER JOIN greenhouse gh ON g.greenhouse_id = gh.id
SET i.company_id = gh.company_id
WHERE i.company_id IS NULL;
