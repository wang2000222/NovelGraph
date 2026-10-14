package com.aiwriter.mapper;

import java.util.*;

/** 将后端常量SQL中的位置参数转成MyBatis预编译绑定，绝不拼接业务值。 */
public final class SqlProvider {
  public String bind(Map<String, Object> parameters) {
    String sql = (String) parameters.get("sql");
    Object[] args = (Object[]) parameters.get("args");
    StringBuilder out = new StringBuilder();
    int index = 0;
    boolean quoted = false;
    for (int i = 0; i < sql.length(); i++) {
      char c = sql.charAt(i);
      if (c == '\'') quoted = !quoted;
      if (c == '?' && !quoted) {
        out.append("#{args[").append(index++).append("]}");
      } else out.append(c);
    }
    if (index != args.length) throw new IllegalArgumentException("SQL参数数量不匹配");
    return out.toString();
  }
}
