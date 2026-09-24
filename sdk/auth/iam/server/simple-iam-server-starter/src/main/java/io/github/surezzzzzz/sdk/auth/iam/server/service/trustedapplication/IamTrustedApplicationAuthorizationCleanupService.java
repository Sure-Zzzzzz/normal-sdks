package io.github.surezzzzzz.sdk.auth.iam.server.service.trustedapplication;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 可信应用客户端 OAuth 授权清理服务。
 *
 * <p>必须经最外层 {@link OAuth2AuthorizationService#remove(OAuth2Authorization)} 删除授权，
 * 以保住授权缓存驱逐、refresh token 族回收与令牌审计。禁止在调用方直接删除
 * {@code oauth2_authorization}。</p>
 *
 * @author surezzzzzz
 */
@SimpleIamServerComponent
@RequiredArgsConstructor
public class IamTrustedApplicationAuthorizationCleanupService {

    private static final String SQL_FIND_AUTHORIZATION_IDS_PREFIX =
            "SELECT id FROM oauth2_authorization WHERE registered_client_id IN (";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectProvider<OAuth2AuthorizationService> authorizationServiceProvider;

    /**
     * 清理一批授权。游标记录的是已成功物理删除的数量，下一批始终从剩余记录头部读取，
     * 因此不会因删除行导致 offset 跳跃。
     */
    public long removeAuthorizationBatch(List<String> registeredClientIds, int batchSize) {
        if (registeredClientIds == null || registeredClientIds.isEmpty() || batchSize <= 0) {
            return 0L;
        }
        OAuth2AuthorizationService authorizationService = authorizationServiceProvider.getIfAvailable();
        if (authorizationService == null) {
            throw new SimpleIamServerException(ErrorCode.TOKEN_OPERATION_FAILED,
                    String.format(ServerErrorMessage.TOKEN_OPERATION_FAILED,
                            "OAuth2AuthorizationService 未装配"));
        }
        List<String> authorizationIds = jdbcTemplate.query(buildFindAuthorizationIdsSql(registeredClientIds.size()),
                (resultSet, rowNum) -> resultSet.getString("id"), buildQueryArguments(registeredClientIds, batchSize));
        for (String authorizationId : authorizationIds) {
            OAuth2Authorization authorization = authorizationService.findById(authorizationId);
            if (authorization != null) {
                authorizationService.remove(authorization);
            }
        }
        return authorizationIds.size();
    }

    /**
     * 删除已无授权行的客户端关联 consent。SAS consent 没有缓存和 token 族副作用，物理删除
     * 与应用最终物理删除保持同一事务边界。
     */
    public void removeConsentRows(List<String> registeredClientIds, List<String> clientIds) {
        deleteByValues("DELETE FROM oauth2_authorization_consent WHERE registered_client_id IN (",
                registeredClientIds);
        deleteByValues("DELETE FROM iam_consent WHERE client_id IN (", clientIds);
    }

    private Object[] buildQueryArguments(List<String> registeredClientIds, int batchSize) {
        List<Object> arguments = new ArrayList<Object>(registeredClientIds);
        arguments.add(batchSize);
        return arguments.toArray();
    }

    private String buildFindAuthorizationIdsSql(int size) {
        return SQL_FIND_AUTHORIZATION_IDS_PREFIX + placeholders(size)
                + ") ORDER BY id ASC LIMIT ?";
    }

    private void deleteByValues(String prefix, List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        jdbcTemplate.update(prefix + placeholders(values.size()) + ")", values.toArray());
    }

    private String placeholders(int size) {
        return String.join(",", Collections.nCopies(size, "?"));
    }
}
