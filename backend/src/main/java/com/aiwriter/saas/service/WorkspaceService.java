package com.aiwriter.saas.service;

import java.util.*;

public interface WorkspaceService {
  Map<String, Object> access(long tenant);

  Map<String, Object> project(long project, String operation);

  Object overview(long tenant);

  Object create(Map<String, Object> b);

  Object members(long tenant);

  Object invite(long tenant, Map<String, Object> b);

  Object accept(Map<String, Object> b);

  Object member(long tenant, long user, Map<String, Object> b, boolean delete);

  Object order(long tenant, Map<String, Object> b);

  Object tenantAction(long tenant, String action, Map<String, Object> body);

  Object invitation(long tenant, long id, boolean resend);

  Object cancelOrder(long tenant, long id);

  Object admin(String resource);

  Object adminUpdate(String resource, long id, Map<String, Object> b);

  Object settings();

  void quota(long tenant, String key, long increase);

  void audit(long tenant, String action, long target);
}
