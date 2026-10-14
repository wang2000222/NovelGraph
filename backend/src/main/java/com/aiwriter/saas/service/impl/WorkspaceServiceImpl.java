package com.aiwriter.saas.service.impl;

import static com.aiwriter.persistence.Store.*;

import com.aiwriter.common.Problem;
import com.aiwriter.persistence.Store;
import com.aiwriter.saas.security.*;
import com.aiwriter.saas.service.WorkspaceService;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 角色、租户、项目授权与额度在服务器计算，不接受前端声明权限。 */
@Service
public class WorkspaceServiceImpl implements WorkspaceService {
  private final Store db;

  public WorkspaceServiceImpl(Store db) {
    this.db = db;
  }

  public Map<String, Object> access(long tenant) {
    List<Map<String, Object>> r =
        db.query(
            "SELECT t.*,m.role FROM saas_tenant t JOIN saas_member m ON m.tenant_id=t.id WHERE t.id=? AND m.user_id=? AND t.status='ACTIVE'",
            tenant,
            SaasContext.id());
    if (r.isEmpty()) throw new Problem(404, "TENANT_NOT_FOUND", "空间不存在或无权限");
    SaasContext.tenant(tenant);
    return r.get(0);
  }

  public static boolean manager(Map<String, Object> t) {
    return Arrays.asList("OWNER", "ADMIN").contains(t.get("role"));
  }

  private Map<String, Object> manage(long tenant) {
    Map<String, Object> t = access(tenant);
    if (!manager(t)) throw new Problem(403, "ROLE_DENIED", "需要空间管理员权限");
    return t;
  }

  public Map<String, Object> project(long id, String operation) {
    List<Map<String, Object>> r =
        db.query(
            "SELECT p.*,m.role AS tenant_role FROM ng_project p JOIN saas_member m ON m.tenant_id=p.tenant_id JOIN saas_tenant t ON t.id=p.tenant_id WHERE p.id=? AND m.user_id=? AND t.status='ACTIVE'",
            id,
            SaasContext.id());
    if (r.isEmpty()) throw new Problem(404, "PROJECT_NOT_FOUND", "项目不存在或无权限");
    Map<String, Object> p = r.get(0);
    boolean all = Arrays.asList("OWNER", "ADMIN").contains(p.get("tenant_role"));
    List<Map<String, Object>> rights =
        db.query(
            "SELECT * FROM saas_project_member WHERE project_id=? AND user_id=? AND tenant_id=?",
            id,
            SaasContext.id(),
            p.get("tenant_id"));
    if (!all && rights.isEmpty()) throw new Problem(404, "PROJECT_NOT_FOUND", "项目不存在或无权限");
    Map<String, Object> right =
        all ? map("role", "ADMIN", "can_export", 1, "can_secrets", 1) : rights.get(0);
    if (operation.equals("EDIT") && !Arrays.asList("ADMIN", "EDITOR").contains(right.get("role")))
      throw new Problem(403, "EDIT_DENIED", "此项目为只读");
    if (operation.equals("ADMIN") && !"ADMIN".equals(right.get("role")))
      throw new Problem(403, "PROJECT_ADMIN_REQUIRED", "需要项目管理员权限");
    if (operation.equals("EXPORT") && num(right, "can_export", 0) != 1)
      throw new Problem(403, "EXPORT_DENIED", "没有导出权限");
    if (operation.equals("SECRET") && num(right, "can_secrets", 0) != 1)
      throw new Problem(403, "SECRET_DENIED", "没有秘密资料权限");
    p.put("rights", right);
    SaasContext.tenant(num(p, "tenant_id", 0));
    return p;
  }

  public Object settings() {
    List<Map<String, Object>> r =
        db.query("SELECT value FROM saas_setting WHERE setting_key='support_email'");
    return map(
        "supportEmail",
        r.isEmpty() ? "support@example.com" : r.get(0).get("value"),
        "plans",
        db.query("SELECT * FROM saas_plan WHERE status='ACTIVE' ORDER BY monthly_price"),
        "billingMode",
        "MANUAL");
  }

  public Object overview(long tenant) {
    Map<String, Object> t = access(tenant);
    Map<String, Object> limits = limits(t);
    Map<String, Object> usage = usage(tenant);
    return map(
        "tenant",
        t,
        "limits",
        limits,
        "usage",
        usage,
        "orders",
        manager(t) || "BILLING".equals(t.get("role"))
            ? db.query(
                "SELECT * FROM saas_order WHERE tenant_id=? ORDER BY id DESC LIMIT 100", tenant)
            : Collections.emptyList(),
        "notifications",
        db.query(
            "SELECT * FROM saas_notification WHERE user_id=? AND (tenant_id=? OR tenant_id IS NULL) ORDER BY id DESC LIMIT 100",
            SaasContext.id(),
            tenant));
  }

  @Transactional
  public Object create(Map<String, Object> b) {
    String name = required(b, "name");
    if (name.length() > 100) throw new Problem(400, "NAME_LONG", "空间名称过长");
    long id =
        db.insert("saas_tenant", map("name", name, "owner_id", SaasContext.id(), "kind", "TEAM"));
    db.insert("saas_member", map("tenant_id", id, "user_id", SaasContext.id(), "role", "OWNER"));
    audit(id, "TENANT_CREATE", id);
    return access(id);
  }

  public Object members(long tenant) {
    manage(tenant);
    return map(
        "members",
        db.query(
            "SELECT m.*,u.name,u.email FROM saas_member m JOIN saas_user u ON u.id=m.user_id WHERE m.tenant_id=? ORDER BY m.id",
            tenant),
        "invitations",
        db.query(
            "SELECT id,email,role,status,expires_at FROM saas_invitation WHERE tenant_id=? ORDER BY id DESC",
            tenant));
  }

  @Transactional
  public Object invite(long tenant, Map<String, Object> b) {
    Map<String, Object> t = manage(tenant);
    String role = str(b, "role", "MEMBER");
    if (!Arrays.asList("MEMBER", "ADMIN", "BILLING").contains(role)
        || (!"OWNER".equals(t.get("role")) && !role.equals("MEMBER")))
      throw new Problem(403, "INVITE_ROLE", "不允许邀请该角色");
    quota(tenant, "seats", 1);
    String email = AuthServiceImpl.email(b);
    if (!db.query(
            "SELECT id FROM saas_invitation WHERE tenant_id=? AND email=? AND status='PENDING' AND expires_at>CURRENT_TIMESTAMP",
            tenant,
            email)
        .isEmpty()) throw new Problem(409, "INVITE_EXISTS", "邀请已发出");
    String token = Passwords.token();
    long id =
        db.insert(
            "saas_invitation",
            map(
                "tenant_id",
                tenant,
                "email",
                email,
                "role",
                role,
                "token_hash",
                Passwords.digest(token),
                "expires_at",
                new Date(System.currentTimeMillis() + 7 * 86400000L),
                "created_by",
                SaasContext.id()));
    db.insert(
        "saas_email",
        map(
            "email",
            email,
            "subject",
            "NovelGraph 团队邀请",
            "content",
            "登录受邀邮箱对应账号后，在团队页面输入邀请代码：\n" + token + "\n空间：" + t.get("name")));
    audit(tenant, "INVITE", id);
    return map("id", id, "queued", true);
  }

  @Transactional
  public Object accept(Map<String, Object> b) {
    List<Map<String, Object>> r =
        db.query(
            "SELECT * FROM saas_invitation WHERE token_hash=? AND email=? AND status='PENDING' AND expires_at>CURRENT_TIMESTAMP",
            Passwords.digest(required(b, "token")),
            SaasContext.user().get("email"));
    if (r.isEmpty()) throw new Problem(400, "INVITE_INVALID", "邀请失效或邮箱不匹配");
    Map<String, Object> i = r.get(0);
    long tenant = num(i, "tenant_id", 0);
    db.query("SELECT id FROM saas_tenant WHERE id=? FOR UPDATE", tenant);
    Map<String, Object> acceptedTenant = db.get("saas_tenant", tenant);
    if (!"ACTIVE".equals(acceptedTenant.get("status")))
      throw new Problem(403, "TENANT_FROZEN", "空间已冻结");
    if (num(usage(tenant), "seats", 0) > num(limits(acceptedTenant), "seats", 0))
      throw new Problem(409, "QUOTA_EXCEEDED", "当前套餐席位不足");
    r =
        db.query(
            "SELECT * FROM saas_invitation WHERE id=? AND status='PENDING' AND expires_at>CURRENT_TIMESTAMP FOR UPDATE",
            i.get("id"));
    if (r.isEmpty()) throw new Problem(409, "INVITE_USED", "邀请已使用");
    if (!db.query(
            "SELECT id FROM saas_member WHERE tenant_id=? AND user_id=?", tenant, SaasContext.id())
        .isEmpty()) throw new Problem(409, "ALREADY_MEMBER", "已经是成员");
    db.insert(
        "saas_member",
        map("tenant_id", tenant, "user_id", SaasContext.id(), "role", i.get("role")));
    db.sql.update("UPDATE saas_invitation SET status='ACCEPTED' WHERE id=?", i.get("id"));
    audit(tenant, "INVITE_ACCEPT", SaasContext.id());
    return map("tenantId", tenant);
  }

  @Transactional
  public Object member(long tenant, long user, Map<String, Object> b, boolean delete) {
    Map<String, Object> t = manage(tenant);
    if (num(t, "owner_id", 0) == user) throw new Problem(409, "OWNER_PROTECTED", "所有者不可移除或降级");
    List<Map<String, Object>> memberRows =
        db.query("SELECT role FROM saas_member WHERE tenant_id=? AND user_id=?", tenant, user);
    if (memberRows.isEmpty()) throw new Problem(404, "MEMBER_MISSING", "成员不存在");
    if (delete && !"OWNER".equals(t.get("role")) && !"MEMBER".equals(memberRows.get(0).get("role")))
      throw new Problem(403, "OWNER_REQUIRED", "仅所有者可移除管理员或财务");
    if (delete) {
      db.sql.update(
          "DELETE FROM saas_project_member WHERE tenant_id=? AND user_id=?", tenant, user);
      db.sql.update("DELETE FROM saas_member WHERE tenant_id=? AND user_id=?", tenant, user);
    } else {
      if (!"OWNER".equals(t.get("role"))) throw new Problem(403, "OWNER_REQUIRED", "仅所有者可调整团队角色");
      String role = required(b, "role");
      if (!Arrays.asList("ADMIN", "MEMBER", "BILLING").contains(role))
        throw new Problem(400, "ROLE_INVALID", "角色无效");
      db.sql.update(
          "UPDATE saas_member SET role=? WHERE tenant_id=? AND user_id=?", role, tenant, user);
    }
    audit(tenant, delete ? "MEMBER_REMOVE" : "MEMBER_ROLE", user);
    return map("success", true);
  }

  public Map<String, Object> limits(Map<String, Object> t) {
    String code = str(t, "plan_code", "FREE");
    if (t.get("subscription_until") instanceof Date
        && ((Date) t.get("subscription_until")).before(new Date())) code = "FREE";
    if (!"FREE".equals(code)) {
      List<Map<String, Object>> paid =
          db.query(
              "SELECT entitlements FROM saas_order WHERE tenant_id=? AND plan_code=? AND status='PAID' ORDER BY paid_at DESC,id DESC LIMIT 1",
              t.get("id"),
              code);
      if (!paid.isEmpty()) return completeLimits(db.obj(paid.get(0).get("entitlements")));
    }
    List<Map<String, Object>> r = db.query("SELECT limits FROM saas_plan WHERE code=?", code);
    if (r.isEmpty()) throw new Problem(503, "PLAN_UNAVAILABLE", "套餐配置不可用");
    return completeLimits(db.obj(r.get(0).get("limits")));
  }

  private Map<String, Object> completeLimits(Map<String, Object> limits) {
    limits.putIfAbsent("associations", 10000);
    limits.putIfAbsent("storageBytes", 100 * 1024 * 1024);
    return limits;
  }

  public Map<String, Object> usage(long tenant) {
    StringJoiner union = new StringJoiner(" UNION ALL ");
    List<Object> params = new ArrayList<>();
    for (Map.Entry<String, String> x : WorldServiceImpl.TABLES.entrySet()) {
      String category = Arrays.asList("characters", "events").contains(x.getKey()) ? "E" : "A";
      union.add(
          "SELECT COUNT(*) AS n,COALESCE(SUM(OCTET_LENGTH(name)+COALESCE(OCTET_LENGTH(description),0)+COALESCE(OCTET_LENGTH(data),0)),0) AS bytes,'"
              + category
              + "' AS category FROM "
              + x.getValue()
              + " WHERE tenant_id=?");
      params.add(tenant);
    }
    Map<String, Object> stats =
        db.query(
                "SELECT COALESCE(SUM(CASE WHEN category='E' THEN n ELSE 0 END),0) AS entities,COALESCE(SUM(CASE WHEN category='A' THEN n ELSE 0 END),0) AS associations,COALESCE(SUM(bytes),0) AS bytes FROM ("
                    + union
                    + ") counts",
                params.toArray())
            .get(0);
    return map(
        "projects",
        count("SELECT COUNT(*) FROM ng_project WHERE tenant_id=?", tenant),
        "seats",
        count("SELECT COUNT(*) FROM saas_member WHERE tenant_id=?", tenant)
            + count(
                "SELECT COUNT(*) FROM saas_invitation WHERE tenant_id=? AND status='PENDING' AND expires_at>CURRENT_TIMESTAMP",
                tenant),
        "entities",
        stats.get("entities"),
        "associations",
        stats.get("associations"),
        "storageBytes",
        num(stats, "bytes", 0)
            + count(
                "SELECT COALESCE(SUM(OCTET_LENGTH(content)),0) FROM ng_snapshot WHERE tenant_id=?",
                tenant),
        "snapshots",
        count("SELECT COUNT(*) FROM ng_snapshot WHERE tenant_id=?", tenant));
  }

  private long count(String sql, long tenant) {
    return db.sql.queryForObject(sql, Long.class, tenant);
  }

  public void quota(long tenant, String key, long increase) {
    db.query("SELECT id FROM saas_tenant WHERE id=? FOR UPDATE", tenant);
    Map<String, Object> t = access(tenant);
    long max = num(limits(t), key, 0), used = num(usage(tenant), key, 0);
    if (used + increase > max)
      throw new Problem(409, "QUOTA_EXCEEDED", "套餐" + key + "额度不足，可删除资料或升级套餐");
  }

  @Transactional
  public Object order(long tenant, Map<String, Object> b) {
    db.query("SELECT id FROM saas_tenant WHERE id=? FOR UPDATE", tenant);
    Map<String, Object> t = access(tenant);
    if (!Arrays.asList("OWNER", "ADMIN", "BILLING").contains(t.get("role")))
      throw new Problem(403, "BILLING_DENIED", "无账单权限");
    String key = required(b, "requestKey");
    List<Map<String, Object>> prior =
        db.query("SELECT * FROM saas_order WHERE tenant_id=? AND request_key=?", tenant, key);
    if (!prior.isEmpty()) return prior.get(0);
    long months = num(b, "months", 1);
    if (months < 1 || months > 12) throw new Problem(400, "MONTHS_INVALID", "订阅周期1至12个月");
    List<Map<String, Object>> p =
        db.query(
            "SELECT * FROM saas_plan WHERE code=? AND status='ACTIVE'", required(b, "planCode"));
    if (p.isEmpty()) throw new Problem(404, "PLAN_NOT_FOUND", "套餐不存在");
    Map<String, Object> plan = p.get(0);
    long id =
        db.insert(
            "saas_order",
            map(
                "tenant_id",
                tenant,
                "plan_code",
                plan.get("code"),
                "amount",
                new BigDecimal(plan.get("monthly_price").toString())
                    .multiply(BigDecimal.valueOf(months)),
                "months",
                months,
                "request_key",
                key,
                "entitlements",
                plan.get("limits")));
    audit(tenant, "ORDER_CREATE", id);
    return db.get("saas_order", id);
  }

  public Object admin(String resource) {
    requireAdmin();
    switch (resource) {
      case "users":
        return db.query(
            "SELECT id,email,name,verified,platform_role,status,created_at FROM saas_user ORDER BY id DESC LIMIT 500");
      case "tenants":
        return db.query("SELECT * FROM saas_tenant ORDER BY id DESC LIMIT 500");
      case "orders":
        return db.query("SELECT * FROM saas_order ORDER BY id DESC LIMIT 500");
      case "plans":
        return db.query("SELECT * FROM saas_plan ORDER BY id");
      case "audit":
        return db.query("SELECT * FROM saas_audit ORDER BY id DESC LIMIT 500");
      case "stats":
        return map(
            "users",
            db.sql.queryForObject("SELECT COUNT(*) FROM saas_user", Long.class),
            "tenants",
            db.sql.queryForObject(
                "SELECT COUNT(*) FROM saas_tenant WHERE status='ACTIVE'", Long.class),
            "projects",
            db.sql.queryForObject("SELECT COUNT(*) FROM ng_project", Long.class),
            "subscriptions",
            db.sql.queryForObject(
                "SELECT COUNT(*) FROM saas_tenant WHERE subscription_until>CURRENT_TIMESTAMP",
                Long.class),
            "revenue",
            db.sql.queryForObject(
                "SELECT COALESCE(SUM(amount),0) FROM saas_order WHERE status='PAID'",
                BigDecimal.class),
            "mailPending",
            db.sql.queryForObject(
                "SELECT COUNT(*) FROM saas_email WHERE status='PENDING'", Long.class));
      case "mail":
        return db.query(
            "SELECT id,email,subject,status,attempt,error_code,created_at FROM saas_email ORDER BY id DESC LIMIT 100");
      default:
        throw new Problem(404, "RESOURCE_UNKNOWN", "资源不存在");
    }
  }

  @Transactional
  public Object adminUpdate(String resource, long id, Map<String, Object> b) {
    requireAdmin();
    switch (resource) {
      case "users":
        String status = required(b, "status");
        if (!Arrays.asList("ACTIVE", "DISABLED").contains(status) || id == SaasContext.id())
          throw new Problem(400, "USER_STATUS", "不能禁用自己或设置未知状态");
        db.sql.update("UPDATE saas_user SET status=? WHERE id=?", status, id);
        db.sql.update("DELETE FROM saas_session WHERE user_id=?", id);
        break;
      case "tenants":
        String state = required(b, "status");
        if (!Arrays.asList("ACTIVE", "FROZEN").contains(state))
          throw new Problem(400, "TENANT_STATUS", "状态无效");
        db.sql.update("UPDATE saas_tenant SET status=? WHERE id=?", state, id);
        break;
      case "orders":
        Map<String, Object> o =
            db.query("SELECT * FROM saas_order WHERE id=? FOR UPDATE", id).stream()
                .findFirst()
                .orElseThrow(() -> new Problem(404, "ORDER_MISSING", "订单不存在"));
        if (!"PENDING".equals(o.get("status"))) return o;
        long tid = num(o, "tenant_id", 0);
        db.query("SELECT id FROM saas_tenant WHERE id=? FOR UPDATE", tid);
        String requested = str(b, "status", "PAID");
        if ("CANCELLED".equals(requested)) {
          db.sql.update("UPDATE saas_order SET status='CANCELLED' WHERE id=?", id);
          break;
        }
        if (!"PAID".equals(requested)) throw new Problem(400, "ORDER_STATUS", "订单状态无效");
        Map<String, Object> tenantRow = db.get("saas_tenant", tid);
        long begin = System.currentTimeMillis();
        if (o.get("plan_code").equals(tenantRow.get("plan_code"))
            && tenantRow.get("subscription_until") instanceof Date)
          begin = Math.max(begin, ((Date) tenantRow.get("subscription_until")).getTime());
        Calendar expiry = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"));
        expiry.setTimeInMillis(begin);
        expiry.add(Calendar.MONTH, (int) num(o, "months", 1));
        Date until = expiry.getTime();
        db.sql.update(
            "UPDATE saas_tenant SET plan_code=?,subscription_until=?,version=version+1 WHERE id=?",
            o.get("plan_code"),
            until,
            tid);
        db.sql.update(
            "UPDATE saas_order SET status='PAID',paid_at=CURRENT_TIMESTAMP WHERE id=?", id);
        db.insert(
            "saas_notification",
            map(
                "tenant_id",
                tid,
                "user_id",
                db.get("saas_tenant", tid).get("owner_id"),
                "title",
                "订阅已人工开通",
                "content",
                "管理员已确认订单并开通套餐。"));
        break;
      case "plans":
        Map<String, Object> oldPlan = db.get("saas_plan", id);
        db.insert(
            "saas_audit",
            map(
                "user_id",
                SaasContext.id(),
                "action",
                "PLAN_VERSION",
                "target_id",
                id,
                "detail",
                db.json(oldPlan)));
        Map<String, Object> limits = db.obj(b.get("limits"));
        for (String limitKey : Arrays.asList("projects", "seats", "entities", "snapshots"))
          if (!limits.containsKey(limitKey)
              || num(limits, limitKey, -1) < 1
              || num(limits, limitKey, 0) > 10000000)
            throw new Problem(400, "PLAN_LIMIT", "套餐额度须为1至10000000的整数");
        BigDecimal price = new BigDecimal(required(b, "monthlyPrice"));
        if (price.signum() < 0 || price.compareTo(new BigDecimal("1000000")) > 0)
          throw new Problem(400, "PLAN_PRICE", "价格范围无效");
        db.sql.update(
            "UPDATE saas_plan SET name=?,monthly_price=?,limits=?,version=version+1 WHERE id=?",
            required(b, "name"),
            price,
            db.json(limits),
            id);
        break;
      case "settings":
        String email = AuthServiceImpl.email(b);
        db.sql.update("UPDATE saas_setting SET value=? WHERE setting_key='support_email'", email);
        break;
      default:
        throw new Problem(400, "ADMIN_ACTION", "不支持此操作");
    }
    audit(0, "ADMIN_" + resource, id);
    return map("success", true);
  }

  @Transactional
  public Object invitation(long tenant, long id, boolean resend) {
    Map<String, Object> t = manage(tenant);
    db.query("SELECT id FROM saas_tenant WHERE id=? FOR UPDATE", tenant);
    Map<String, Object> i =
        db.query("SELECT * FROM saas_invitation WHERE tenant_id=? AND id=? FOR UPDATE", tenant, id)
            .stream()
            .findFirst()
            .orElseThrow(() -> new Problem(404, "INVITE_MISSING", "邀请不存在"));
    if (!"OWNER".equals(t.get("role")) && !"MEMBER".equals(i.get("role")))
      throw new Problem(403, "OWNER_REQUIRED", "仅所有者可操作该邀请");
    if (!"PENDING".equals(i.get("status"))) throw new Problem(409, "INVITE_STATE", "邀请已结束");
    if (resend) {
      if (((Date) i.get("expires_at")).before(new Date())) quota(tenant, "seats", 1);
      String token = Passwords.token();
      db.sql.update(
          "UPDATE saas_invitation SET token_hash=?,expires_at=? WHERE tenant_id=? AND id=?",
          Passwords.digest(token),
          new Date(System.currentTimeMillis() + 7 * 86400000L),
          tenant,
          id);
      db.insert(
          "saas_email",
          map(
              "email",
              i.get("email"),
              "subject",
              "NovelGraph 团队邀请",
              "content",
              "登录受邀账号，在团队页面输入新邀请代码：" + token));
    } else
      db.sql.update(
          "UPDATE saas_invitation SET status='REVOKED' WHERE tenant_id=? AND id=?", tenant, id);
    audit(tenant, resend ? "INVITE_RESEND" : "INVITE_REVOKE", id);
    return map("success", true);
  }

  @Transactional
  public Object cancelOrder(long tenant, long id) {
    Map<String, Object> t = access(tenant);
    if (!Arrays.asList("OWNER", "ADMIN", "BILLING").contains(t.get("role")))
      throw new Problem(403, "BILLING_DENIED", "无账单权限");
    if (db.sql.update(
            "UPDATE saas_order SET status='CANCELLED' WHERE tenant_id=? AND id=? AND status='PENDING'",
            tenant,
            id)
        != 1) throw new Problem(409, "ORDER_STATE", "仅可取消待开通订单");
    audit(tenant, "ORDER_CANCEL", id);
    return map("success", true);
  }

  @Transactional
  public Object tenantAction(long tenant, String action, Map<String, Object> b) {
    Map<String, Object> t = access(tenant);
    db.query("SELECT id FROM saas_tenant WHERE id=? FOR UPDATE", tenant);
    t = access(tenant);
    long me = SaasContext.id();
    switch (action) {
      case "leave":
        if ("OWNER".equals(t.get("role")))
          throw new Problem(409, "OWNER_PROTECTED", "请先转移所有权或删除空间");
        db.sql.update(
            "DELETE FROM saas_project_member WHERE tenant_id=? AND user_id=?", tenant, me);
        db.sql.update("DELETE FROM saas_member WHERE tenant_id=? AND user_id=?", tenant, me);
        break;
      case "settings":
        if (!"OWNER".equals(t.get("role"))) throw new Problem(403, "OWNER_REQUIRED", "需要所有者权限");
        db.sql.update(
            "UPDATE saas_tenant SET name=?,member_can_create=?,version=version+1 WHERE id=?",
            required(b, "name"),
            Boolean.TRUE.equals(b.get("memberCanCreate")) ? 1 : 0,
            tenant);
        break;
      case "transfer":
        if (!"OWNER".equals(t.get("role")) || !"TEAM".equals(t.get("kind")))
          throw new Problem(403, "OWNER_REQUIRED", "仅团队所有者可转移所有权");
        long user = num(b, "userId", 0);
        if (user == me
            || db.query(
                    "SELECT m.id FROM saas_member m JOIN saas_user u ON u.id=m.user_id WHERE m.tenant_id=? AND m.user_id=? AND u.status='ACTIVE'",
                    tenant,
                    user)
                .isEmpty()) throw new Problem(400, "TRANSFER_MEMBER", "接收方须为有效团队成员");
        db.sql.update(
            "UPDATE saas_tenant SET pending_owner_id=?,pending_owner_at=CURRENT_TIMESTAMP WHERE id=?",
            user,
            tenant);
        db.insert(
            "saas_notification",
            map(
                "tenant_id",
                tenant,
                "user_id",
                user,
                "title",
                "团队所有权转移邀请",
                "content",
                "请在空间页面确认是否接管团队。"));
        break;
      case "accept-transfer":
        if (num(t, "pending_owner_id", 0) != me
            || !(t.get("pending_owner_at") instanceof Date)
            || System.currentTimeMillis() - ((Date) t.get("pending_owner_at")).getTime()
                > 86400000L) throw new Problem(403, "TRANSFER_INVALID", "转移申请不存在或已超过24小时");
        db.sql.update(
            "UPDATE saas_member SET role='ADMIN' WHERE tenant_id=? AND user_id=?",
            tenant,
            t.get("owner_id"));
        db.sql.update(
            "UPDATE saas_member SET role='OWNER' WHERE tenant_id=? AND user_id=?", tenant, me);
        db.sql.update(
            "UPDATE saas_tenant SET owner_id=?,pending_owner_id=NULL,pending_owner_at=NULL,version=version+1 WHERE id=?",
            me,
            tenant);
        break;
      case "cancel-transfer":
        if (!"OWNER".equals(t.get("role")) && num(t, "pending_owner_id", 0) != me)
          throw new Problem(403, "TRANSFER_INVALID", "无权限取消转移");
        db.sql.update(
            "UPDATE saas_tenant SET pending_owner_id=NULL,pending_owner_at=NULL WHERE id=?",
            tenant);
        break;
      case "delete":
        if (!"OWNER".equals(t.get("role")) || !"TEAM".equals(t.get("kind")))
          throw new Problem(403, "OWNER_REQUIRED", "仅团队所有者可删除团队");
        if (!Objects.equals(t.get("name"), b.get("confirmName")))
          throw new Problem(400, "CONFIRM_NAME", "请填写完整空间名称");
        if (!db.query("SELECT id FROM saas_order WHERE tenant_id=? AND status='PENDING'", tenant)
            .isEmpty()) throw new Problem(409, "ORDER_PENDING", "请先取消未完成订单");
        for (String child :
            Arrays.asList(
                "saas_order", "saas_invitation", "saas_notification", "saas_project_member"))
          db.sql.update("DELETE FROM " + child + " WHERE tenant_id=?", tenant);
        db.sql.update("UPDATE saas_audit SET tenant_id=NULL WHERE tenant_id=?", tenant);
        db.sql.update("DELETE FROM ng_project WHERE tenant_id=?", tenant);
        db.sql.update("DELETE FROM saas_tenant WHERE id=?", tenant);
        audit(0, "TENANT_DELETE", tenant);
        return map("deleted", true);
      default:
        throw new Problem(400, "TENANT_ACTION", "空间操作无效");
    }
    audit(tenant, "TENANT_" + action.toUpperCase(Locale.ROOT), tenant);
    return map("success", true);
  }

  private void requireAdmin() {
    if (!SaasContext.admin()) throw new Problem(403, "PLATFORM_ADMIN_REQUIRED", "需要平台管理员权限");
  }

  public void audit(long tenant, String action, long target) {
    db.insert(
        "saas_audit",
        map(
            "tenant_id",
            tenant == 0 ? null : tenant,
            "user_id",
            SaasContext.id(),
            "action",
            action,
            "target_id",
            target));
  }
}
