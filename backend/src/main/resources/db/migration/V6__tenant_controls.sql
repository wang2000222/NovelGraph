-- 团队设置与双方确认的所有权转移；不改变已存在的故事事实。
ALTER TABLE saas_tenant ADD COLUMN member_can_create TINYINT NOT NULL DEFAULT 0 COMMENT '普通成员是否允许创建项目';
ALTER TABLE saas_tenant ADD COLUMN pending_owner_id BIGINT NULL COMMENT '待确认的新所有者用户ID';
ALTER TABLE saas_tenant ADD COLUMN pending_owner_at DATETIME(3) NULL COMMENT '所有权转移申请时间';
