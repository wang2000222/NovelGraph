package com.aiwriter.entity;

import com.baomidou.mybatisplus.annotation.*;

/** 小说文本提取导入的幂等记录。 */
@TableName("ng_extraction_batch")
public class NgExtractionBatch {
  /** 导入批次主键。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  public Long getId() {
    return id;
  }

  public void setId(Long v) {
    id = v;
  }
  /** 所属租户。 */
  @TableField("tenant_id")
  private Long tenantId;

  public Long getTenantId() {
    return tenantId;
  }

  public void setTenantId(Long v) {
    tenantId = v;
  }
  /** 所属项目。 */
  @TableField("project_id")
  private Long projectId;

  public Long getProjectId() {
    return projectId;
  }

  public void setProjectId(Long v) {
    projectId = v;
  }
  /** 防重复批次编号。 */
  @TableField("request_key")
  private String requestKey;

  public String getRequestKey() {
    return requestKey;
  }

  public void setRequestKey(String v) {
    requestKey = v;
  }
  /** 规范化回答摘要。 */
  @TableField("payload_hash")
  private String payloadHash;

  public String getPayloadHash() {
    return payloadHash;
  }

  public void setPayloadHash(String v) {
    payloadHash = v;
  }
  /** 导入结果。 */
  @TableField("result_json")
  private String resultJson;

  public String getResultJson() {
    return resultJson;
  }

  public void setResultJson(String v) {
    resultJson = v;
  }
  /** 操作用户。 */
  @TableField("created_by")
  private Long createdBy;

  public Long getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(Long v) {
    createdBy = v;
  }
  /** 提交时间。 */
  @TableField("created_at")
  private java.util.Date createdAt;

  public java.util.Date getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(java.util.Date v) {
    createdAt = v;
  }
}
