package com.aiwriter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@org.mybatis.spring.annotation.MapperScan("com.aiwriter.mapper")
public class WriterApplication {
  public static void main(String[] args) {
    SpringApplication.run(WriterApplication.class, args);
  }
}
