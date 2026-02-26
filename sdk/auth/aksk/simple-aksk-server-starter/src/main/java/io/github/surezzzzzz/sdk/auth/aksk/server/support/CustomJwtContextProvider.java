package io.github.surezzzzzz.sdk.auth.aksk.server.support;

import io.github.surezzzzzz.sdk.auth.aksk.resource.core.constant.SimpleAkskResourceConstant;
import io.github.surezzzzzz.sdk.auth.aksk.resource.core.provider.SimpleAkskSecurityContextProvider;
import io.github.surezzzzzz.sdk.auth.aksk.server.annotation.SimpleAkskServerComponent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.HashMap;
import java.util.Map;

/**
 * Custom JWT Context Provider
 * 从 JWT 中提取 scope 等信息到 security context
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleAkskServerComponent
public class CustomJwtContextProvider implements SimpleAkskSecurityContextProvider {

    @Override
    public Map<String, String> getAll() {
        Map<String, String> context = new HashMap<>();

        try {
            // 从 Spring Security 获取 JWT
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof Jwt) {
                Jwt jwt = (Jwt) authentication.getPrincipal();

                // 提取 scope claim（OAuth2 标准字段，空格分隔）
                Object scopeClaim = jwt.getClaim(SimpleAkskResourceConstant.JWT_CLAIM_SCOPE);
                if (scopeClaim != null) {
                    String scopeValue;
                    // scope 可能是 List<String> 或 String
                    if (scopeClaim instanceof java.util.List) {
                        // 如果是 List，转换为空格分隔的字符串
                        scopeValue = String.join(" ", (java.util.List<String>) scopeClaim);
                    } else {
                        // 如果是 String，直接使用
                        scopeValue = scopeClaim.toString();
                    }
                    context.put(SimpleAkskResourceConstant.FIELD_SCOPE, scopeValue);
                }

                // 提取其他常用 claims
                String clientId = jwt.getClaimAsString(SimpleAkskResourceConstant.JWT_CLAIM_CLIENT_ID);
                if (clientId != null) {
                    context.put(SimpleAkskResourceConstant.FIELD_CLIENT_ID, clientId);
                }

                String userId = jwt.getClaimAsString(SimpleAkskResourceConstant.JWT_CLAIM_USER_ID);
                if (userId != null) {
                    context.put(SimpleAkskResourceConstant.FIELD_USER_ID, userId);
                }

                String username = jwt.getClaimAsString(SimpleAkskResourceConstant.JWT_CLAIM_USERNAME);
                if (username != null) {
                    context.put(SimpleAkskResourceConstant.FIELD_USERNAME, username);
                }

                Integer clientType = jwt.getClaim(SimpleAkskResourceConstant.JWT_CLAIM_CLIENT_TYPE);
                if (clientType != null) {
                    context.put(SimpleAkskResourceConstant.FIELD_CLIENT_TYPE, String.valueOf(clientType));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract JWT context", e);
        }

        return context;
    }

    @Override
    public String get(String key) {
        Map<String, String> all = getAll();
        return all != null ? all.get(key) : null;
    }
}
