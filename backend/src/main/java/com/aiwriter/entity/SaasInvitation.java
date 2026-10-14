package com.aiwriter.entity;

import com.baomidou.mybatisplus.annotation.*;

/** 限时成员邀请。 */
@TableName("saas_invitation")
public class SaasInvitation {
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
  /** 受邀邮箱。 */
  @TableField("email")
  private String email;

  public String getEmail() {
    return email;
  }

  public void setEmail(String value) {
    email = value;
  }
  /** 邀请角色。 */
  @TableField("role")
  private String role;

  public String getRole() {
    return role;
  }

  public void setRole(String value) {
    role = value;
  }
  /** 一次性令牌散列。 */
  @TableField("token_hash")
  private String tokenHash;

  public String getTokenHash() {
    return tokenHash;
  }

  public void setTokenHash(String value) {
    tokenHash = value;
  }
  /** 有效截止。 */
  @TableField("expires_at")
  private java.util.Date expiresAt;

  public java.util.Date getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(java.util.Date value) {
    expiresAt = value;
  }
  /** 邀请状态。 */
  @TableField("status")
  private String status;

  public String getStatus() {
    return status;
  }

  public void setStatus(String value) {
    status = value;
  }
  /** 邀请者。 */
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
