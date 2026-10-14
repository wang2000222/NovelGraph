package com.aiwriter.saas.service;

import java.util.Map;

public interface AuthService {
  Map<String, Object> register(Map<String, Object> body);

  Map<String, Object> login(Map<String, Object> body);

  Map<String, Object> authenticate(String token);

  Map<String, Object> me();

  void logout(String token);

  void verify(Map<String, Object> body);

  void forgot(Map<String, Object> body);

  void reset(Map<String, Object> body);

  void revokeOthers();

  void passwordChange(Map<String, Object> body);
}
