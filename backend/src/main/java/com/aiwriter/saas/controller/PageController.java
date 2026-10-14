package com.aiwriter.saas.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** 部署包内的 Vue 路由回退；API、文档与静态资源保留原始处理方式。 */
@Controller
public class PageController {
  @GetMapping({"/", "/login", "/team", "/plans", "/account", "/admin", "/projects/{id}/editor"})
  public String page() {
    return "forward:/index.html";
  }
}
