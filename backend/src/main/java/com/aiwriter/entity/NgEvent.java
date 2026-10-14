package com.aiwriter.entity;

import com.baomidou.mybatisplus.annotation.*;

/** 故事事件。 */
@TableName("ng_event")
public class NgEvent {
  /** 主键。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    id = value;
  }
  /** 所属租户。 */
  @TableField("tenant_id")
  private Long tenantId;

  public Long getTenantId() {
    return tenantId;
  }

  public void setTenantId(Long value) {
    tenantId = value;
  }
  /** 所属项目。 */
  @TableField("project_id")
  private Long projectId;

  public Long getProjectId() {
    return projectId;
  }

  public void setProjectId(Long value) {
    projectId = value;
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
  /** 公开描述。 */
  @TableField("description")
  private String description;

  public String getDescription() {
    return description;
  }

  public void setDescription(String value) {
    description = value;
  }
  /** 读者揭示章节。 */
  @TableField("reveal_chapter")
  private Integer revealChapter;

  public Integer getRevealChapter() {
    return revealChapter;
  }

  public void setRevealChapter(Integer value) {
    revealChapter = value;
  }
  /** 可见边界。 */
  @TableField("visibility")
  private String visibility;

  public String getVisibility() {
    return visibility;
  }

  public void setVisibility(String value) {
    visibility = value;
  }
  /** 该实体扩展属性JSON。 */
  @TableField("data")
  private String data;

  public String getData() {
    return data;
  }

  public void setData(String value) {
    data = value;
  }
  /** 资料版本。 */
  @TableField("revision")
  private Long revision;

  public Long getRevision() {
    return revision;
  }

  public void setRevision(Long value) {
    revision = value;
  }
  /** 软删除。 */
  @TableField("deleted")
  private Integer deleted;

  public Integer getDeleted() {
    return deleted;
  }

  public void setDeleted(Integer value) {
    deleted = value;
  }
  /** 创建者。 */
  @TableField("created_by")
  private Long createdBy;

  public Long getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(Long value) {
    createdBy = value;
  }
  /** 时间类型。 */
  @TableField("time_kind")
  private String timeKind;

  public String getTimeKind() {
    return timeKind;
  }

  public void setTimeKind(String value) {
    timeKind = value;
  }
  /** 原始时间表达。 */
  @TableField("time_text")
  private String timeText;

  public String getTimeText() {
    return timeText;
  }

  public void setTimeText(String value) {
    timeText = value;
  }
  /** 精确或故事内开始时间。 */
  @TableField("starts_at")
  private String startsAt;

  public String getStartsAt() {
    return startsAt;
  }

  public void setStartsAt(String value) {
    startsAt = value;
  }
  /** 结束时间。 */
  @TableField("ends_at")
  private String endsAt;

  public String getEndsAt() {
    return endsAt;
  }

  public void setEndsAt(String value) {
    endsAt = value;
  }
  /** 故事排序锚点。 */
  @TableField("story_seq")
  private Long storySeq;

  public Long getStorySeq() {
    return storySeq;
  }

  public void setStorySeq(Long value) {
    storySeq = value;
  }
  /** 相对事件锚点。 */
  @TableField("anchor_id")
  private Long anchorId;

  public Long getAnchorId() {
    return anchorId;
  }

  public void setAnchorId(Long value) {
    anchorId = value;
  }
  /** 主要地点。 */
  @TableField("location_id")
  private Long locationId;

  public Long getLocationId() {
    return locationId;
  }

  public void setLocationId(Long value) {
    locationId = value;
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
