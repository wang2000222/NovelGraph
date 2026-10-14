package com.aiwriter.entity;

import com.baomidou.mybatisplus.annotation.*;

/** 平台与租户操作审计。 */
@TableName("saas_audit")
public class SaasAudit {
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
  /** 操作者。 */
  @TableField("user_id")
  private Long userId;

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long value) {
    userId = value;
  }
  /** 动作。 */
  @TableField("action")
  private String action;

  public String getAction() {
    return action;
  }

  public void setAction(String value) {
    action = value;
  }
  /** 对象ID。 */
  @TableField("target_id")
  private Long targetId;

  public Long getTargetId() {
    return targetId;
  }

  public void setTargetId(Long value) {
    targetId = value;
  }
  /** 脱敏说明。 */
  @TableField("detail")
  private String detail;

  public String getDetail() {
    return detail;
  }

  public void setDetail(String value) {
    detail = value;
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
