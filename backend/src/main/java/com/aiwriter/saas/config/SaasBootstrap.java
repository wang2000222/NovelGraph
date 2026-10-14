package com.aiwriter.saas.config;

import static com.aiwriter.persistence.Store.*;

import com.aiwriter.persistence.Store;
import com.aiwriter.saas.security.Passwords;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.stereotype.Component;

/** 幂等创建套餐和明确配置的平台管理员，禁止首次注册抢占管理员。 */
@Component
public class SaasBootstrap implements ApplicationRunner {
  private final Store db;
  private final String email, password, initialPlans;

  public SaasBootstrap(
      Store db,
      @Value("${saas.admin-email:}") String email,
      @Value("${saas.admin-password:}") String password,
      @Value("${saas.initial-plans}") String initialPlans) {
    this.db = db;
    this.email = email;
    this.password = password;
    this.initialPlans = initialPlans;
  }

  public void run(ApplicationArguments args) {
    for (Map<String, Object> p : db.list(initialPlans)) {
      if (db.query("SELECT id FROM saas_plan WHERE code=?", required(p, "code")).isEmpty())
        db.insert(
            "saas_plan",
            map(
                "code",
                p.get("code"),
                "name",
                required(p, "name"),
                "monthly_price",
                p.get("monthlyPrice"),
                "limits",
                db.json(db.obj(p.get("limits")))));
    }
    if (db.query("SELECT id FROM saas_setting WHERE setting_key='support_email'").isEmpty())
      db.insert("saas_setting", map("setting_key", "support_email", "value", "support@example.com"));
    if (!email.isEmpty()
        && !password.isEmpty()
        && db.query("SELECT id FROM saas_user WHERE email=?", email.toLowerCase(Locale.ROOT))
            .isEmpty()) {
      if (password.length() < 12) throw new IllegalStateException("NG_ADMIN_PASSWORD至少12字符");
      long uid =
          db.insert(
              "saas_user",
              map(
                  "email",
                  email.toLowerCase(Locale.ROOT),
                  "name",
                  "平台管理员",
                  "password_hash",
                  Passwords.hash(password),
                  "verified",
                  1,
                  "platform_role",
                  "ADMIN"));
      long tid =
          db.insert("saas_tenant", map("name", "管理员创作空间", "kind", "PERSONAL", "owner_id", uid));
      db.insert("saas_member", map("tenant_id", tid, "user_id", uid, "role", "OWNER"));
    }
  }
}
