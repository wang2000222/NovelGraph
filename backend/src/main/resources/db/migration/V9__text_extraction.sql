-- 文本提取的剧情任务和幂等导入记录；随项目删除级联清理。
CREATE TABLE ng_task (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT '所属租户',
  project_id BIGINT NOT NULL COMMENT '所属项目',
  character_id BIGINT NULL COMMENT '执行人物，可为空',
  event_id BIGINT NULL COMMENT '关联事件，可为空',
  name VARCHAR(200) NOT NULL COMMENT '名称',
  description LONGTEXT NULL COMMENT '公开描述',
  reveal_chapter INT NULL COMMENT '读者揭示章节',
  visibility VARCHAR(20) NOT NULL DEFAULT 'AUTHOR_ONLY' COMMENT '可见边界',
  data LONGTEXT NOT NULL COMMENT '该实体扩展属性JSON',
  revision BIGINT NOT NULL DEFAULT 0 COMMENT '资料版本',
  deleted INT NOT NULL DEFAULT 0 COMMENT '软删除',
  created_by BIGINT NOT NULL COMMENT '创建者',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  KEY idx_ng_task_scope(tenant_id,project_id,deleted), CONSTRAINT fk_ng_task_project FOREIGN KEY(project_id) REFERENCES ng_project(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='剧情任务与目标';

CREATE TABLE ng_extraction_batch (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '导入批次主键',
 tenant_id BIGINT NOT NULL COMMENT '所属租户',
 project_id BIGINT NOT NULL COMMENT '所属小说项目',
 request_key VARCHAR(64) NOT NULL COMMENT 'AI回答中的批次编号，用于防止重复导入',
 payload_hash VARCHAR(64) NOT NULL COMMENT '规范化回答的SHA256摘要',
 result_json TEXT NOT NULL COMMENT '导入数量及结果，不保存原始小说文本',
 created_by BIGINT NOT NULL COMMENT '操作用户',
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '提交时间',
 UNIQUE KEY uk_extraction_request(project_id,request_key),
 CONSTRAINT fk_extraction_project FOREIGN KEY(project_id) REFERENCES ng_project(id) ON DELETE CASCADE
) COMMENT='小说文本提取导入批次';
