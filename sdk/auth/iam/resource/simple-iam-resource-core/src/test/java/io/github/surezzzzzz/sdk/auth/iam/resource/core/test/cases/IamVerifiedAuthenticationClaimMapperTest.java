package io.github.surezzzzzz.sdk.auth.iam.resource.core.test.cases;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.claim.ApplicationAuthorizationContextClaimMapper;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.SimpleApplicationAuthorizationConstant;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.iam.core.constant.SimpleIamCoreConstant;
import io.github.surezzzzzz.sdk.auth.iam.resource.core.exception.IamResourceAuthenticationException;
import io.github.surezzzzzz.sdk.auth.iam.resource.core.model.IamVerifiedAuthentication;
import io.github.surezzzzzz.sdk.auth.iam.resource.core.support.IamResourceAuthenticationResultHelper;
import io.github.surezzzzzz.sdk.auth.iam.resource.core.support.IamVerifiedAuthenticationClaimMapper;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceSubjectType;
import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationResult;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * IAM已验证认证Claim映射器测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class IamVerifiedAuthenticationClaimMapperTest {

    @Test
    void shouldMapVerifiedHumanIdentityAndAuthorizationToCommonResult() {
        Map<String, Object> claims = verifiedClaims(authorization(ApplicationAuthorizationSubjectType.HUMAN, "user-a"));

        IamVerifiedAuthentication authentication = IamVerifiedAuthenticationClaimMapper.fromVerifiedClaims(claims);
        ResourceAuthenticationResult result = IamResourceAuthenticationResultHelper.authenticated(authentication);

        log.info("IAM公共主体：{}", result.getPrincipal());
        assertEquals("user-a", authentication.getSubjectId(), "必须保留IAM稳定主体标识");
        assertEquals("iam", result.getPrincipal().getSourceId().getValue(), "必须固定映射为IAM来源");
        assertEquals(ResourceSubjectType.HUMAN, result.getPrincipal().getSubjectType(), "IAM必须映射为人员主体");
        assertEquals("user-a", result.getApplicationAuthorization().getSubjectId(), "授权快照必须绑定同一主体");
    }

    @Test
    void shouldRejectMalformedClaimAndInvalidAuthorizationBinding() {
        Map<String, Object> nonTextSubject = verifiedClaims(authorization(ApplicationAuthorizationSubjectType.HUMAN, "user-a"));
        nonTextSubject.put(SimpleIamCoreConstant.CLAIM_SUBJECT, Long.valueOf(1L));
        Map<String, Object> malformedAuthorization = verifiedClaims(authorization(ApplicationAuthorizationSubjectType.HUMAN, "user-a"));
        malformedAuthorization.put(SimpleIamCoreConstant.CLAIM_APPLICATION_AUTHORIZATION, Collections.emptyMap());
        Map<String, Object> serviceAuthorization = verifiedClaims(authorization(ApplicationAuthorizationSubjectType.SERVICE, "user-a"));
        Map<String, Object> mismatchedAuthorization = verifiedClaims(authorization(ApplicationAuthorizationSubjectType.HUMAN, "user-b"));
        mismatchedAuthorization.put(SimpleIamCoreConstant.CLAIM_SUBJECT, "user-a");

        log.info("IAM非法主体、快照结构、服务主体与主体绑定均必须拒绝");
        assertThrows(IamResourceAuthenticationException.class,
                () -> IamVerifiedAuthenticationClaimMapper.fromVerifiedClaims(nonTextSubject),
                "非文本IAM主体必须拒绝");
        assertThrows(IamResourceAuthenticationException.class,
                () -> IamVerifiedAuthenticationClaimMapper.fromVerifiedClaims(malformedAuthorization),
                "非法授权快照必须拒绝");
        assertThrows(IamResourceAuthenticationException.class,
                () -> IamVerifiedAuthenticationClaimMapper.fromVerifiedClaims(serviceAuthorization),
                "服务主体授权快照不得映射为IAM人员主体");
        assertThrows(IamResourceAuthenticationException.class,
                () -> IamVerifiedAuthenticationClaimMapper.fromVerifiedClaims(mismatchedAuthorization),
                "主体与授权快照不一致必须拒绝");
        assertThrows(IamResourceAuthenticationException.class,
                () -> new IamVerifiedAuthentication("user-a", authorization(ApplicationAuthorizationSubjectType.HUMAN, "user-b")),
                "直接构造也必须保持主体绑定");
    }

    private Map<String, Object> verifiedClaims(ApplicationAuthorizationContext authorization) {
        Map<String, Object> claims = new LinkedHashMap<String, Object>();
        claims.put(SimpleIamCoreConstant.CLAIM_SUBJECT, authorization.getSubjectId());
        claims.put(SimpleIamCoreConstant.CLAIM_APPLICATION_AUTHORIZATION,
                ApplicationAuthorizationContextClaimMapper.toClaim(authorization));
        return claims;
    }

    private ApplicationAuthorizationContext authorization(ApplicationAuthorizationSubjectType subjectType, String subjectId) {
        return new ApplicationAuthorizationContext(
                SimpleApplicationAuthorizationConstant.PROTOCOL,
                SimpleApplicationAuthorizationConstant.VERSION,
                subjectType,
                subjectId,
                "application-a",
                true,
                Collections.<String>emptyList(),
                Collections.<String>emptyList(),
                Collections.singletonList("api.read"),
                null,
                1L,
                "manifest-1",
                "digest-a",
                Instant.ofEpochSecond(100L),
                Instant.ofEpochSecond(200L));
    }
}
