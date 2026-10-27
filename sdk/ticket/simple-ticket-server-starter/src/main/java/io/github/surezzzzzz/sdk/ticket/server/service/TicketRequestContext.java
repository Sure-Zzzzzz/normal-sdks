package io.github.surezzzzzz.sdk.ticket.server.service;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 已认证请求上下文（认证桥从已验证认证结果派生，不信任业务字段——feedback 同构）。
 *
 * @author surezzzzzz
 */
@Data
@AllArgsConstructor
public class TicketRequestContext {

    /**
     * 主体标识（人员 subjectId 或机器凭据标识）
     */
    private String subjectId;

    /**
     * 显示名快照
     */
    private String displayName;

    /**
     * 是否拥有工单全量数据授权；它只代表全量读的数据身份，不代表页面可见性或写权限。
     */
    private boolean allTicketRead;

    /**
     * 是否机器主体（openapi 面；创建凭据审计锚）
     */
    private boolean machine;

    /**
     * 已验证的精确 API 权限码。
     */
    private Set<String> apiPermissions;

    /**
     * 创建人员请求上下文，保留本地测试与自定义 resolver 的四参契约。
     *
     * @param subjectId 主体标识
     * @param displayName 显示名快照
     * @param allTicketRead 全量读页面权限
     * @param machine 是否机器主体
     */
    public TicketRequestContext(String subjectId, String displayName, boolean allTicketRead, boolean machine) {
        this(subjectId, displayName, allTicketRead, machine, Collections.<String>emptySet());
    }

    /**
     * 创建带已验证 API 权限的请求上下文。
     *
     * @param subjectId 主体标识
     * @param displayName 显示名快照
     * @param allTicketRead 全量读页面权限
     * @param machine 是否机器主体
     * @param apiPermissions 精确 API 权限
     */
    public TicketRequestContext(String subjectId, String displayName, boolean allTicketRead, boolean machine,
                                Collection<String> apiPermissions) {
        this.subjectId = subjectId;
        this.displayName = displayName;
        this.allTicketRead = allTicketRead;
        this.machine = machine;
        this.apiPermissions = immutablePermissions(apiPermissions);
    }

    /**
     * 判断是否带有精确 API 权限码。
     *
     * @param permission 权限码
     * @return 是否拥有
     */
    public boolean hasApiPermission(String permission) {
        return permission != null && apiPermissions.contains(permission);
    }

    private static Set<String> immutablePermissions(Collection<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> copy = new HashSet<>();
        for (String permission : permissions) {
            if (permission != null && !permission.trim().isEmpty()) {
                copy.add(permission);
            }
        }
        return Collections.unmodifiableSet(copy);
    }
}
