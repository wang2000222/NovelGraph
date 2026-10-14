package com.aiwriter.saas.controller;

import com.aiwriter.saas.service.*;
import io.swagger.annotations.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Api(tags = "NovelGraph 世界编辑器")
public class WorldController {
  @GetMapping("/projects/{id}/permissions")
  @ApiOperation("项目授权与可选团队成员")
  public Object grants(@PathVariable long id) {
    return w.grants(id);
  }

  @DeleteMapping("/projects/{id}/snapshots/{sid}")
  @ApiOperation("删除恢复点并释放额度")
  public Object deleteSnapshot(@PathVariable long id, @PathVariable long sid) {
    w.deleteSnapshot(id, sid);
    return Collections.singletonMap("deleted", true);
  }

  private final WorldService w;
  private final ExportService e;

  public WorldController(WorldService w, ExportService e) {
    this.w = w;
    this.e = e;
  }

  @GetMapping("/tenants/{tenant}/projects")
  @ApiOperation("租户内已授权项目")
  public Object projects(@PathVariable long tenant) {
    return w.projects(tenant);
  }

  @PostMapping("/tenants/{tenant}/projects")
  @ApiOperation("创建小说世界")
  public Object create(@PathVariable long tenant, @RequestBody Map<String, Object> b) {
    return w.create(tenant, b);
  }

  @PatchMapping("/projects/{id}")
  @ApiOperation("修改项目基本设定")
  public Object update(@PathVariable long id, @RequestBody Map<String, Object> b) {
    return w.update(id, b);
  }

  @DeleteMapping("/projects/{id}")
  @ApiOperation("永久级联删除项目")
  public Object delete(@PathVariable long id) {
    w.delete(id);
    return Collections.singletonMap("deleted", true);
  }

  @GetMapping("/projects/{id}/graph")
  @ApiOperation("授权图谱与布局")
  public Object graph(@PathVariable long id) {
    return w.graph(id);
  }

  @PostMapping("/projects/{id}/entities/{kind}")
  @ApiOperation("新增结构化资料")
  public Object add(
      @PathVariable long id, @PathVariable String kind, @RequestBody Map<String, Object> b) {
    return w.save(id, kind, 0, b);
  }

  @PatchMapping("/projects/{id}/entities/{kind}/{eid}")
  @ApiOperation("版本检查并修改资料")
  public Object save(
      @PathVariable long id,
      @PathVariable String kind,
      @PathVariable long eid,
      @RequestBody Map<String, Object> b) {
    return w.save(id, kind, eid, b);
  }

  @DeleteMapping("/projects/{id}/entities/{kind}/{eid}")
  @ApiOperation("软删除资料及相关引用")
  public Object del(
      @PathVariable long id,
      @PathVariable String kind,
      @PathVariable long eid,
      @RequestBody Map<String, Object> b) {
    return w.deleteEntity(id, kind, eid, b);
  }

  @PostMapping("/projects/{id}/entities/{kind}/{eid}/restore")
  @ApiOperation("恢复单条资料")
  public Object restoreEntity(
      @PathVariable long id,
      @PathVariable String kind,
      @PathVariable long eid,
      @RequestBody Map<String, Object> b) {
    return w.restoreEntity(id, kind, eid, b);
  }

  @PostMapping("/projects/{id}/layout")
  @ApiOperation("批量保存独立布局")
  public Object layout(@PathVariable long id, @RequestBody Map<String, Object> b) {
    return w.layout(id, b);
  }

  @PostMapping("/projects/{id}/permissions")
  @ApiOperation("授予成员项目角色")
  public Object permissions(@PathVariable long id, @RequestBody Map<String, Object> b) {
    return w.permissions(id, b);
  }

  @PostMapping("/projects/{id}/validate")
  @ApiOperation("设定一致性检查")
  public Object validate(@PathVariable long id) {
    return w.validate(id);
  }

  @PostMapping("/projects/{id}/exports/preview")
  @ApiOperation("确定性提示词预览")
  public Object preview(@PathVariable long id, @RequestBody Map<String, Object> b) {
    return e.preview(id, b);
  }

  @PostMapping("/projects/{id}/exports/download")
  @ApiOperation("按预览版本下载提示词")
  public ResponseEntity<byte[]> download(
      @PathVariable long id, @RequestBody Map<String, Object> b) {
    Map<String, Object> r = e.preview(id, b);
    String ext = Arrays.asList("JSON", "BACKUP").contains(r.get("format")) ? "json" : "md";
    return ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=novelgraph-" + id + "." + ext)
        .contentType(MediaType.parseMediaType("text/plain;charset=UTF-8"))
        .body(r.get("content").toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  @GetMapping("/projects/{id}/snapshots")
  @ApiOperation("恢复点列表")
  public Object snapshots(@PathVariable long id) {
    return w.snapshots(id);
  }

  @PostMapping("/projects/{id}/snapshots")
  @ApiOperation("创建恢复点")
  public Object snapshot(@PathVariable long id, @RequestBody Map<String, Object> b) {
    return w.snapshot(id, b);
  }

  @PostMapping("/projects/{id}/snapshots/{sid}/restore")
  @ApiOperation("恢复为新修订")
  public Object restore(
      @PathVariable long id, @PathVariable long sid, @RequestBody Map<String, Object> b) {
    return w.restore(id, sid, b);
  }

  @PostMapping("/projects/{id}/copy")
  @ApiOperation("复制为新项目")
  public Object copy(@PathVariable long id, @RequestBody Map<String, Object> b) {
    return w.copy(id, b);
  }

  @PostMapping("/imports/preview")
  @ApiOperation("校验JSON导入包")
  public Object importPreview(@RequestBody Map<String, Object> b) {
    return w.importPreview(b);
  }

  @PostMapping("/tenants/{tenant}/imports")
  @ApiOperation("导入为新项目并重映射ID")
  public Object importCommit(@PathVariable long tenant, @RequestBody Map<String, Object> b) {
    return w.importCommit(tenant, b);
  }
}
