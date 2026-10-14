-- 防止小说删除后仍在返回的模型请求重新生成无主用量记录。
-- 共享模型及未关联工作流的摘要调用（run_id为空）仍然保留。
ALTER TABLE llm_usage_log ADD CONSTRAINT fk_usage_run_parent FOREIGN KEY (run_id) REFERENCES workflow_run(id) ON DELETE CASCADE;
