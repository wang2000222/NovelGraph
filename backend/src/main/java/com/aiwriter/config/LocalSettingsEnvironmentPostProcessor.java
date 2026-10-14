package com.aiwriter.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/** 在创建Bean之前加载本地开发凭据，支持IDEA从项目根目录或backend目录直接启动。 */
public class LocalSettingsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    Path working = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
    List<Path> candidates =
        Arrays.asList(
            working.resolve(".local/settings.json"),
            working.resolve("backend/.local/settings.json"));
    for (Path candidate : candidates) {
      if (!Files.isRegularFile(candidate)) continue;
      try {
        Map<String, Object> values =
            new ObjectMapper()
                .readValue(
                    Files.readAllBytes(candidate),
                    new TypeReference<LinkedHashMap<String, Object>>() {});
        // 命令行参数和系统环境变量优先；本地文件只补齐未设置的配置，不输出凭据。
        environment
            .getPropertySources()
            .addLast(new MapPropertySource("writerLocalSettings", values));
        return;
      } catch (Exception e) {
        throw new IllegalStateException("本地配置文件无法读取，请检查 .local/settings.json 的JSON格式", e);
      }
    }
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }
}
