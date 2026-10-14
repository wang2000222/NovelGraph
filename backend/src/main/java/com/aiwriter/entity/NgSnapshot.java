package com.aiwriter.entity;

import com.baomidou.mybatisplus.annotation.*;

/** 项目恢复点。 */
@TableName("ng_snapshot")
public class NgSnapshot {
  /** 主键。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    id = value;
  }
  /** 租户。 */
  @TableField("tenant_id")
  private Long tenantId;

  public Long getTenantId() {
    return tenantId;
  }

  public void setTenantId(Long value) {
    tenantId = value;
  }
  /** 项目。 */
  @TableField("project_id")
  private Long projectId;

  public Long getProjectId() {
    return projectId;
  }

  public void setProjectId(Long value) {
    projectId = value;
  }
  /** 内容版本。 */
  @TableField("revision")
  private Long revision;

  public Long getRevision() {
    return revision;
  }

  public void setRevision(Long value) {
    revision = value;
  }
  /** 快照备注。 */
  @TableField("name")
  private String name;

  public String getName() {
    return name;
  }

  public void setName(String value) {
    name = value;
  }
  /** 全量项目设定包。 */
  @TableField("content")
  private String content;

  public String getContent() {
    return content;
  }

  public void setContent(String value) {
    content = value;
  }
  /** 创建人。 */
  @TableField("created_by")
  private Long createdBy;

  public Long getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(Long value) {
    createdBy = value;
  }
  /** 创建时间。 */
  @TableField("created_at")
  private java.util.Date createdAt;

  public java.util.Date getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(java.util.Date value) {
    createdAt = value;
  }
  /** 更新时间。 */
  @TableField("updated_at")
  private java.util.Date updatedAt;

  public java.util.Date getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(java.util.Date value) {
    updatedAt = value;
  }
}
