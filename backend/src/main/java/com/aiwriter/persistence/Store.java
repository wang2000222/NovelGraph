package com.aiwriter.persistence;

import com.aiwriter.common.Problem;
import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import java.lang.reflect.Field;
import java.util.*;
import org.springframework.stereotype.Component;

/** SQL标识符仅由后端代码产生；所有业务参数使用预编译绑定。 */
@Component
public class Store {
  public final SqlGateway sql;
  private final ObjectMapper mapper;
  private final EntityRegistry registry;

  public Store(SqlGateway sql, ObjectMapper mapper, EntityRegistry registry) {
    this.sql = sql;
    // 对外响应可省略null，内部事实提案必须保留oldValue:null，不能丢失协议字段。
    this.mapper =
        mapper
            .copy()
            .setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS);
    this.registry = registry;
  }

  /** 使用各表的MyBatis-Plus BaseMapper读取实体，返回与现有API一致的字段映射。 */
  public Map<String, Object> get(String table, long id) {
    Object entity = registry.mapper(table).selectById(id);
    if (entity == null) throw new Problem(404, "NOT_FOUND", "记录不存在");
    Map<String, Object> result = new LinkedHashMap<>();
    try {
      for (Field field : entity.getClass().getDeclaredFields()) {
        field.setAccessible(true);
        TableField column = field.getAnnotation(TableField.class);
        result.put(column == null ? field.getName() : column.value(), field.get(entity));
      }
    } catch (IllegalAccessException e) {
      throw new IllegalStateException(e);
    }
    return result;
  }

  public List<Map<String, Object>> query(String statement, Object... args) {
    return sql.query(statement, args);
  }

  /** 基础插入交给BaseMapper，MyBatis-Plus回填自增ID，未提供字段使用数据库默认值。 */
  public long insert(String table, Map<String, Object> values) {
    ObjectMapper entityMapper =
        mapper.copy().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    Object entity = entityMapper.convertValue(values, registry.entity(table));
    registry.mapper(table).insert(entity);
    try {
      return ((Number) entity.getClass().getMethod("getId").invoke(entity)).longValue();
    } catch (Exception e) {
      throw new IllegalStateException("主键回填失败", e);
    }
  }

  public String json(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (Exception e) {
      throw new Problem(400, "INVALID_JSON", "JSON格式无效");
    }
  }

  /** 读取事实值JSON；事实可能为字符串、数值、布尔、数组或对象。 */
  public Object value(Object raw) {
    if (raw == null) return null;
    try {
      return mapper.readValue(raw.toString(), Object.class);
    } catch (Exception e) {
      throw new Problem(400, "INVALID_FACT_JSON", "事实值JSON格式无效");
    }
  }

  public Map<String, Object> obj(Object raw) {
    if (raw instanceof Map) return (Map<String, Object>) raw;
    if (raw == null) return new LinkedHashMap<>();
    try {
      return mapper.readValue(
          raw.toString(), new TypeReference<LinkedHashMap<String, Object>>() {});
    } catch (Exception e) {
      throw new Problem(400, "INVALID_JSON", "JSON对象格式无效");
    }
  }

  public List<Map<String, Object>> list(Object raw) {
    if (raw == null) return new ArrayList<>();
    if (raw instanceof List) return (List<Map<String, Object>>) raw;
    try {
      return mapper.readValue(raw.toString(), new TypeReference<List<Map<String, Object>>>() {});
    } catch (Exception e) {
      throw new Problem(400, "INVALID_JSON", "JSON数组格式无效");
    }
  }

  public static Map<String, Object> map(Object... pairs) {
    Map<String, Object> v = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) v.put(pairs[i].toString(), pairs[i + 1]);
    return v;
  }

  public static String str(Map<String, Object> v, String key, String fallback) {
    Object x = v.get(key);
    return x == null ? fallback : x.toString();
  }

  public static long num(Map<String, Object> v, String key, long fallback) {
    try {
      return v.get(key) == null ? fallback : Long.parseLong(v.get(key).toString());
    } catch (Exception e) {
      throw new Problem(400, "INVALID_NUMBER", key + "必须是整数");
    }
  }

  public static String required(Map<String, Object> v, String key) {
    String x = str(v, key, "").trim();
    if (x.isEmpty()) throw new Problem(400, "REQUIRED", key + "不能为空");
    return x;
  }
}
