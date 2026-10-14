package com.aiwriter.common;

import com.aiwriter.persistence.Store;
import java.io.IOException;
import java.util.UUID;
import javax.servlet.*;
import javax.servlet.http.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
class ApiSecurity extends OncePerRequestFilter {
  private final Store db;

  ApiSecurity(Store db) {
    this.db = db;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String trace = UUID.randomUUID().toString();
    req.setAttribute("traceId", trace);
    res.setHeader("X-Trace-Id", trace);
    String path = req.getRequestURI();
    // 新SaaS接口由账号会话过滤器保护；旧生成接口默认关闭，不开放给租户。
    if (path.startsWith("/api/v1/")) {
      chain.doFilter(req, res);
      return;
    }
    if (path.startsWith("/api/")) {
      res.setStatus(410);
      res.setContentType("application/json;charset=UTF-8");
      res.getWriter().write("{\"message\":\"旧创作功能已停用，请使用NovelGraph工作台\"}");
      return;
    }
    chain.doFilter(req, res);
  }
}

@RestControllerAdvice
class ApiErrors {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ApiErrors.class);

  @ExceptionHandler(Problem.class)
  ResponseEntity<?> problem(Problem e) {
    return ResponseEntity.status(e.status)
        .body(Store.map("code", e.code, "message", e.getMessage()));
  }

  @ExceptionHandler({
    org.springframework.http.converter.HttpMessageNotReadableException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
    org.springframework.web.bind.MissingRequestHeaderException.class,
    org.springframework.web.bind.MissingServletRequestParameterException.class
  })
  ResponseEntity<?> bad(Exception e) {
    return ResponseEntity.badRequest()
        .body(Store.map("code", "BAD_REQUEST", "message", "请求参数格式错误"));
  }

  @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
  ResponseEntity<?> method(Exception e) {
    return ResponseEntity.status(405)
        .body(Store.map("code", "METHOD_NOT_ALLOWED", "message", "该接口不支持此请求方法"));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<?> conflict(Exception e) {
    return ResponseEntity.status(409)
        .body(Store.map("code", "CONFLICT", "message", "记录重复或数据约束不满足"));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> error(Exception e, HttpServletRequest request) {
    // 记录可定位的追踪ID与代码位置，不记录请求体、服务商响应或异常中的密钥内容。
    log.error(
        "request failed traceId={} exception={} source={}",
        request.getAttribute("traceId"),
        e.getClass().getSimpleName(),
        e.getStackTrace().length == 0 ? "unknown" : e.getStackTrace()[0].toString());
    return ResponseEntity.status(500)
        .body(Store.map("code", "INTERNAL_ERROR", "message", "服务处理失败，请通过追踪ID排查"));
  }
}
