package com.aiwriter.saas.controller;

import com.aiwriter.saas.service.WorkspaceService;
import io.swagger.annotations.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Api(tags = "SaaS 空间与运营")
public class WorkspaceController {
  private final WorkspaceService s;

  public WorkspaceController(WorkspaceService s) {
    this.s = s;
  }

  @GetMapping("/public")
  @ApiOperation("公开套餐与客服邮箱")
  public Object settings() {
    return s.settings();
  }

  @PostMapping("/tenants")
  @ApiOperation("创建团队空间")
  public Object create(@RequestBody Map<String, Object> b) {
    return s.create(b);
  }

  @GetMapping("/tenants/{id}")
  @ApiOperation("空间权益与用量")
  public Object overview(@PathVariable long id) {
    return s.overview(id);
  }

  @GetMapping("/tenants/{id}/members")
  @ApiOperation("成员与邀请")
  public Object members(@PathVariable long id) {
    return s.members(id);
  }

  @PostMapping("/tenants/{id}/invitations")
  @ApiOperation("邮件邀请成员")
  public Object invite(@PathVariable long id, @RequestBody Map<String, Object> b) {
    return s.invite(id, b);
  }

  @PostMapping("/invitations/accept")
  @ApiOperation("接受匹配邮箱邀请")
  public Object accept(@RequestBody Map<String, Object> b) {
    return s.accept(b);
  }

  @PatchMapping("/tenants/{id}/members/{uid}")
  @ApiOperation("修改成员角色")
  public Object role(
      @PathVariable long id, @PathVariable long uid, @RequestBody Map<String, Object> b) {
    return s.member(id, uid, b, false);
  }

  @DeleteMapping("/tenants/{id}/members/{uid}")
  @ApiOperation("移除成员并撤销项目授权")
  public Object remove(@PathVariable long id, @PathVariable long uid) {
    return s.member(id, uid, Collections.emptyMap(), true);
  }

  @PostMapping("/tenants/{id}/orders")
  @ApiOperation("创建人工开通订单")
  public Object order(@PathVariable long id, @RequestBody Map<String, Object> b) {
    return s.order(id, b);
  }

  @PostMapping("/tenants/{id}/actions/{action}")
  @ApiOperation("空间设置、退出、删除及双方确认的所有权转移")
  public Object action(
      @PathVariable long id, @PathVariable String action, @RequestBody Map<String, Object> b) {
    return s.tenantAction(id, action, b);
  }

  @PostMapping("/tenants/{id}/invitations/{iid}/resend")
  @ApiOperation("重新发送邀请并轮换代码")
  public Object resend(@PathVariable long id, @PathVariable long iid) {
    return s.invitation(id, iid, true);
  }

  @DeleteMapping("/tenants/{id}/invitations/{iid}")
  @ApiOperation("撤销邀请并释放席位")
  public Object cancelInvite(@PathVariable long id, @PathVariable long iid) {
    return s.invitation(id, iid, false);
  }

  @PostMapping("/tenants/{id}/orders/{oid}/cancel")
  @ApiOperation("取消本空间待开通订单")
  public Object cancelOrder(@PathVariable long id, @PathVariable long oid) {
    return s.cancelOrder(id, oid);
  }

  @GetMapping("/admin/{resource}")
  @ApiOperation("平台管理资源")
  public Object admin(@PathVariable String resource) {
    return s.admin(resource);
  }

  @PatchMapping("/admin/{resource}/{id}")
  @ApiOperation("平台管理员操作")
  public Object adminUpdate(
      @PathVariable String resource, @PathVariable long id, @RequestBody Map<String, Object> b) {
    return s.adminUpdate(resource, id, b);
  }
}
