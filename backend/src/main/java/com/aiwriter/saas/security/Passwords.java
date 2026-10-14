package com.aiwriter.saas.security;

import java.security.*;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Java8内置PBKDF2密码散列与令牌散列，日志不记录明文凭据。 */
public final class Passwords {
  public static String token() {
    byte[] b = new byte[32];
    new SecureRandom().nextBytes(b);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
  }

  public static String digest(String raw) {
    try {
      byte[] b = MessageDigest.getInstance("SHA-256").digest(raw.getBytes("UTF-8"));
      StringBuilder s = new StringBuilder();
      for (byte x : b) s.append(String.format("%02x", x));
      return s.toString();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public static String hash(String password) {
    String salt = token();
    return salt + ":" + derive(password, salt);
  }

  private static String derive(String password, String salt) {
    try {
      return Base64.getEncoder()
          .encodeToString(
              SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                  .generateSecret(
                      new PBEKeySpec(password.toCharArray(), salt.getBytes("UTF-8"), 210000, 256))
                  .getEncoded());
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public static boolean check(String password, String encoded) {
    String[] s = encoded.split(":");
    return s.length == 2
        && MessageDigest.isEqual(
            derive(password, s[0]).getBytes(java.nio.charset.StandardCharsets.UTF_8),
            s[1].getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }
}
