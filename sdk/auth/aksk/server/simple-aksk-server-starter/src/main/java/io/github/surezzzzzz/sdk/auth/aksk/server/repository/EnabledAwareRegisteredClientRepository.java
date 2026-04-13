package io.github.surezzzzzz.sdk.auth.aksk.server.repository;

import io.github.surezzzzzz.sdk.auth.aksk.server.entity.OAuth2RegisteredClientEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.util.Optional;

/**
 * Enabled-Aware Registered Client Repository
 * <p>
 * 包装标准的RegisteredClientRepository，在加载客户端时检查enabled状态
 *
 * @author surezzzzzz
 */
@Slf4j
@RequiredArgsConstructor
public class EnabledAwareRegisteredClientRepository implements RegisteredClientRepository {

    private final RegisteredClientRepository delegate;
    private final OAuth2RegisteredClientEntityRepository entityRepository;

    @Override
    public void save(RegisteredClient registeredClient) {
        delegate.save(registeredClient);
    }

    @Override
    public RegisteredClient findById(String id) {
        RegisteredClient client = delegate.findById(id);
        return filterDisabledClient(client);
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        RegisteredClient client = delegate.findByClientId(clientId);
        return filterDisabledClient(client);
    }

    /**
     * 过滤禁用的客户端
     *
     * @param client 客户端
     * @return 如果客户端启用则返回客户端，否则返回null
     */
    private RegisteredClient filterDisabledClient(RegisteredClient client) {
        if (client == null) {
            return null;
        }

        // 查询enabled状态
        Optional<OAuth2RegisteredClientEntity> entityOpt = entityRepository.findByClientId(client.getClientId());
        if (entityOpt.isPresent()) {
            OAuth2RegisteredClientEntity entity = entityOpt.get();
            if (!entity.isEnabled()) {
                log.warn("Client {} is disabled, rejecting authentication", client.getClientId());
                return null;
            }
            return client;
        } else {
            // Entity not found in JPA repository, assume enabled
            return client;
        }
    }
}
