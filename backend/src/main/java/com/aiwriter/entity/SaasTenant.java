package com.aiwriter.entity;

import com.baomidou.mybatisplus.annotation.*;

/** 个人与团队空间。 */
@TableName("saas_tenant")
public class SaasTenant {
  /** 主键。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    id = value;
  }
  /** 空间名称。 */
  @TableField("name")
  private String name;

  public String getName() {
    return name;
  }

  public void setName(String value) {
    name = value;
  }
  /** 个人或团队。 */
  @TableField("kind")
  private String kind;

  public String getKind() {
    return kind;
  }

  public void setKind(String value) {
    kind = value;
  }
  /** 所有者账号。 */
  @TableField("owner_id")
  private Long ownerId;

  public Long getOwnerId() {
    return ownerId;
  }

  public void setOwnerId(Long value) {
    ownerId = value;
  }
  /** 生效套餐。 */
  @TableField("plan_code")
  private String planCode;

  public String getPlanCode() {
    return planCode;
  }

  public void setPlanCode(String value) {
    planCode = value;
  }
  /** 空间状态。 */
  @TableField("status")
  private String status;

  public String getStatus() {
    return status;
  }

  public void setStatus(String value) {
    status = value;
  }
  /** 套餐到期时间。 */
  @TableField("subscription_until")
  private java.util.Date subscriptionUntil;

  public java.util.Date getSubscriptionUntil() {
    return subscriptionUntil;
  }

  public void setSubscriptionUntil(java.util.Date value) {
    subscriptionUntil = value;
  }
  /** 空间版本。 */
  @TableField("version")
  private Long version;

  public Long getVersion() {
    return version;
  }

  public void setVersion(Long value) {
    version = value;
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
