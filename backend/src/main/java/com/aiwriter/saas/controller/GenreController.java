package com.aiwriter.saas.controller;

import com.aiwriter.saas.service.GenreService;
import io.swagger.annotations.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Api(tags = "故事题材字典")
public class GenreController {
  private final GenreService service;

  public GenreController(GenreService service) {
    this.service = service;
  }

  @GetMapping("/genres")
  @ApiOperation("可选的启用题材")
  public Object list() {
    return service.list(false);
  }

  @GetMapping("/admin/genres")
  @ApiOperation("管理员查看全部题材")
  public Object adminList() {
    return service.list(true);
  }

  @PostMapping("/admin/genres")
  @ApiOperation("管理员新增题材")
  public Object create(@RequestBody Map<String, Object> b) {
    return service.save(null, b);
  }

  @PatchMapping("/admin/genres/{id}")
  @ApiOperation("管理员编辑、排序、启停题材")
  public Object update(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return service.save(id, b);
  }
}
