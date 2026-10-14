package com.aiwriter.saas.controller;

import com.aiwriter.saas.service.ExtractionService;
import io.swagger.annotations.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects/{project}/extractions")
@Api(tags = "小说文本提取")
public class ExtractionController {
  private final ExtractionService service;

  public ExtractionController(ExtractionService service) {
    this.service = service;
  }

  @PostMapping("/prompt")
  @ApiOperation("生成小说文本提取提示词与中文JSON回答模板")
  public Object prompt(@PathVariable long project, @RequestBody Map<String, Object> body) {
    return service.prompt(project, body);
  }

  @PostMapping("/preview")
  @ApiOperation("校验AI回答并预览，事务回滚不保留任何资料")
  public Object preview(@PathVariable long project, @RequestBody Map<String, Object> body) {
    return service.preview(project, body);
  }

  @PostMapping("/commit")
  @ApiOperation("版本校验后整批导入当前项目，相同批次幂等")
  public Object commit(@PathVariable long project, @RequestBody Map<String, Object> body) {
    return service.commit(project, body);
  }
}
