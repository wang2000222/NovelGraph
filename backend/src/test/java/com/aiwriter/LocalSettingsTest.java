package com.aiwriter;

import static org.junit.jupiter.api.Assertions.*;

import com.aiwriter.config.LocalSettingsEnvironmentPostProcessor;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class LocalSettingsTest {
  @TempDir Path temp;

  @Test
  void loadsBothIdeaWorkingDirectoriesAndKeepsOverrides() throws Exception {
    Path backend = temp.resolve("backend");
    Files.createDirectories(backend.resolve(".local"));
    Files.write(
        backend.resolve(".local/settings.json"),
        "{\"NG_ADMIN_EMAIL\":\"local@example.com\"}".getBytes(StandardCharsets.UTF_8));
    String original = System.getProperty("user.dir");
    try {
      for (Path dir : new Path[] {temp, backend}) {
        System.setProperty("user.dir", dir.toString());
        StandardEnvironment env = new StandardEnvironment();
        new LocalSettingsEnvironmentPostProcessor()
            .postProcessEnvironment(env, new SpringApplication(WriterApplication.class));
        assertEquals("local@example.com", env.getProperty("NG_ADMIN_EMAIL"));
        env.getPropertySources()
            .addFirst(
                new MapPropertySource(
                    "override",
                    Collections.singletonMap("NG_ADMIN_EMAIL", "override@example.com")));
        assertEquals("override@example.com", env.getProperty("NG_ADMIN_EMAIL"));
      }
    } finally {
      System.setProperty("user.dir", original);
    }
  }
}
