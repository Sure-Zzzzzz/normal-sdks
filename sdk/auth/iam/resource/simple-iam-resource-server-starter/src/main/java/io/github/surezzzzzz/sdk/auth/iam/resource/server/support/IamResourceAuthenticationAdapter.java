package io.github.surezzzzzz.sdk.auth.iam.resource.server.support;

import io.github.surezzzzzz.sdk.auth.iam.core.constant.SimpleIamCoreConstant;
import io.github.surezzzzzz.sdk.auth.iam.resource.core.support.IamResourceAuthenticationResultHelper;
import io.github.surezzzzzz.sdk.auth.iam.resource.core.support.IamVerifiedAuthenticationClaimMapper;
import io.github.surezzzzzz.sdk.auth.iam.resource.server.exception.IamResourceVerificationProtocolException;
import io.github.surezzzzzz.sdk.auth.iam.resource.server.exception.IamResourceVerificationUnavailableException;
import io.github.surezzzzzz.sdk.auth.iam.resource.server.exception.ValidationException;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceAuthenticationFailureCategory;
import io.github.surezzzzzz.sdk.auth.resource.core.model.BearerResourceCredential;
import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationResult;
import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationSourceId;
import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceCredential;
import io.github.surezzzzzz.sdk.auth.resource.core.spi.ResourceAuthenticationAdapter;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * IAM资源认证适配器。
 *
 * @author surezzzzzz
 */
@Slf4j
public final class IamResourceAuthenticationAdapter implements ResourceAuthenticationAdapter {

    private static final ResourceAuthenticationSourceId SOURCE_ID = new ResourceAuthenticationSourceId(
            SimpleIamCoreConstant.RESOURCE_AUTHENTICATION_SOURCE_ID);

    private final HttpIamResourceTokenVerificationClient verificationClient;

    public IamResourceAuthenticationAdapter(HttpIamResourceTokenVerificationClient verificationClient) {
        if (verificationClient == null) {
            throw new ValidationException("IAM受控验证客户端不能为null");
        }
        this.verificationClient = verificationClient;
    }

    /**
     * 资源认证来源标识（IAM）
     */
    @Override
    public ResourceAuthenticationSourceId sourceId() {
        return SOURCE_ID;
    }

    /**
     * Bearer 凭证走 IAM 远程验证并映射结果（拒绝分类见各分支）
     */
    @Override
    public ResourceAuthenticationResult authenticate(ResourceCredential credential) {
        if (!(credential instanceof BearerResourceCredential)
                || !SOURCE_ID.equals(credential.getSourceId())) {
            log.debug("IAM资源认证拒绝：凭据类型或来源不匹配");
            return ResourceAuthenticationResult.rejected(ResourceAuthenticationFailureCategory.CREDENTIAL_MALFORMED);
        }
        try {
            Map<String, Object> claims = verificationClient.verify(((BearerResourceCredential) credential).getToken());
            if (claims == null) {
                log.debug("IAM资源认证拒绝：令牌不活跃");
                return ResourceAuthenticationResult.rejected(ResourceAuthenticationFailureCategory.TOKEN_INACTIVE);
            }
            log.debug("IAM受控验证成功，主体={}", claims.get(SimpleIamCoreConstant.CLAIM_SUBJECT));
            return IamResourceAuthenticationResultHelper.authenticated(
                    IamVerifiedAuthenticationClaimMapper.fromVerifiedClaims(claims));
        } catch (IamResourceVerificationUnavailableException exception) {
            log.warn("IAM受控验证服务不可用，认证拒绝，异常类型={}", exception.getClass().getName());
            return ResourceAuthenticationResult.rejected(ResourceAuthenticationFailureCategory.PROVIDER_UNAVAILABLE);
        } catch (IamResourceVerificationProtocolException exception) {
            log.warn("IAM受控验证响应不符合协议，认证拒绝，异常类型={}", exception.getClass().getName());
            return ResourceAuthenticationResult.rejected(ResourceAuthenticationFailureCategory.AUTHORIZATION_INVALID);
        } catch (RuntimeException exception) {
            log.warn("IAM授权快照映射失败，认证拒绝，异常类型={}", exception.getClass().getName());
            return ResourceAuthenticationResult.rejected(ResourceAuthenticationFailureCategory.AUTHORIZATION_INVALID);
        }
    }
}
