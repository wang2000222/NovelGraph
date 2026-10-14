package com.aiwriter.saas.service;

import java.util.*;

public interface WorldService {
  Object grants(long project);

  void deleteSnapshot(long project, long snapshot);

  void prune(Map<String, Object> graph);

  Object projects(long tenant);

  Object create(long tenant, Map<String, Object> b);

  Object update(long project, Map<String, Object> b);

  void delete(long project);

  Map<String, Object> graph(long project);

  Object save(long project, String kind, long id, Map<String, Object> b);

  /** 内部整批新增，复用单条字段校验，批次结束统一校验与递增版本。 */
  void batch(long project, Map<String, Object> graph, Runnable writes);

  Object deleteEntity(long project, String kind, long id, Map<String, Object> b);

  Object restoreEntity(long project, String kind, long id, Map<String, Object> b);

  Object layout(long project, Map<String, Object> b);

  Object permissions(long project, Map<String, Object> b);

  Object snapshots(long project);

  Object snapshot(long project, Map<String, Object> b);

  Object restore(long project, long snapshot, Map<String, Object> b);

  Object copy(long project, Map<String, Object> b);

  Object importPreview(Map<String, Object> b);

  Object importCommit(long tenant, Map<String, Object> b);

  Map<String, Object> validate(long project);
}
