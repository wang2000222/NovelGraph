package com.aiwriter.entity;

import com.baomidou.mybatisplus.annotation.*;

/** 平台故事题材字典；项目保存题材名称快照，停用不影响历史作品。 */
@TableName("ng_story_genre")
public class StoryGenre {
  @TableId(type = IdType.AUTO)
  private Long id;

  private String name;
  private String description;
  private Integer sortOrder;
  private Integer enabled;

  public Long getId() {
    return id;
  }

  public void setId(Long v) {
    id = v;
  }

  public String getName() {
    return name;
  }

  public void setName(String v) {
    name = v;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String v) {
    description = v;
  }

  public Integer getSortOrder() {
    return sortOrder;
  }

  public void setSortOrder(Integer v) {
    sortOrder = v;
  }

  public Integer getEnabled() {
    return enabled;
  }

  public void setEnabled(Integer v) {
    enabled = v;
  }
}
