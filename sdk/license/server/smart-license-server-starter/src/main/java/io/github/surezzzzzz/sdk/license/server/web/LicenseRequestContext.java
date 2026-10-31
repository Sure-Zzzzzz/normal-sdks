package io.github.surezzzzzz.sdk.license.server.web;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import lombok.Getter;

/**
 * License 业务请求上下文：已验证主体 + 本次授权快照（API 权限码集合 + DATA 授权文档）。
 *
 * <p>由公共资源链完成认证（IAM 人员令牌 / AKSK 服务令牌），本对象只承接其结论，
 * 不解析令牌。业务端点经 {@code requireApiPermission} 消费精确 API 码，
 * DATA 经 {@code requireDataAll} 做准入围栏（受限 IN 过滤随查询实施逐表接入）。</p>
 *
 * @author surezzzzzz
 */
public final class LicenseRequestContext {

    private final String principalId;
    private final String subjectType;
    private final List<String> apiPermissions;
    @Getter
    private final List<String> pagePermissions;
    private final String dataGrantDocumentJson;
    private final String requestId;
    @Getter
    private final String authenticationSourceId;

    public LicenseRequestContext(String principalId, String subjectType, List<String> apiPermissions,
                                 String dataGrantDocumentJson, String requestId) {
        this(principalId, subjectType, apiPermissions, Collections.<String>emptyList(), dataGrantDocumentJson,
                requestId);
    }

    /** PAGE/API 均承接同一次验证的授权快照，不推断角色继承关系。 */
    public LicenseRequestContext(String principalId, String subjectType, List<String> apiPermissions,
                                 List<String> pagePermissions, String dataGrantDocumentJson, String requestId) {
        this(principalId, subjectType, apiPermissions, pagePermissions, dataGrantDocumentJson, requestId, null);
    }

    /** 认证来源只承接官方资源上下文，用于服务本人资格核对，不从请求体或身份前缀推断。 */
    public LicenseRequestContext(String principalId, String subjectType, List<String> apiPermissions,
                                 List<String> pagePermissions, String dataGrantDocumentJson, String requestId,
                                 String authenticationSourceId) {
        if (principalId == null || principalId.isEmpty() || requestId == null || requestId.isEmpty()) {
            throw new IllegalArgumentException("请求上下文字段非法");
        }
        this.principalId = principalId;
        this.subjectType = subjectType;
        this.apiPermissions = apiPermissions == null
                ? Collections.<String>emptyList() : Collections.unmodifiableList(new ArrayList<>(apiPermissions));
        this.pagePermissions = pagePermissions == null
                ? Collections.<String>emptyList() : Collections.unmodifiableList(new ArrayList<>(pagePermissions));
        this.dataGrantDocumentJson = dataGrantDocumentJson;
        this.requestId = requestId;
        this.authenticationSourceId = authenticationSourceId;
    }

    /** 稳定主体标识（iam:{subjectId} 或 aksk:{clientId}，与 KMS owner 语义同构）。 */
    public String getPrincipalId() {
        return principalId;
    }

    /** HUMAN / SERVICE。 */
    public String getSubjectType() {
        return subjectType;
    }

    public List<String> getApiPermissions() {
        return apiPermissions;
    }

    /** DATA 授权文档原始 JSON；无 DATA 投影时为 null（业务按资源失败关闭）。 */
    public String getDataGrantDocumentJson() {
        return dataGrantDocumentJson;
    }

    public String getRequestId() {
        return requestId;
    }
}
