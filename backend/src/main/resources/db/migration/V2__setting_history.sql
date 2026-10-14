-- 保留世界规则、人物、事件等设定的历史快照。
CREATE TABLE novel_setting_revision (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '修订记录主键',
  novel_id BIGINT NOT NULL COMMENT '所属小说项目',
  resource_type VARCHAR(50) NOT NULL COMMENT '设定资源类型',
  resource_id BIGINT NOT NULL COMMENT '设定记录ID',
  snapshot LONGTEXT NOT NULL COMMENT '修改前完整设定JSON快照',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '保存旧版本时间',
  KEY idx_setting_revision (novel_id,resource_type,resource_id),
  CONSTRAINT fk_setting_revision_novel FOREIGN KEY (novel_id) REFERENCES novel_project(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='小说设定修订历史';

ALTER TABLE chapter ADD CONSTRAINT fk_chapter_novel FOREIGN KEY (novel_id) REFERENCES novel_project(id);
ALTER TABLE chapter_version ADD CONSTRAINT fk_chapter_version_parent FOREIGN KEY (chapter_id) REFERENCES chapter(id);
ALTER TABLE ai_credential ADD CONSTRAINT fk_credential_provider FOREIGN KEY (provider_id) REFERENCES ai_provider(id);
ALTER TABLE ai_model ADD CONSTRAINT fk_model_credential FOREIGN KEY (credential_id) REFERENCES ai_credential(id);
ALTER TABLE workflow_version ADD CONSTRAINT fk_version_workflow FOREIGN KEY (workflow_id) REFERENCES workflow_definition(id);
ALTER TABLE workflow_run ADD CONSTRAINT fk_run_chapter FOREIGN KEY (chapter_id) REFERENCES chapter(id);
ALTER TABLE workflow_run ADD CONSTRAINT fk_run_version FOREIGN KEY (version_id) REFERENCES workflow_version(id);
ALTER TABLE node_run ADD CONSTRAINT fk_node_run_parent FOREIGN KEY (run_id) REFERENCES workflow_run(id);
ALTER TABLE node_artifact ADD CONSTRAINT fk_artifact_node FOREIGN KEY (node_run_id) REFERENCES node_run(id);
