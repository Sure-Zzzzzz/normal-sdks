package io.github.surezzzzzz.sdk.auth.iam.server.event;

import lombok.Getter;

import java.time.Instant;
import java.util.Set;

/**
 * IAM Token 受控验证终审事件（/iam/resource/tokens/verify 每次调用的最终结论）。
 *
 * <p>active 表达终审结论（六道校验全过为 true，任一不过为 false）；
 * verificationClientId 为发起验证的资源验证客户端标识。验证失败时
 * token 元数据字段可能为空（无法定位到授权记录）。
 *
 * @author surezzzzzz
 */
@Getter
public class TokenVerifiedEvent extends AbstractTokenEvent {

    /**
     * 终审结论：true = 全部校验通过；false = 任一校验未通过。
     */
    private final boolean active;

    /**
     * 发起本次验证的资源验证客户端 clientId。
     */
    private final String verificationClientId;

    public TokenVerifiedEvent(Object source,
                              String clientId, String clientType,
                              String userId, String username,
                              String tokenValue, Set<String> scopes,
                              Instant issuedAt, Instant expiresAt,
                              boolean active, String verificationClientId) {
        super(source, TokenEventType.VERIFIED, TokenEventCause.RESOURCE_VERIFICATION,
                clientId, clientType, userId, username,
                tokenValue, scopes, issuedAt, expiresAt);
        this.active = active;
        this.verificationClientId = verificationClientId;
    }
}
