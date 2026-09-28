package io.github.surezzzzzz.sdk.auth.aksk.server.service;

import io.github.surezzzzzz.sdk.auth.aksk.server.annotation.SimpleAkskServerComponent;
import io.github.surezzzzzz.sdk.auth.aksk.server.configuration.SimpleAkskServerProperties;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceSubjectType;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;

/**
 * 自助入口的 owner 解析器。
 *
 * <p>只消费公共资源层放入 SecurityContext 的已验证身份源 HUMAN，禁止接受浏览器提供的 owner 字段。</p>
 */
@SimpleAkskServerComponent
@Slf4j
@RequiredArgsConstructor
public class AkskSelfServicePrincipalResolver {

    private final SimpleAkskServerProperties properties;

    public AkskSelfServicePrincipal resolve() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof VerifiedResourceContext)) {
            log.debug("AKU自助主体解析拒绝：认证上下文类型不匹配");
            return null;
        }
        VerifiedResourceContext context = (VerifiedResourceContext) authentication.getPrincipal();
        String subjectId = context.getPrincipal().getSubjectId();
        if (!StringUtils.hasText(subjectId)
                || subjectId.length() > io.github.surezzzzzz.sdk.auth.aksk.server.constant.SimpleAkskServerConstant.OWNER_SUBJECT_ID_MAX_LENGTH) {
            log.debug("AKU自助主体解析拒绝：subjectId 不符合身份源稳定主体契约");
            return null;
        }
        if (context.getPrincipal().getSubjectType() != ResourceSubjectType.HUMAN
                || !properties.getOwnerAuthorization().getHumanResourceSourceId()
                .equals(context.getPrincipal().getSourceId().getValue())) {
            log.debug("AKU自助主体解析拒绝：subjectType={}, sourceId={}, expectedSourceId={}",
                    context.getPrincipal().getSubjectType(), context.getPrincipal().getSourceId().getValue(),
                    properties.getOwnerAuthorization().getHumanResourceSourceId());
            return null;
        }
        return new AkskSelfServicePrincipal(properties.getOwnerAuthorization().getOwnerSourceId(),
                subjectId, context.getRequestId());
    }
}
