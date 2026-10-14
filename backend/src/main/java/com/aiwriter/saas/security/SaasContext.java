package com.aiwriter.saas.security;

import com.aiwriter.common.Problem;
import java.util.Map;

/** 单次请求的认证用户；线程复用前必须清除，不接受客户端声明管理员身份。 */
public final class SaasContext {
  private static final ThreadLocal<Map<String, Object>> USER = new ThreadLocal<>();
  private static final ThreadLocal<Long> TENANT = new ThreadLocal<>();

  public static void set(Map<String, Object> user) {
    TENANT.remove();
    USER.set(user);
  }

  public static void tenant(long id) {
    TENANT.set(id);
  }

  public static Long tenant() {
    return TENANT.get();
  }

  public static Map<String, Object> user() {
    if (USER.get() == null) throw new Problem(401, "LOGIN_REQUIRED", "请先登录");
    return USER.get();
  }

  public static long id() {
    return ((Number) user().get("id")).longValue();
  }

  public static boolean admin() {
    return "ADMIN".equals(user().get("platform_role"));
  }

  public static void clear() {
    USER.remove();
    TENANT.remove();
  }
}
