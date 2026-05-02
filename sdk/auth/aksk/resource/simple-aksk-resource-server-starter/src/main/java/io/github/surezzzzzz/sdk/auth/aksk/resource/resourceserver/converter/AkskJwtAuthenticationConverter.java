package io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.converter;

import io.github.surezzzzzz.sdk.auth.aksk.resource.core.constant.SimpleAkskResourceConstant;
import io.github.surezzzzzz.sdk.auth.aksk.resource.core.event.AkskAccessEvent;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.constant.SimpleAkskResourceServerConstant;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.support.ConverterHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import javax.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

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
 *   <li>发布 AkskAccessEvent 事件</li>
 * </ul>
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
public class AkskJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final ApplicationEventPublisher eventPublisher;

    public AkskJwtAuthenticationConverter(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Map<String, String> context = extractContext(jwt);

        HttpServletRequest request = ConverterHelper.getCurrentRequest();
        if (request != null) {
            request.setAttribute(SimpleAkskResourceConstant.CONTEXT_ATTRIBUTE, context);
            log.debug("JWT context injected: {} fields", context.size());

            try {
                AkskAccessEvent event = ConverterHelper.buildAccessEvent(
                        this, SimpleAkskResourceServerConstant.ACCESS_SOURCE_JWT, context, request);
                eventPublisher.publishEvent(event);
            } catch (Exception e) {
                log.warn("Failed to publish AkskAccessEvent", e);
            }
        }

        Collection<GrantedAuthority> authorities = ConverterHelper.extractAuthorities(
                jwt.getClaimAsString(SimpleAkskResourceConstant.JWT_CLAIM_SCOPE));

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

        // 提取标准映射中的 claims
        SimpleAkskResourceConstant.JWT_CLAIM_TO_FIELD.forEach((claimName, fieldName) -> {
            Object value = jwt.getClaim(claimName);
            if (value != null) {
                context.put(fieldName, ConverterHelper.claimValueToString(value));
                log.debug("Extracted JWT claim: {} -> {} = {}", claimName, fieldName, value);
            }
        });

        // 提取自定义 claims（不在标准映射中的）
        jwt.getClaims().forEach((claimName, value) -> {
            if (isStandardJwtClaim(claimName) || SimpleAkskResourceConstant.JWT_CLAIM_TO_FIELD.containsKey(claimName)) {
                return;
            }
            if (value != null) {
                String fieldName = convertToCamelCase(claimName);
                context.put(fieldName, ConverterHelper.claimValueToString(value));
                log.debug("Extracted custom JWT claim: {} -> {} = {}", claimName, fieldName, value);
            }
        });

        log.debug("JWT context extracted: {} fields", context.size());
        return context;
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
        if (snakeCase == null || !snakeCase.contains("_")) {
            return snakeCase;
        }
        String[] parts = snakeCase.split("_");
        StringBuilder result = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                result.append(Character.toUpperCase(parts[i].charAt(0)))
                        .append(parts[i].substring(1));
            }
        }
        return result.toString();
    }
}
