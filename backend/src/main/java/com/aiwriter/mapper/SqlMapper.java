package com.aiwriter.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import java.util.*;
import org.apache.ibatis.annotations.*;

/** 仅承载事实区间、租约、聚合和FOR UPDATE等复杂SQL；基础CRUD使用实体BaseMapper。 */
public interface SqlMapper {
  @SelectProvider(type = SqlProvider.class, method = "bind")
  // 复杂查询包含登录、运营统计及显式 tenant_id 条件，由服务校验范围；禁止接收客户端 SQL。
  @InterceptorIgnore(tenantLine = "true")
  List<Map<String, Object>> select(@Param("sql") String sql, @Param("args") Object[] args);

  @UpdateProvider(type = SqlProvider.class, method = "bind")
  @InterceptorIgnore(tenantLine = "true")
  int update(@Param("sql") String sql, @Param("args") Object[] args);
}
