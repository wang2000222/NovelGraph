package com.aiwriter.config;

import com.aiwriter.saas.security.SaasContext;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.context.annotation.*;

/** MyBatis-Plus分页配置；实体按数据库下划线字段映射。 */
@Configuration
public class MybatisConfig {
  @Bean
  public MybatisPlusInterceptor mybatisPlusInterceptor() {
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    // 已授权空间由服务层设置；基础实体 Mapper 再次限制租户。
    // 平台全局表及未登录的账号初始化不使用租户过滤。
    interceptor.addInnerInterceptor(
        new TenantLineInnerInterceptor(
            new TenantLineHandler() {
              public Expression getTenantId() {
                return new LongValue(SaasContext.tenant());
              }

              public boolean ignoreTable(String table) {
                if (SaasContext.tenant() == null) return true;
                return java.util.Arrays.asList(
                        "saas_user",
                        "saas_session",
                        "saas_tenant",
                        "saas_plan",
                        "ng_story_genre",
                        "saas_email",
                        "saas_verification",
                        "saas_setting",
                        "saas_audit",
                        "saas_notification")
                    .contains(table.toLowerCase(java.util.Locale.ROOT));
              }
            }));
    PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
    pagination.setMaxLimit(1000L);
    interceptor.addInnerInterceptor(pagination);
    return interceptor;
  }
}
