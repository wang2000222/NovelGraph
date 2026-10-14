package com.aiwriter.saas.service;

import java.util.Map;

public interface ExportService {
  Map<String, Object> preview(long project, Map<String, Object> body);
}
