package com.aiwriter.entity;

import com.baomidou.mybatisplus.annotation.*;

/** 套餐及权益版本。 */
@TableName("saas_plan")
public class SaasPlan {
  /** 主键。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    id = value;
  }
  /** 套餐代码。 */
  @TableField("code")
  private String code;

  public String getCode() {
    return code;
  }

  public void setCode(String value) {
    code = value;
  }
  /** 名称。 */
  @TableField("name")
  private String name;

  public String getName() {
    return name;
  }

  public void setName(String value) {
    name = value;
  }
  /** 月价。 */
  @TableField("monthly_price")
  private java.math.BigDecimal monthlyPrice;

  public java.math.BigDecimal getMonthlyPrice() {
    return monthlyPrice;
  }

  public void setMonthlyPrice(java.math.BigDecimal value) {
    monthlyPrice = value;
  }
  /** 权益版本。 */
  @TableField("version")
  private Long version;

  public Long getVersion() {
    return version;
  }

  public void setVersion(Long value) {
    version = value;
  }
  /** 资源额度JSON。 */
  @TableField("limits")
  private String limits;

  public String getLimits() {
    return limits;
  }

  public void setLimits(String value) {
    limits = value;
  }
  /** 状态。 */
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
