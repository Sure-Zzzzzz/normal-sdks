package io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.test.provider;

import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.provider.EsPersistenceAuditUserProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 测试用 persistence 审计用户信息提供者
 *
 * @author surezzzzzz
 */
@Component
@ConditionalOnProperty(prefix = "test.es.persistence.audit", name = "use-mock-provider", havingValue = "true")
public class TestPersistenceAuditUserProvider implements EsPersistenceAuditUserProvider {

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
