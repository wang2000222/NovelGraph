package com.aiwriter.saas.service.impl;

import static com.aiwriter.persistence.Store.*;

import com.aiwriter.common.Problem;
import com.aiwriter.entity.StoryGenre;
import com.aiwriter.mapper.StoryGenreMapper;
import com.aiwriter.saas.security.SaasContext;
import com.aiwriter.saas.service.GenreService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 全平台共用题材字典；所有写入都在服务端验证平台管理员身份。 */
@Service
public class GenreServiceImpl implements GenreService {
  private final StoryGenreMapper mapper;

  public GenreServiceImpl(StoryGenreMapper mapper) {
    this.mapper = mapper;
  }

  private void admin() {
    if (!SaasContext.admin()) throw new Problem(403, "PLATFORM_ADMIN_REQUIRED", "需要平台管理员权限");
  }

  public List<StoryGenre> list(boolean all) {
    if (all) admin();
    QueryWrapper<StoryGenre> q = new QueryWrapper<>();
    if (!all) q.eq("enabled", 1);
    return mapper.selectList(q.orderByAsc("sort_order", "id"));
  }

  @Transactional
  public StoryGenre save(Long id, Map<String, Object> b) {
    admin();
    String name = required(b, "name").trim(), description = str(b, "description", "").trim();
    long order = num(b, "sortOrder", 0), enabled = num(b, "enabled", 1);
    if (name.isEmpty()
        || name.length() > 50
        || description.length() > 500
        || order < 0
        || order > 99999
        || (enabled != 0 && enabled != 1))
      throw new Problem(400, "INVALID_GENRE", "题材名称限50字、说明限500字，排序为0至99999，状态为0或1");
    StoryGenre row = id == null ? new StoryGenre() : mapper.selectById(id);
    if (row == null) throw new Problem(404, "GENRE_NOT_FOUND", "题材不存在");
    QueryWrapper<StoryGenre> same = new QueryWrapper<StoryGenre>().eq("name", name);
    if (id != null) same.ne("id", id);
    if (mapper.selectCount(same) > 0) throw new Problem(409, "GENRE_EXISTS", "题材名称已存在");
    row.setName(name);
    row.setDescription(description);
    row.setSortOrder((int) order);
    row.setEnabled((int) enabled);
    if (id == null) mapper.insert(row);
    else mapper.updateById(row);
    return row;
  }

  public String selectedName(long id) {
    StoryGenre row = mapper.selectById(id);
    if (row == null || !Integer.valueOf(1).equals(row.getEnabled()))
      throw new Problem(400, "GENRE_UNAVAILABLE", "所选题材已停用或不存在，请重新选择");
    return row.getName();
  }
}
