package com.aiwriter.saas.security;

import com.aiwriter.common.Problem;
import com.aiwriter.persistence.Store;
import com.aiwriter.saas.service.AuthService;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.servlet.*;
import javax.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** 会话、CSRF与基础请求限流；客户身份独立于旧API令牌。 */
@Component
public class SaasFilter extends OncePerRequestFilter {
  private final AuthService auth;
  private final Store db;
  private final Set<String> origins;
  private final Map<String, long[]> limits = new ConcurrentHashMap<>();

  public SaasFilter(
      AuthService auth,
      Store db,
      @Value(
              "${saas.allowed-origins:http://localhost:5173,http://127.0.0.1:5173,http://localhost:4173,http://127.0.0.1:4173}")
          String origins) {
    this.auth = auth;
    this.db = db;
    this.origins = new HashSet<>(Arrays.asList(origins.split(",")));
  }

  public static String token(HttpServletRequest req) {
    if (req.getCookies() != null)
      for (Cookie c : req.getCookies()) if (c.getName().equals("ng_session")) return c.getValue();
    return "";
  }

  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    if (!req.getRequestURI().startsWith("/api/v1/")) {
      chain.doFilter(req, res);
      return;
    }
    res.setHeader("Cache-Control", "no-store");
    res.setHeader("X-Content-Type-Options", "nosniff");
    try {
      boolean write = !Arrays.asList("GET", "HEAD", "OPTIONS").contains(req.getMethod());
      String path = req.getRequestURI();
      if (write) {
        String origin = req.getHeader("Origin");
        String ownOrigin =
            req.getScheme()
                + "://"
                + req.getServerName()
                + ((req.getServerPort() == 80 && req.getScheme().equals("http")
                        || req.getServerPort() == 443 && req.getScheme().equals("https"))
                    ? ""
                    : ":" + req.getServerPort());
        if (origin != null && !origins.contains(origin) && !origin.equals(ownOrigin))
          throw new Problem(403, "ORIGIN_DENIED", "请求来源不受信任");
      }
      boolean publicApi =
          path.matches("/api/v1/auth/(login|register|verify|forgot|reset)")
              || path.equals("/api/v1/public");
      if (publicApi && write) {
        long now = System.currentTimeMillis();
        limits.entrySet().removeIf(e -> now - e.getValue()[0] > 600000);
        long[] v = limits.computeIfAbsent(req.getRemoteAddr(), k -> new long[] {now, 0});
        synchronized (v) {
          if (now - v[0] > 60000) {
            v[0] = now;
            v[1] = 0;
          }
          if (++v[1] > 20) throw new Problem(429, "RATE_LIMIT", "请求过于频繁，请稍后再试");
        }
      }
      if (!publicApi) {
        Map<String, Object> u = auth.authenticate(token(req));
        SaasContext.set(u);
        if (write && !Objects.equals(u.get("csrf"), req.getHeader("X-CSRF-Token")))
          throw new Problem(403, "CSRF_INVALID", "页面安全信息已失效，请刷新");
      }
      chain.doFilter(req, res);
    } catch (Problem e) {
      res.setStatus(e.status);
      res.setContentType("application/json;charset=UTF-8");
      res.getWriter().write(db.json(Store.map("code", e.code, "message", e.getMessage())));
    } finally {
      SaasContext.clear();
    }
  }
}
