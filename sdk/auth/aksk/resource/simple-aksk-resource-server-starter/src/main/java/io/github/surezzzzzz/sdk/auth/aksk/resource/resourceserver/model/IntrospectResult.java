package io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Introspect 结果缓存模型
 *
 * <p>缓存 introspect 完整响应，包含 active 标志和原始 attributes，
 * 命中缓存时可直接重建 OAuth2AuthenticatedPrincipal，无需二次解析。
 *
 * @author surezzzzzz
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IntrospectResult {

    /**
     * token 是否有效
     */
    private boolean active;

    /**
     * introspect 原始响应 attributes（含 client_id、scope 等所有字段）
     */
    private Map<String, Object> attributes;
}
