package io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.converter;

import io.github.surezzzzzz.sdk.auth.aksk.resource.core.constant.SimpleAkskResourceConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * JWT Authentication Converter
 *
 * <p>将 JWT Token 转换为 Spring Security Authentication，并提取 claims 到上下文。
 *
 * <p>功能：
 * <ul>
 *   <li>提取 JWT claims 并转换为 camelCase 格式</li>
 *   <li>存储到 Request Attribute</li>
 *   <li>提取 scope 作为 GrantedAuthority</li>
 * </ul>
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
public class AkskJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        // 提取 JWT claims 并转换为上下文 Map
        Map<String, String> context = extractContext(jwt);

        // 直接存储到 Request Attribute
        HttpServletRequest request = getCurrentRequest();
        if (request != null) {
            request.setAttribute(SimpleAkskResourceConstant.CONTEXT_ATTRIBUTE, context);
            log.debug("JWT context injected: {} fields", context.size());
        }

        // 提取权限（从 scope claim）
        Collection<GrantedAuthority> authorities = extractAuthorities(jwt);

        // 创建 Authentication Token
        return new JwtAuthenticationToken(jwt, authorities);
    }

    /**
     * 提取 JWT claims 并转换为上下文 Map
     *
     * @param jwt JWT Token
     * @return 上下文 Map（key: camelCase 字段名, value: claim 值）
     */
    private Map<String, String> extractContext(Jwt jwt) {
        Map<String, String> context = new HashMap<>();

        // 遍历 JWT_CLAIM_TO_FIELD 映射，提取 claims
        SimpleAkskResourceConstant.JWT_CLAIM_TO_FIELD.forEach((jwtClaimName, fieldName) -> {
            Object claimValue = jwt.getClaim(jwtClaimName);
            if (claimValue != null) {
                context.put(fieldName, claimValue.toString());
                log.debug("Extracted JWT claim: {} -> {} = {}", jwtClaimName, fieldName, claimValue);
            }
        });

        // 提取其他自定义 claims（不在标准映射中的）
        jwt.getClaims().forEach((claimName, claimValue) -> {
            // 跳过标准 JWT claims（iss, sub, aud, exp, nbf, iat, jti）
            if (isStandardJwtClaim(claimName)) {
                return;
            }

            // 跳过已经映射的 claims
            if (SimpleAkskResourceConstant.JWT_CLAIM_TO_FIELD.containsKey(claimName)) {
                return;
            }

            // 转换为 camelCase 并存储
            String fieldName = convertToCamelCase(claimName);
            if (claimValue != null) {
                context.put(fieldName, claimValue.toString());
                log.debug("Extracted custom JWT claim: {} -> {} = {}", claimName, fieldName, claimValue);
            }
        });

        log.debug("JWT context extracted: {} fields", context.size());
        return context;
    }

    /**
     * 提取权限（从 scope claim）
     *
     * @param jwt JWT Token
     * @return GrantedAuthority 集合
     */
    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        String scope = jwt.getClaimAsString(SimpleAkskResourceConstant.JWT_CLAIM_SCOPE);
        if (scope == null || scope.isEmpty()) {
            return Collections.emptyList();
        }

        // scope 格式：空格分隔的字符串（如 "read write admin"）
        return Arrays.stream(scope.split(" "))
                .filter(s -> !s.isEmpty())
                .map(s -> new SimpleGrantedAuthority("SCOPE_" + s))
                .collect(Collectors.toList());
    }

    /**
     * 判断是否为标准 JWT claim
     *
     * @param claimName Claim 名称
     * @return true 如果是标准 JWT claim
     */
    private boolean isStandardJwtClaim(String claimName) {
        return JwtClaimNames.ISS.equals(claimName)
                || JwtClaimNames.SUB.equals(claimName)
                || JwtClaimNames.AUD.equals(claimName)
                || JwtClaimNames.EXP.equals(claimName)
                || JwtClaimNames.NBF.equals(claimName)
                || JwtClaimNames.IAT.equals(claimName)
                || JwtClaimNames.JTI.equals(claimName);
    }

    /**
     * 将 snake_case 转换为 camelCase
     *
     * @param snakeCase snake_case 字符串
     * @return camelCase 字符串
     */
    private String convertToCamelCase(String snakeCase) {
        if (snakeCase == null || snakeCase.isEmpty()) {
            return snakeCase;
        }

        String[] parts = snakeCase.split("_");
        if (parts.length == 1) {
            return snakeCase;
        }

        StringBuilder camelCase = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                camelCase.append(Character.toUpperCase(parts[i].charAt(0)));
                if (parts[i].length() > 1) {
                    camelCase.append(parts[i].substring(1));
                }
            }
        }

        return camelCase.toString();
    }

    /**
     * 获取当前请求
     *
     * @return 当前 HttpServletRequest，如果不存在则返回 null
     */
    private HttpServletRequest getCurrentRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes) {
            return ((ServletRequestAttributes) attrs).getRequest();
        }
        return null;
    }
}
