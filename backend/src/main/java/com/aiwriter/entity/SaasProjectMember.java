package com.aiwriter.entity;

import com.baomidou.mybatisplus.annotation.*;

/** 项目授权。 */
@TableName("saas_project_member")
public class SaasProjectMember {
  /** 主键。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    id = value;
  }
  /** 空间。 */
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
  /** 成员。 */
  @TableField("user_id")
  private Long userId;

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long value) {
    userId = value;
  }
  /** 项目角色。 */
  @TableField("role")
  private String role;

  public String getRole() {
    return role;
  }

  public void setRole(String value) {
    role = value;
  }
  /** 导出权限。 */
  @TableField("can_export")
  private Integer canExport;

  public Integer getCanExport() {
    return canExport;
  }

  public void setCanExport(Integer value) {
    canExport = value;
  }
  /** 秘密权限。 */
  @TableField("can_secrets")
  private Integer canSecrets;

  public Integer getCanSecrets() {
    return canSecrets;
  }

  public void setCanSecrets(Integer value) {
    canSecrets = value;
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
