package com.aiwriter.entity;

import com.baomidou.mybatisplus.annotation.*;

/** 多重人物关系。 */
@TableName("ng_relation")
public class NgRelation {
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
  /** 起点人物。 */
  @TableField("source_id")
  private Long sourceId;

  public Long getSourceId() {
    return sourceId;
  }

  public void setSourceId(Long value) {
    sourceId = value;
  }
  /** 终点人物。 */
  @TableField("target_id")
  private Long targetId;

  public Long getTargetId() {
    return targetId;
  }

  public void setTargetId(Long value) {
    targetId = value;
  }
  /** 方向。 */
  @TableField("direction")
  private String direction;

  public String getDirection() {
    return direction;
  }

  public void setDirection(String value) {
    direction = value;
  }
  /** 生效序号。 */
  @TableField("start_seq")
  private Long startSeq;

  public Long getStartSeq() {
    return startSeq;
  }

  public void setStartSeq(Long value) {
    startSeq = value;
  }
  /** 结束序号。 */
  @TableField("end_seq")
  private Long endSeq;

  public Long getEndSeq() {
    return endSeq;
  }

  public void setEndSeq(Long value) {
    endSeq = value;
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
