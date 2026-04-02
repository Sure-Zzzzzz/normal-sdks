package io.github.surezzzzzz.sdk.audit.search.elasticsearch.test.provider;

import io.github.surezzzzzz.sdk.audit.search.elasticsearch.provider.EsAuditUserProvider;
import io.github.surezzzzzz.sdk.auth.aksk.resource.core.constant.SimpleAkskResourceConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

/**
 * 从HTTP请求头获取用户信息的Provider
 *
 * <p>用于Header认证集成测试，直接从请求头读取用户信息
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Component
@ConditionalOnProperty(
        prefix = "test.es.audit",
        name = "provider-type",
        havingValue = "header"
)
@Slf4j
public class HeaderEsAuditUserProvider implements EsAuditUserProvider {

    @Override
    public String getClientId() {
        return getHeader(SimpleAkskResourceConstant.HEADER_CLIENT_ID);
    }

    @Override
    public String getClientType() {
        return getHeader(SimpleAkskResourceConstant.HEADER_CLIENT_TYPE);
    }

    @Override
    public String getUserId() {
        return getHeader(SimpleAkskResourceConstant.HEADER_USER_ID);
    }

    @Override
    public String getUsername() {
        return getHeader(SimpleAkskResourceConstant.HEADER_USERNAME);
    }

    private String getHeader(String headerName) {
        try {
            HttpServletRequest request = getCurrentRequest();
            if (request == null) {
                log.debug("No current request found");
                return null;
            }
            return request.getHeader(headerName);
        } catch (Exception e) {
            log.warn("Failed to get header: {}", headerName, e);
            return null;
        }
    }

    private HttpServletRequest getCurrentRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes) {
            return ((ServletRequestAttributes) attrs).getRequest();
        }
        return null;
    }
}
