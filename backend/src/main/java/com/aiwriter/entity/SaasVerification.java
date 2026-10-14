package com.aiwriter.entity;

import com.baomidou.mybatisplus.annotation.*;

/** 一次性账号验证。 */
@TableName("saas_verification")
public class SaasVerification {
  /** 主键。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    id = value;
  }
  /** 账号。 */
  @TableField("user_id")
  private Long userId;

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long value) {
    userId = value;
  }
  /** 验证码散列。 */
  @TableField("token_hash")
  private String tokenHash;

  public String getTokenHash() {
    return tokenHash;
  }

  public void setTokenHash(String value) {
    tokenHash = value;
  }
  /** 验证或重置类型。 */
  @TableField("kind")
  private String kind;

  public String getKind() {
    return kind;
  }

  public void setKind(String value) {
    kind = value;
  }
  /** 失效时间。 */
  @TableField("expires_at")
  private java.util.Date expiresAt;

  public java.util.Date getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(java.util.Date value) {
    expiresAt = value;
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
