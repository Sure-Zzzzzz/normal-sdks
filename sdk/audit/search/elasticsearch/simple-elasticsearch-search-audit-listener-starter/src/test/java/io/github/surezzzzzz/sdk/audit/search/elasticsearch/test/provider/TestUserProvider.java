package io.github.surezzzzzz.sdk.audit.search.elasticsearch.test.provider;

import io.github.surezzzzzz.sdk.audit.search.elasticsearch.provider.EsAuditUserProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 测试用的用户信息提供者
 *
 * <p>只在单元测试中使用，集成测试使用AkskContextEsAuditUserProvider
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Component
@ConditionalOnProperty(
        prefix = "test.es.audit",
        name = "use-mock-provider",
        havingValue = "true"
)
public class TestUserProvider implements EsAuditUserProvider {

    private String clientId;
    private String clientType;
    private String userId;
    private String username;

    @Override
    public String getClientId() {
        return clientId;
    }

    @Override
    public String getClientType() {
        return clientType;
    }

    @Override
    public String getUserId() {
        return userId;
    }

    @Override
    public String getUsername() {
        return username;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public void setClientType(String clientType) {
        this.clientType = clientType;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void reset() {
        this.clientId = null;
        this.clientType = null;
        this.userId = null;
        this.username = null;
    }
}
