package io.github.surezzzzzz.sdk.crm.server.service;

import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmOwner;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * owner 解析桥：在无 Resource 协作件的散形态下从已认证主体名称派生 CrmOwner。
 *
 * <p>Resource Server 协作形态由可选的同型桥负责翻译 {@code VerifiedResourceContext}；
 * 缺少协作件时本类仍可独立装配。</p>
 *
 * <p>主体名称按 {@code iam:} / {@code aksk:} 前缀派生；宿主自备桥时由默认配置让位。</p>
 *
 * <p>日志红线：只输出主体类型等白名单同构字段，不输出 token、claim 或单独的 subjectId 裸值。</p>
 *
 * @author surezzzzzz
 */
public class SimpleCrmServerOwnerResolverBridge {

    /**
     * 从当前安全上下文解析归属主体。
     *
     * @return 归属主体
     */
    public CrmOwner resolve() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getPrincipal() == null) {
            throw new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE, "authentication");
        }
        String name = authentication.getName();
        if (name == null || name.trim().isEmpty()) {
            throw new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE, "principalName");
        }
        // 散形态：主体名即 owner 前缀形态（iam:xxx / aksk:xxx）；无前缀按人员处理
        if (name.startsWith(CrmOwner.PREFIX_SERVICE)) {
            return CrmOwner.of(name);
        }
        return CrmOwner.human(name.contains(":") ? name.substring(name.indexOf(':') + 1) : name);
    }

}
