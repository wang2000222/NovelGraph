package com.aiwriter.persistence;

import com.aiwriter.mapper.SqlMapper;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Component;

/** 复杂业务SQL统一交给MyBatis Mapper执行，与Spring事务使用相同数据源。 */
@Component
public class SqlGateway {
  private final SqlMapper mapper;

  public SqlGateway(SqlMapper mapper) {
    this.mapper = mapper;
  }

  public List<Map<String, Object>> query(String sql, Object... args) {
    return mapper.select(sql, args);
  }

  public int update(String sql, Object... args) {
    return mapper.update(sql, args);
  }

  public void execute(String sql) {
    mapper.update(sql, new Object[0]);
  }

  public <T> T queryForObject(String sql, Class<T> type, Object... args) {
    List<Map<String, Object>> rows = query(sql, args);
    if (rows.size() != 1) throw new IllegalStateException("查询应返回一行");
    Object v = rows.get(0).values().iterator().next();
    if (v == null) return null;
    if (type == Integer.class) return type.cast(((Number) v).intValue());
    if (type == Long.class) return type.cast(((Number) v).longValue());
    if (type == BigDecimal.class) return type.cast(new BigDecimal(v.toString()));
    return type.cast(v);
  }
}
