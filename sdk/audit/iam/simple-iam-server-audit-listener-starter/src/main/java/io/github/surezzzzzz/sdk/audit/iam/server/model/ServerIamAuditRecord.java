package io.github.surezzzzzz.sdk.audit.iam.server.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;

/**
 * IAM 统一审计记录
 *
 * <p>四族事件（Token / 认证 / 会话 / 管理面）归一化的脱敏审计载体；
 * 每个字段仅在所属族的事件上有值，其余族保持 {@code null}。
 * 记录刻意不携带 Token 原文——处理器不得推导、记录或重建该值。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerIamAuditRecord {
    /**
     * 事件族；决定其余字段的取值范围。
     */
    private ServerIamAuditEventFamily family;
    /**
     * 族内动作类型编码（如 token-issued / login-succeeded / revoked / granted）。
     */
    private String eventType;
    /**
     * 触发该动作的业务来源编码（如 oauth2-revoke / logout / relogin-kick）；无来源表达时为 {@code null}。
     */
    private String cause;
    /**
     * 事件对象创建时间，不等同于 Token 的签发或过期时间。
     */
    private Instant eventTime;
    /**
     * 用户 ID（统一字符串化；登录失败且无法定位用户时为 {@code null}）。
     */
    private String userId;
    /**
     * 用户名。
     */
    private String username;
    /**
     * 客户端 ID；仅 Token 族有值。
     */
    private String clientId;
    /**
     * 客户端类型；仅 Token 族有值。
     */
    private String clientType;
    /**
     * 登录方式编码（local-password / 外部 providerCode）；仅认证族有值。
     */
    private String provider;
    /**
     * 发起端 IP；仅认证族有值。
     */
    private String ip;
    /**
     * 发起端 User-Agent；仅认证族有值。
     */
    private String userAgent;
    /**
     * 登录失败原因错误码；仅认证族 LOGIN_FAILED 有值。
     */
    private String errorCode;
    /**
     * 触发锁定时的累计失败次数；仅认证族 ACCOUNT_LOCKED 有值。
     */
    private Integer failureCount;
    /**
     * IAM 会话 ID；批量吊销事件为 {@code null}；仅会话族有值。
     */
    private String sessionId;
    /**
     * 批量吊销数量；仅会话族批量撤销有值。
     */
    private Integer revokedCount;
    /**
     * 管理操作目标实体类型编码（user / role / oauth-client 等）；仅管理面族有值。
     */
    private String subjectType;
    /**
     * 管理操作目标实体标识；仅管理面族有值。
     */
    private String subjectId;
    /**
     * 管理操作目标实体可读名；仅管理面族有值。
     */
    private String subjectName;
    /**
     * 操作人（当前认证用户名）；系统自动操作为 {@code null}；仅管理面族有值。
     */
    private String operator;
    /**
     * 管理操作补充信息（已脱敏自由文本）；仅管理面族有值。
     */
    private String detail;
    /**
     * 授权范围；仅 Token 族有值。
     */
    private Set<String> scopes;
    /**
     * Token 颁发时间；仅 Token 族有值。
     */
    private Instant issuedAt;
    /**
     * Token 过期时间；仅 Token 族有值。
     */
    private Instant expiresAt;
    /**
     * 被整族撤销的 Refresh Token 族 ID；仅复用检测事件有值。
     */
    private String familyId;
    /**
     * 受控验证终审结论；仅 VERIFIED 事件有值。
     */
    private Boolean active;
    /**
     * 发起验证的资源验证客户端 clientId；仅 VERIFIED 事件有值。
     */
    private String verificationClientId;
}
