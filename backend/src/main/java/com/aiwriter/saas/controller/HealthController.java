package com.aiwriter.saas.controller;

import com.aiwriter.persistence.Store;
import io.swagger.annotations.*;
import org.springframework.web.bind.annotation.*;

/** 存活检查只返回服务状态，不公开数据库地址与邮件凭据。 */
@RestController
@Api(tags = "服务状态")
public class HealthController {
  private final Store db;

  public HealthController(Store db) {
    this.db = db;
  }

  @GetMapping("/health")
  @ApiOperation("服务与数据库连通状态")
  public Object health() {
    db.sql.queryForObject("SELECT 1", Integer.class);
    return Store.map("status", "UP", "application", "NovelGraph");
  }
}
