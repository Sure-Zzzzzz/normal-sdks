package io.github.surezzzzzz.sdk.auth.aksk.server.provider;

import io.github.surezzzzzz.sdk.auth.aksk.server.annotation.SimpleAkskServerComponent;
import io.github.surezzzzzz.sdk.auth.aksk.server.constant.SimpleAkskServerConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterContext;
import io.github.surezzzzzz.sdk.limiter.redis.smart.generator.SmartRedisLimiterKeyProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * AKSK OAuth2 Client ID Key Provider
 *
 * @author surezzzzzz
 */
@SimpleAkskServerComponent
public class AkskOAuth2ClientIdKeyProvider implements SmartRedisLimiterKeyProvider {

    public static final String BEAN_NAME = "akskOAuth2ClientIdKeyProvider";

    private static final String LIMIT_KEY_PREFIX = "client";

    private static final String LIMIT_KEY_FORMAT = "%s:%s";

    @Override
    public String provide(HttpServletRequest request, SmartRedisLimiterContext context) {
        String clientId = resolveClientId(request);
        if (!StringUtils.hasText(clientId)) {
            return null;
        }
        return String.format(LIMIT_KEY_FORMAT,
                LIMIT_KEY_PREFIX, clientId);
    }

    private String resolveClientId(HttpServletRequest request) {
        String basicClientId = resolveBasicClientId(request);
        if (StringUtils.hasText(basicClientId)) {
            return basicClientId;
        }
        return request.getParameter(SimpleAkskServerConstant.JWT_CLAIM_CLIENT_ID);
    }

    private String resolveBasicClientId(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(SimpleAkskServerConstant.HTTP_BASIC_AUTH_PREFIX)) {
            return null;
        }
        try {
            String encoded = authorization.substring(SimpleAkskServerConstant.HTTP_BASIC_AUTH_PREFIX.length());
            String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            int separatorIndex = decoded.indexOf(':');
            if (separatorIndex < 0) {
                return decoded;
            }
            return decoded.substring(0, separatorIndex);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
