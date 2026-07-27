package com.firstagent.backend.common.security;

import java.util.Map;

public interface TokenProvider {

    String generateToken(Map<String, Object> claims, long expirationMillis);

    boolean isTokenValid(String token);

    Map<String, Object> extractClaims(String token);
}