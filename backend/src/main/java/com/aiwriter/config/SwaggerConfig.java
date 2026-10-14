package com.aiwriter.config;

import com.github.xiaoymin.knife4j.spring.annotations.EnableKnife4j;
import java.util.*;
import org.springframework.context.annotation.*;
import springfox.documentation.builders.*;
import springfox.documentation.service.*;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

/** Knife4j的default分组；登录后用/me返回的CSRF值调试写入接口。 */
@Configuration
@EnableSwagger2
@EnableKnife4j
public class SwaggerConfig {
  @Bean
  public Docket novelgraphApi() {
    return new Docket(DocumentationType.SWAGGER_2)
        .groupName("default")
        .apiInfo(
            new ApiInfoBuilder()
                .title("NovelGraph SaaS接口")
                .description(
                    "Java 8 / MyBatis-Plus。使用账号登录获得HttpOnly会话；写入请求携带/me返回的X-CSRF-Token。租户、项目权限及秘密权限由服务器校验。")
                .version("1.0.0")
                .build())
        .select()
        .apis(RequestHandlerSelectors.basePackage("com.aiwriter.saas.controller"))
        .paths(PathSelectors.any())
        .build()
        .securitySchemes(Arrays.asList(new ApiKey("X-CSRF-Token", "X-CSRF-Token", "header")));
  }
}
