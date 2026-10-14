package com.aiwriter.entity;

import com.baomidou.mybatisplus.annotation.*;

/** 站内通知。 */
@TableName("saas_notification")
public class SaasNotification {
  /** 主键。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    id = value;
  }
  /** 所属空间。 */
  @TableField("tenant_id")
  private Long tenantId;

  public Long getTenantId() {
    return tenantId;
  }

  public void setTenantId(Long value) {
    tenantId = value;
  }
  /** 收件用户。 */
  @TableField("user_id")
  private Long userId;

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long value) {
    userId = value;
  }
  /** 标题。 */
  @TableField("title")
  private String title;

  public String getTitle() {
    return title;
  }

  public void setTitle(String value) {
    title = value;
  }
  /** 内容。 */
  @TableField("content")
  private String content;

  public String getContent() {
    return content;
  }

  public void setContent(String value) {
    content = value;
  }
  /** 已读标记。 */
  @TableField("is_read")
  private Integer isRead;

  public Integer getIsRead() {
    return isRead;
  }

  public void setIsRead(Integer value) {
    isRead = value;
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
