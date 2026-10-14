package com.aiwriter.entity;

import com.baomidou.mybatisplus.annotation.*;

/** 验证与通知邮件队列。 */
@TableName("saas_email")
public class SaasEmail {
  /** 主键。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    id = value;
  }
  /** 收件邮箱。 */
  @TableField("email")
  private String email;

  public String getEmail() {
    return email;
  }

  public void setEmail(String value) {
    email = value;
  }
  /** 主题。 */
  @TableField("subject")
  private String subject;

  public String getSubject() {
    return subject;
  }

  public void setSubject(String value) {
    subject = value;
  }
  /** 邮件正文。 */
  @TableField("content")
  private String content;

  public String getContent() {
    return content;
  }

  public void setContent(String value) {
    content = value;
  }
  /** 发送状态。 */
  @TableField("status")
  private String status;

  public String getStatus() {
    return status;
  }

  public void setStatus(String value) {
    status = value;
  }
  /** 发送次数。 */
  @TableField("attempt")
  private Integer attempt;

  public Integer getAttempt() {
    return attempt;
  }

  public void setAttempt(Integer value) {
    attempt = value;
  }
  /** 脱敏发送错误。 */
  @TableField("error_code")
  private String errorCode;

  public String getErrorCode() {
    return errorCode;
  }

  public void setErrorCode(String value) {
    errorCode = value;
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
