package com.aiwriter.saas.service;

import java.util.Map;

/** 小说文本与AI结构化回答的提取导入协议。 */
public interface ExtractionService {
  Map<String, Object> prompt(long project, Map<String, Object> body);

  Map<String, Object> preview(long project, Map<String, Object> body);

  Map<String, Object> commit(long project, Map<String, Object> body);
}
