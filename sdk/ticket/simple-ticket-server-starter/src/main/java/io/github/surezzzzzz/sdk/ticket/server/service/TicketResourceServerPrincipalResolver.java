package io.github.surezzzzzz.sdk.ticket.server.service;

import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext;
import io.github.surezzzzzz.sdk.ticket.server.constant.SimpleTicketServerConstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.servlet.http.HttpServletRequest;

/**
 * 组合式 Resource Server 认证桥（feedback/KMS 同型定式，DESIGN §5.3）。
 *
 * <p>只做翻译不验证凭据；人员面收 HUMAN 主体，openapi 面收机器主体（凭据标识进上下文）。
 * 双主体均要求形态与 subjectId 合法，否则 null 走 401 链，不猜测不回退。
 * 日志红线：只输出原因码，不输出 token/claim/subjectId 裸值。</p>
 *
 * @author surezzzzzz
 */
public class TicketResourceServerPrincipalResolver implements TicketPrincipalResolver {

    private static final Logger log = LoggerFactory.getLogger(TicketResourceServerPrincipalResolver.class);

    private static final String REASON_SHAPE_MISMATCH = "resource-context-shape-mismatch";

    private static final String REASON_EMPTY_SUBJECT = "subject-empty";

    @Override
    public TicketRequestContext resolve(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        if (!(principal instanceof VerifiedResourceContext)) {
            log.info("工单资源层主体解析拒绝 原因码={}", REASON_SHAPE_MISMATCH);
            return null;
        }
        VerifiedResourceContext context = (VerifiedResourceContext) principal;
        if (context.getPrincipal() == null || context.getPrincipal().getSubjectId() == null
                || context.getPrincipal().getSubjectId().trim().isEmpty()) {
            log.info("工单资源层主体解析拒绝 原因码={}", REASON_EMPTY_SUBJECT);
            return null;
        }
        String subjectId = context.getPrincipal().getSubjectId().trim();
        boolean machine = context.getPrincipal().getSubjectType() == ResourceSubjectType.SERVICE;
        ApplicationAuthorizationContext authorization = context.getApplicationAuthorization();
        boolean allTicketRead = hasAllTicketRead(authorization == null ? null : authorization.getDataGrantDocument());
        return new TicketRequestContext(subjectId, subjectId, allTicketRead, machine,
                authorization == null ? null : authorization.getApiPermissions());
    }

    private boolean hasAllTicketRead(DataGrantDocument document) {
        if (document == null || document.getGrants() == null) {
            return false;
        }
        for (DataGrant grant : document.getGrants()) {
            if (SimpleTicketServerConstant.DATA_RESOURCE_TICKET.equals(grant.getResource())
                    && grant.getActions().contains(SimpleTicketServerConstant.DATA_ACTION_READ)
                    && grant.isAll()) {
                return true;
            }
        }
        return false;
    }
}
