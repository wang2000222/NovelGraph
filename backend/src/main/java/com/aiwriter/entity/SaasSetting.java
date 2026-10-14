package com.aiwriter.entity;

import com.baomidou.mybatisplus.annotation.*;

/** 平台配置。 */
@TableName("saas_setting")
public class SaasSetting {
  /** 主键。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    id = value;
  }
  /** 配置键。 */
  @TableField("setting_key")
  private String settingKey;

  public String getSettingKey() {
    return settingKey;
  }

  public void setSettingKey(String value) {
    settingKey = value;
  }
  /** 配置值。 */
  @TableField("value")
  private String value;

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
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
