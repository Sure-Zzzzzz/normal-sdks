package io.github.surezzzzzz.sdk.crm.server.service;

import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceSubjectType;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext;
import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmOwner;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Resource Server 协作形态的 owner 翻译桥。仅在宿主引入 resource-core 时装配。
 *
 * @author surezzzzzz
 */
public class SimpleCrmServerResourceOwnerResolverBridge extends SimpleCrmServerOwnerResolverBridge {

    @Override
    public CrmOwner resolve() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        if (!(principal instanceof VerifiedResourceContext)) {
            // Resource 协作形态只接受公共 Resource 层生成的已验证上下文，不能退回
            // Authentication.getName()，否则可能绕过应用准入和主体类型校验。
            throw new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                    "verifiedResourceContext");
        }
        VerifiedResourceContext context = (VerifiedResourceContext) principal;
        if (context.getPrincipal() == null) {
            throw new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE, "principal");
        }
        String subjectId = context.getPrincipal().getSubjectId();
        if (subjectId == null || subjectId.trim().isEmpty()) {
            throw new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE, "subjectId");
        }
        if (context.getPrincipal().getSubjectType() == ResourceSubjectType.HUMAN) {
            return CrmOwner.human(subjectId);
        }
        return CrmOwner.service(subjectId);
    }
}
