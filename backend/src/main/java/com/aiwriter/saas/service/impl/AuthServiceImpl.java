package com.aiwriter.saas.service.impl;

import static com.aiwriter.persistence.Store.*;

import com.aiwriter.common.Problem;
import com.aiwriter.persistence.Store;
import com.aiwriter.saas.security.*;
import com.aiwriter.saas.service.AuthService;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 账号用例：验证邮件排队、会话吊销和重置，不泄露账号是否存在。 */
@Service
public class AuthServiceImpl implements AuthService {
  private final Store db;
  private final boolean verifyEmail;
  private final String publicUrl;

  public AuthServiceImpl(
      Store db,
      @Value("${saas.verify-email:true}") boolean verifyEmail,
      @Value("${saas.public-url:http://localhost:5173}") String publicUrl) {
    this.db = db;
    this.verifyEmail = verifyEmail;
    this.publicUrl = publicUrl;
  }

  public static String email(Map<String, Object> b) {
    String e = required(b, "email").toLowerCase(Locale.ROOT);
    if (e.length() > 200 || !e.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
      throw new Problem(400, "EMAIL_INVALID", "请输入有效邮箱");
    return e;
  }

  public static String password(Map<String, Object> b) {
    String p = required(b, "password");
    if (p.length() < 10 || p.length() > 200)
      throw new Problem(400, "PASSWORD_WEAK", "密码需10至200个字符");
    return p;
  }

  @Transactional
  public Map<String, Object> register(Map<String, Object> b) {
    String e = email(b);
    if (!db.query("SELECT id FROM saas_user WHERE email=?", e).isEmpty())
      throw new Problem(409, "EMAIL_EXISTS", "此邮箱已注册，请登录或找回密码");
    long id =
        db.insert(
            "saas_user",
            map(
                "email",
                e,
                "name",
                str(b, "name", e.split("@")[0]),
                "password_hash",
                Passwords.hash(password(b)),
                "verified",
                verifyEmail ? 0 : 1));
    long tenant =
        db.insert(
            "saas_tenant",
            map("name", str(b, "name", "我的") + "的创作空间", "kind", "PERSONAL", "owner_id", id));
    db.insert("saas_member", map("tenant_id", tenant, "user_id", id, "role", "OWNER"));
    if (verifyEmail) verification(id, e, "VERIFY");
    return map("registered", true, "verificationRequired", verifyEmail);
  }

  @Transactional
  public Map<String, Object> login(Map<String, Object> b) {
    List<Map<String, Object>> rows = db.query("SELECT * FROM saas_user WHERE email=?", email(b));
    if (rows.isEmpty()
        || !Passwords.check(required(b, "password"), str(rows.get(0), "password_hash", "")))
      throw new Problem(401, "LOGIN_INVALID", "邮箱或密码错误");
    Map<String, Object> u = rows.get(0);
    if (!"ACTIVE".equals(u.get("status"))) throw new Problem(403, "USER_DISABLED", "账号已停用");
    if (num(u, "verified", 0) != 1) throw new Problem(403, "VERIFY_REQUIRED", "请先验证邮箱");
    String token = Passwords.token(), csrf = Passwords.token();
    db.insert(
        "saas_session",
        map(
            "user_id",
            u.get("id"),
            "token_hash",
            Passwords.digest(token),
            "csrf",
            csrf,
            "expires_at",
            new java.util.Date(System.currentTimeMillis() + 7 * 86400000L)));
    return map("token", token, "csrf", csrf, "user", safe(u));
  }

  public Map<String, Object> authenticate(String token) {
    List<Map<String, Object>> r =
        db.query(
            "SELECT u.*,s.csrf,s.id AS session_id FROM saas_session s JOIN saas_user u ON u.id=s.user_id WHERE s.token_hash=? AND s.expires_at>CURRENT_TIMESTAMP AND u.status='ACTIVE' AND u.verified=1",
            Passwords.digest(token));
    if (r.isEmpty()) throw new Problem(401, "SESSION_EXPIRED", "登录已过期，请重新登录");
    return r.get(0);
  }

  public Map<String, Object> me() {
    Map<String, Object> u = SaasContext.user();
    return map(
        "user",
        safe(u),
        "csrf",
        u.get("csrf"),
        "tenants",
        db.query(
            "SELECT t.*,m.role FROM saas_member m JOIN saas_tenant t ON t.id=m.tenant_id WHERE m.user_id=? ORDER BY t.id",
            SaasContext.id()));
  }

  public void logout(String token) {
    db.sql.update("DELETE FROM saas_session WHERE token_hash=?", Passwords.digest(token));
  }

  @Transactional
  public void verify(Map<String, Object> b) {
    Map<String, Object> v = consume(required(b, "token"), "VERIFY");
    db.sql.update("UPDATE saas_user SET verified=1 WHERE id=?", v.get("user_id"));
  }

  @Transactional
  public void forgot(Map<String, Object> b) {
    List<Map<String, Object>> r = db.query("SELECT * FROM saas_user WHERE email=?", email(b));
    if (!r.isEmpty()) verification(num(r.get(0), "id", 0), str(r.get(0), "email", ""), "RESET");
  }

  @Transactional
  public void reset(Map<String, Object> b) {
    String p = password(b);
    Map<String, Object> v = consume(required(b, "token"), "RESET");
    db.sql.update(
        "UPDATE saas_user SET password_hash=?,verified=1 WHERE id=?",
        Passwords.hash(p),
        v.get("user_id"));
    db.sql.update("DELETE FROM saas_session WHERE user_id=?", v.get("user_id"));
  }

  private Map<String, Object> consume(String token, String kind) {
    List<Map<String, Object>> r =
        db.query(
            "SELECT * FROM saas_verification WHERE token_hash=? AND kind=? AND expires_at>CURRENT_TIMESTAMP FOR UPDATE",
            Passwords.digest(token),
            kind);
    if (r.isEmpty()) throw new Problem(400, "TOKEN_INVALID", "链接失效或已使用");
    db.sql.update("DELETE FROM saas_verification WHERE id=?", r.get(0).get("id"));
    return r.get(0);
  }

  private void verification(long id, String email, String kind) {
    String t = Passwords.token();
    db.sql.update("DELETE FROM saas_verification WHERE user_id=? AND kind=?", id, kind);
    db.insert(
        "saas_verification",
        map(
            "user_id",
            id,
            "kind",
            kind,
            "token_hash",
            Passwords.digest(t),
            "expires_at",
            new Date(System.currentTimeMillis() + 3600000L)));
    db.insert(
        "saas_email",
        map(
            "email",
            email,
            "subject",
            kind.equals("VERIFY") ? "NovelGraph 邮箱验证" : "NovelGraph 重置密码",
            "content",
            "请打开以下链接完成操作，有效期1小时：\n" + publicUrl + "/login?action=" + kind + "&token=" + t));
  }

  public void revokeOthers() {
    db.sql.update(
        "DELETE FROM saas_session WHERE user_id=? AND id<>?",
        SaasContext.id(),
        SaasContext.user().get("session_id"));
  }

  @Transactional
  public void passwordChange(Map<String, Object> b) {
    Map<String, Object> u = db.get("saas_user", SaasContext.id());
    if (!Passwords.check(required(b, "oldPassword"), str(u, "password_hash", "")))
      throw new Problem(403, "OLD_PASSWORD", "原密码不正确");
    db.sql.update(
        "UPDATE saas_user SET password_hash=? WHERE id=?",
        Passwords.hash(password(b)),
        SaasContext.id());
    db.sql.update("DELETE FROM saas_session WHERE user_id=?", SaasContext.id());
  }

  public static Map<String, Object> safe(Map<String, Object> u) {
    return map(
        "id",
        u.get("id"),
        "name",
        u.get("name"),
        "email",
        u.get("email"),
        "platformRole",
        u.get("platform_role"));
  }
}
