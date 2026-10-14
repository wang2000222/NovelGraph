package com.aiwriter.entity;

import com.baomidou.mybatisplus.annotation.*;

/** SaaS用户账号。 */
@TableName("saas_user")
public class SaasUser {
  /** 主键。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    id = value;
  }
  /** 登录邮箱。 */
  @TableField("email")
  private String email;

  public String getEmail() {
    return email;
  }

  public void setEmail(String value) {
    email = value;
  }
  /** 显示名称。 */
  @TableField("name")
  private String name;

  public String getName() {
    return name;
  }

  public void setName(String value) {
    name = value;
  }
  /** 加盐密码散列。 */
  @TableField("password_hash")
  private String passwordHash;

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String value) {
    passwordHash = value;
  }
  /** 邮箱验证状态。 */
  @TableField("verified")
  private Integer verified;

  public Integer getVerified() {
    return verified;
  }

  public void setVerified(Integer value) {
    verified = value;
  }
  /** 平台角色。 */
  @TableField("platform_role")
  private String platformRole;

  public String getPlatformRole() {
    return platformRole;
  }

  public void setPlatformRole(String value) {
    platformRole = value;
  }
  /** 账号状态。 */
  @TableField("status")
  private String status;

  public String getStatus() {
    return status;
  }

  public void setStatus(String value) {
    status = value;
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
