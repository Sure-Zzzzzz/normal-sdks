package io.github.surezzzzzz.sdk.license.server.web;

import io.github.surezzzzzz.sdk.license.server.annotation.SimpleLicenseServerComponent;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourcePrincipal;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceSubjectType;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 默认主体解析桥：公共资源链验证后的 SecurityContext → {@link LicenseRequestContext}。
 *
 * <p>主体归一（与 KMS owner 语义同构）：IAM 人员（HUMAN，含 inherited AKU）为
 * {@code iam:{subjectId}}；AKSK 服务凭据（SERVICE）为 {@code aksk:{clientId}}。
 * 形态不符按未认证拒绝——License 不提供匿名端点。</p>
 *
 * @author surezzzzzz
 */
@SimpleLicenseServerComponent
public class DefaultLicensePrincipalResolver {

    public LicenseRequestContext resolve(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        if (!(principal instanceof VerifiedResourceContext)) {
            return null;
        }
        VerifiedResourceContext verified = (VerifiedResourceContext) principal;
        VerifiedResourcePrincipal resourcePrincipal = verified.getPrincipal();
        ApplicationAuthorizationContext authorization = verified.getApplicationAuthorization();
        if (resourcePrincipal == null || authorization == null) {
            return null;
        }
        ResourceSubjectType subjectType = resourcePrincipal.getSubjectType();
        String subjectId = resourcePrincipal.getSubjectId();
        String sourceId = resourcePrincipal.getSourceId() == null
                ? null : resourcePrincipal.getSourceId().getValue();
        if ((subjectType != ResourceSubjectType.HUMAN && subjectType != ResourceSubjectType.SERVICE)
                || subjectId == null || subjectId.trim().isEmpty() || sourceId == null || sourceId.trim().isEmpty()) {
            return null;
        }
        boolean human = subjectType == ResourceSubjectType.HUMAN;
        String principalId = (human ? "iam:" : "aksk:") + subjectId;
        try {
            String dataJson = authorization.getDataGrantDocument() == null
                    ? null : io.github.surezzzzzz.sdk.license.core.codec.StrictJson.mapper()
                    .writeValueAsString(authorization.getDataGrantDocument());
            return new LicenseRequestContext(principalId,
                    subjectType.name(),
                    authorization.getApiPermissions(),
                    authorization.getPagePermissions(),
                    dataJson,
                    requestIdOf(request), sourceId);
        } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
            // DATA 文档序列化失败按上下文形态不符拒绝（不降级为无 DATA 继续走）
            return null;
        }
    }

    /** 请求关联号：优先网关/资源链透传，缺省生成（仅日志与错误响应关联，不进业务语义）。 */
    private static String requestIdOf(HttpServletRequest request) {
        String incoming = request.getHeader("X-Request-Id");
        if (incoming != null && incoming.matches("[A-Za-z0-9._-]{8,64}")) {
            return incoming;
        }
        return UUID.randomUUID().toString();
    }
}
