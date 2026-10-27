package io.github.surezzzzzz.sdk.ticket.server.test.cases;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataConstraintOperator;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataConstraint;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceSubjectType;
import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationSourceId;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourcePrincipal;
import io.github.surezzzzzz.sdk.ticket.server.service.TicketRequestContext;
import io.github.surezzzzzz.sdk.ticket.server.service.TicketResourceServerPrincipalResolver;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 三权桥契约。
 */
@Slf4j
class TicketResourceServerPrincipalResolverTest {

    private final TicketResourceServerPrincipalResolver resolver = new TicketResourceServerPrincipalResolver();

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void pagePermissionDoesNotGrantAllTicketData() {
        setContext(null);

        TicketRequestContext context = resolver.resolve(null);
        log.debug("验证 PAGE 与 DATA 分离：全量数据身份={}", context.isAllTicketRead());

        assertFalse(context.isAllTicketRead(), "仅有 PAGE 菜单权限不能授予全量工单 DATA");
    }

    @Test
    void allTicketReadDataGrantEnablesAllTicketData() {
        setContext(new DataGrantDocument("simple-data-permission", "1.0",
                Collections.singletonList(new DataGrant("ticket", Collections.singletonList("read"), true,
                        Collections.emptyList()))));

        TicketRequestContext context = resolver.resolve(null);
        log.debug("验证 DATA 全量授权：全量数据身份={}", context.isAllTicketRead());

        assertTrue(context.isAllTicketRead(), "ticket/read/all=true DATA 应授予全量工单数据身份");
    }

    private void setContext(DataGrantDocument dataGrantDocument) {
        VerifiedResourcePrincipal principal = new VerifiedResourcePrincipal(
                new ResourceAuthenticationSourceId("ticket-test"), ResourceSubjectType.HUMAN, "person-1");
        ApplicationAuthorizationContext authorization = new ApplicationAuthorizationContext(
                "simple-application-authorization", "1.0", ApplicationAuthorizationSubjectType.HUMAN,
                "person-1", "ticket", true, Collections.singletonList("ticket-user"),
                Collections.singletonList("ticket.page.all"), Collections.singletonList("ticket:read"),
                dataGrantDocument, 1L, "1", "digest", Instant.now(), Instant.now().plusSeconds(3600));
        VerifiedResourceContext verified = new VerifiedResourceContext(principal, authorization, "request-ticket-test");
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(verified, null));
    }
}
