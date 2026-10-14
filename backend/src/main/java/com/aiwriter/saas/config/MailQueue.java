package com.aiwriter.saas.config;

import static com.aiwriter.persistence.Store.*;

import com.aiwriter.persistence.Store;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 未配置邮件发送器时保留队列，不假报验证邮件发送成功。客服mailto不依赖此队列。 */
@Component
public class MailQueue {
  private final Store db;
  private final ObjectProvider<JavaMailSender> sender;
  private final String from;

  public MailQueue(
      Store d, ObjectProvider<JavaMailSender> s, @Value("${saas.mail-from:}") String from) {
    db = d;
    sender = s;
    this.from = from;
  }

  @Scheduled(fixedDelay = 15000)
  public void send() {
    JavaMailSender mail = sender.getIfAvailable();
    if (mail == null || from.isEmpty()) return;
    // 进程崩溃后的领取记录可重试；最终失败不长久保留原始验证代码。
    db.sql.update(
        "UPDATE saas_email SET status='PENDING' WHERE status='PROCESSING' AND attempt<5 AND updated_at<?",
        new Date(System.currentTimeMillis() - 600000L));
    db.sql.update(
        "UPDATE saas_email SET status='FAILED',content='[发送失败]' WHERE status='PROCESSING' AND attempt>=5 AND updated_at<?",
        new Date(System.currentTimeMillis() - 600000L));
    List<Map<String, Object>> rows =
        db.query(
            "SELECT * FROM saas_email WHERE status='PENDING' AND attempt<5 ORDER BY id LIMIT 1");
    if (rows.isEmpty()) return;
    Map<String, Object> x = rows.get(0);
    if (db.sql.update(
            "UPDATE saas_email SET status='PROCESSING',attempt=attempt+1 WHERE id=? AND status='PENDING'",
            x.get("id"))
        != 1) return;
    try {
      SimpleMailMessage m = new SimpleMailMessage();
      m.setFrom(from);
      m.setTo(str(x, "email", ""));
      m.setSubject(str(x, "subject", ""));
      m.setText(str(x, "content", ""));
      mail.send(m);
      db.sql.update(
          "UPDATE saas_email SET status='SENT',error_code=NULL,content='[已发送]' WHERE id=?",
          x.get("id"));
    } catch (Exception e) {
      db.sql.update(
          "UPDATE saas_email SET status=?,content=?,error_code='SMTP_FAILED' WHERE id=?",
          num(x, "attempt", 0) >= 4 ? "FAILED" : "PENDING",
          num(x, "attempt", 0) >= 4 ? "[发送失败]" : x.get("content"),
          x.get("id"));
    }
  }
}
