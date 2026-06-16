-- 摄像头自动检测结果持久化支持
-- 1. report_id 改为可为空（摄像头检测无关联上报）
-- 2. 新增 source_type / camera_id / grid_labels 字段

ALTER TABLE inference
    MODIFY COLUMN report_id VARCHAR(36) NULL COMMENT '关联上报ID（摄像头自动检测时为空）';

ALTER TABLE inference
    ADD COLUMN source_type VARCHAR(20) NOT NULL DEFAULT 'REPORT' COMMENT '数据来源: REPORT(用户上报) / CAMERA(摄像头自动检测)' AFTER total_elapsed_ms,
    ADD COLUMN camera_id   VARCHAR(64) NULL COMMENT '来源摄像头ID（仅 source_type=CAMERA 时有值）' AFTER source_type,
    ADD COLUMN grid_labels  VARCHAR(255) NULL COMMENT '关联网格标签，逗号分隔' AFTER camera_id;

ALTER TABLE inference
    ADD INDEX idx_source_type (source_type),
    ADD INDEX idx_camera_id (camera_id);
