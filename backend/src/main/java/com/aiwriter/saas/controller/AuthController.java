package com.aiwriter.saas.controller;

import static com.aiwriter.persistence.Store.map;

import com.aiwriter.saas.security.SaasFilter;
import com.aiwriter.saas.service.AuthService;
import io.swagger.annotations.*;
import java.util.*;
import javax.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Api(tags = "SaaS 账号")
public class AuthController {
  private final AuthService auth;
  private final boolean secure;

  public AuthController(AuthService a, @Value("${saas.secure-cookie:false}") boolean secure) {
    auth = a;
    this.secure = secure;
  }

  @PostMapping("/auth/register")
  @ApiOperation("注册账号并创建个人空间")
  public Object register(@RequestBody Map<String, Object> b) {
    return auth.register(b);
  }

  @PostMapping("/auth/login")
  @ApiOperation("登录")
  public Object login(@RequestBody Map<String, Object> b, HttpServletResponse res) {
    Map<String, Object> r = auth.login(b);
    res.addHeader(
        "Set-Cookie",
        "ng_session="
            + r.remove("token")
            + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=604800"
            + (secure ? "; Secure" : ""));
    return r;
  }

  @GetMapping("/me")
  @ApiOperation("当前账号和空间")
  public Object me() {
    return auth.me();
  }

  @PostMapping("/auth/revoke-others")
  @ApiOperation("主动注销其他登录会话")
  public Object revoke() {
    auth.revokeOthers();
    return map("success", true);
  }

  @PostMapping("/auth/change-password")
  @ApiOperation("验证旧密码并修改，吊销全部会话")
  public Object change(@RequestBody Map<String, Object> b) {
    auth.passwordChange(b);
    return map("success", true);
  }

  @PostMapping("/auth/logout")
  @ApiOperation("注销当前会话")
  public Object logout(HttpServletRequest req, HttpServletResponse res) {
    auth.logout(SaasFilter.token(req));
    res.addHeader(
        "Set-Cookie",
        "ng_session=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0" + (secure ? "; Secure" : ""));
    return map("success", true);
  }

  @PostMapping("/auth/verify")
  @ApiOperation("验证邮箱")
  public Object verify(@RequestBody Map<String, Object> b) {
    auth.verify(b);
    return map("success", true);
  }

  @PostMapping("/auth/forgot")
  @ApiOperation("申请找回密码")
  public Object forgot(@RequestBody Map<String, Object> b) {
    auth.forgot(b);
    return map("message", "如账号存在，重置邮件将发送至邮箱");
  }

  @PostMapping("/auth/reset")
  @ApiOperation("设置新密码并吊销旧会话")
  public Object reset(@RequestBody Map<String, Object> b) {
    auth.reset(b);
    return map("success", true);
  }
}
