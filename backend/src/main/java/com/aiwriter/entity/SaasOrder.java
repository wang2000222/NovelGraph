package com.aiwriter.entity;

import com.baomidou.mybatisplus.annotation.*;

/** 人工开通订阅订单。 */
@TableName("saas_order")
public class SaasOrder {
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
  /** 购买套餐。 */
  @TableField("plan_code")
  private String planCode;

  public String getPlanCode() {
    return planCode;
  }

  public void setPlanCode(String value) {
    planCode = value;
  }
  /** 金额。 */
  @TableField("amount")
  private java.math.BigDecimal amount;

  public java.math.BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(java.math.BigDecimal value) {
    amount = value;
  }
  /** 订单状态。 */
  @TableField("status")
  private String status;

  public String getStatus() {
    return status;
  }

  public void setStatus(String value) {
    status = value;
  }
  /** 下单权益快照。 */
  @TableField("entitlements")
  private String entitlements;

  public String getEntitlements() {
    return entitlements;
  }

  public void setEntitlements(String value) {
    entitlements = value;
  }
  /** 购买月数。 */
  @TableField("months")
  private Integer months;

  public Integer getMonths() {
    return months;
  }

  public void setMonths(Integer value) {
    months = value;
  }
  /** 幂等请求键。 */
  @TableField("request_key")
  private String requestKey;

  public String getRequestKey() {
    return requestKey;
  }

  public void setRequestKey(String value) {
    requestKey = value;
  }
  /** 开通时间。 */
  @TableField("paid_at")
  private java.util.Date paidAt;

  public java.util.Date getPaidAt() {
    return paidAt;
  }

  public void setPaidAt(java.util.Date value) {
    paidAt = value;
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
