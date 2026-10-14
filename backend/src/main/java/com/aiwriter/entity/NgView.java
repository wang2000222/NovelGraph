package com.aiwriter.entity;

import com.baomidou.mybatisplus.annotation.*;

/** 独立画布布局。 */
@TableName("ng_view")
public class NgView {
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
  /** 视图名称。 */
  @TableField("view_key")
  private String viewKey;

  public String getViewKey() {
    return viewKey;
  }

  public void setViewKey(String value) {
    viewKey = value;
  }
  /** 布局版本。 */
  @TableField("revision")
  private Long revision;

  public Long getRevision() {
    return revision;
  }

  public void setRevision(Long value) {
    revision = value;
  }
  /** 布局坐标及视口JSON。 */
  @TableField("layout")
  private String layout;

  public String getLayout() {
    return layout;
  }

  public void setLayout(String value) {
    layout = value;
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
