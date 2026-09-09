package io.github.surezzzzzz.sdk.auth.iam.server.service;

import io.github.surezzzzzz.sdk.auth.iam.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.core.exception.IamProtocolException;
import io.github.surezzzzzz.sdk.auth.iam.core.spi.ExternalBrowserLoginProvider;
import io.github.surezzzzzz.sdk.auth.iam.core.spi.ExternalCredentialAuthenticator;
import io.github.surezzzzzz.sdk.auth.iam.core.spi.ExternalIdentity;
import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 外部身份源登录编排服务
 *
 * <p>凭证校验型与跳转型登录的统一入口：调用 SPI 适配器认证、按错误码区分
 * 凭据错误（计失败）与外部源不可用（不计失败），认证成功后交归一服务映射本地用户。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class ExternalLoginService {

    private final ExternalProviderRegistry providerRegistry;
    private final ExternalIdentityResolutionService resolutionService;
    private final LoginFailurePolicySupport failurePolicySupport;

    /**
     * 凭证校验型登录（如 LDAP bind）。
     *
     * <p>失败计数按 providerCode + username 维度，与本地密码登录计数互不污染。
     *
     * @param providerCode 登录方式编码
     * @param username     用户名
     * @param credential   凭证
     * @return 登录成功的本地用户
     */
    @Transactional
    public IamUserEntity credentialLogin(String providerCode, String username, String credential) {
        ExternalCredentialAuthenticator authenticator =
                providerRegistry.getCredentialAuthenticator(providerCode);
        if (authenticator == null) {
            throw new SimpleIamServerException(ErrorCode.EXTERNAL_PROVIDER_NOT_FOUND,
                    String.format(ServerErrorMessage.EXTERNAL_PROVIDER_NOT_FOUND, providerCode));
        }

        failurePolicySupport.assertExternalAttemptAllowed(providerCode, username);

        ExternalIdentity identity;
        try {
            identity = authenticator.authenticate(username, credential);
        } catch (IamProtocolException exception) {
            throw translated(providerCode, username, exception);
        }
        return completeLogin(providerCode, username, identity);
    }

    /**
     * 跳转型登录回调完成（如 OIDC 授权码换取）。
     *
     * <p>回调无法归因到用户名，失败计数与达限断言均按 providerCode 维度：
     * 伪造或重放回调（{@link ErrorCode#EXTERNAL_CALLBACK_INVALID}）同样累计，
     * 达限后锁定该登录方式一个锁定窗口，防回调爆破。
     *
     * @param provider       跳转型登录提供方
     * @param callbackParams 回调参数
     * @return 登录成功的本地用户
     */
    @Transactional
    public IamUserEntity completeBrowserLogin(ExternalBrowserLoginProvider provider,
                                              Map<String, String> callbackParams) {
        String providerCode = provider.providerCode();
        failurePolicySupport.assertExternalAttemptAllowed(providerCode, providerCode);
        ExternalIdentity identity;
        try {
            identity = provider.consumeCallback(callbackParams);
        } catch (IamProtocolException exception) {
            throw translated(providerCode, providerCode, exception);
        }
        return completeLogin(providerCode, providerCode, identity);
    }

    private IamUserEntity completeLogin(String providerCode, String counterKey,
                                        ExternalIdentity identity) {
        IamUserEntity user = resolutionService.resolve(identity);
        failurePolicySupport.recordExternalSuccess(providerCode, counterKey, user);
        log.info("外部身份源登录成功：provider={}, username={}, userId={}",
                providerCode, user.getUsername(), user.getId());
        return user;
    }

    private SimpleIamServerException translated(String providerCode, String counterKey,
                                                IamProtocolException exception) {
        String errorCode = exception.getErrorCode();
        if (ErrorCode.EXTERNAL_BAD_CREDENTIALS.equals(errorCode)) {
            long failureCount = failurePolicySupport.recordExternalFailure(providerCode, counterKey);
            int remaining = failurePolicySupport.remainingAttempts(failureCount);
            if (remaining <= 0) {
                return new SimpleIamServerException(errorCode, ServerErrorMessage.ACCOUNT_LOCKED, exception);
            }
            return new SimpleIamServerException(errorCode,
                    String.format(ServerErrorMessage.BAD_CREDENTIALS_REMAINING, remaining), exception);
        }
        if (ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE.equals(errorCode)) {
            log.warn("外部身份源不可用：provider={}, reason={}", providerCode, exception.getMessage());
            return new SimpleIamServerException(errorCode,
                    ServerErrorMessage.EXTERNAL_PROVIDER_UNAVAILABLE, exception);
        }
        failurePolicySupport.recordExternalFailure(providerCode, counterKey);
        log.warn("外部登录回调无效：provider={}, reason={}", providerCode, exception.getMessage());
        return new SimpleIamServerException(errorCode,
                ServerErrorMessage.EXTERNAL_CALLBACK_INVALID, exception);
    }
}
