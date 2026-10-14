package com.aiwriter.entity;

import com.baomidou.mybatisplus.annotation.*;

/** 小说世界项目。 */
@TableName("ng_project")
public class NgProject {
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
  /** 小说名称。 */
  @TableField("title")
  private String title;

  public String getTitle() {
    return title;
  }

  public void setTitle(String value) {
    title = value;
  }
  /** 题材。 */
  @TableField("genre")
  private String genre;

  public String getGenre() {
    return genre;
  }

  public void setGenre(String value) {
    genre = value;
  }
  /** 世界概述。 */
  @TableField("overview")
  private String overview;

  public String getOverview() {
    return overview;
  }

  public void setOverview(String value) {
    overview = value;
  }
  /** 风格。 */
  @TableField("style")
  private String style;

  public String getStyle() {
    return style;
  }

  public void setStyle(String value) {
    style = value;
  }
  /** 创作约束。 */
  @TableField("constraints")
  private String constraints;

  public String getConstraints() {
    return constraints;
  }

  public void setConstraints(String value) {
    constraints = value;
  }
  /** 时区。 */
  @TableField("timezone")
  private String timezone;

  public String getTimezone() {
    return timezone;
  }

  public void setTimezone(String value) {
    timezone = value;
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
  /** 明确关联旧小说ID。 */
  @TableField("legacy_project_id")
  private Long legacyProjectId;

  public Long getLegacyProjectId() {
    return legacyProjectId;
  }

  public void setLegacyProjectId(Long value) {
    legacyProjectId = value;
  }
  /** 创建者。 */
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
