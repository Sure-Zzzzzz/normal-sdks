package io.github.surezzzzzz.sdk.ticket.server.test.cases;

import io.github.surezzzzzz.sdk.ticket.server.controller.ManageTicketController;
import io.github.surezzzzzz.sdk.ticket.server.controller.OpenApiTicketController;
import io.github.surezzzzzz.sdk.ticket.server.controller.PortalTicketController;
import io.github.surezzzzzz.sdk.ticket.server.dto.PageResponse;
import io.github.surezzzzzz.sdk.ticket.server.repository.TicketAttachmentRepository;
import io.github.surezzzzzz.sdk.ticket.server.repository.TicketCommentRepository;
import io.github.surezzzzzz.sdk.ticket.server.repository.TicketGroupRepository;
import io.github.surezzzzzz.sdk.ticket.server.repository.TicketRepository;
import io.github.surezzzzzz.sdk.ticket.server.repository.TicketTransitionRepository;
import io.github.surezzzzzz.sdk.ticket.server.repository.TicketTypeRepository;
import io.github.surezzzzzz.sdk.ticket.server.service.TicketCommandService;
import io.github.surezzzzzz.sdk.ticket.server.service.TicketPrincipalResolver;
import io.github.surezzzzzz.sdk.ticket.server.service.TicketQueryService;
import io.github.surezzzzzz.sdk.ticket.server.service.TicketRequestContext;
import io.github.surezzzzzz.sdk.ticket.server.support.TicketRequestParameterParser;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 分页边界：控制器回显规范化参数，查询偏移不发生整数溢出。
 */
class TicketPaginationBoundaryTest {

    @Test
    void parserNormalizesPageAndSize() {
        assertEquals(1, TicketRequestParameterParser.safePage(0));
        assertEquals(1, TicketRequestParameterParser.safePage(-3));
        assertEquals(1, TicketRequestParameterParser.safeSize(0));
        assertEquals(1, TicketRequestParameterParser.safeSize(-3));
        assertEquals(100, TicketRequestParameterParser.safeSize(101));
    }

    @Test
    void controllersQueryAndEchoNormalizedPagination() {
        TicketQueryService queries = mock(TicketQueryService.class);
        TicketCommandService commands = mock(TicketCommandService.class);
        doReturn(Collections.emptyList()).when(queries).myTickets(anyString(), anyCollection(),
                nullable(String.class), nullable(Instant.class), nullable(Instant.class), nullable(String.class),
                anyInt(), anyInt());
        doReturn(Collections.emptyList()).when(queries).queue(anyString(), anyCollection(),
                nullable(io.github.surezzzzzz.sdk.ticket.server.constant.TicketPriority.class),
                nullable(Boolean.class), nullable(String.class), anyInt(), anyInt());
        doReturn(Collections.emptyList()).when(queries).allTickets(anyCollection(), anyInt(), anyInt());
        doReturn(Collections.emptyList()).when(queries).byCredential(anyString(), anyCollection(), anyInt(), anyInt());

        TicketPrincipalResolver person = request -> new TicketRequestContext(
                "person-1", "人员", false, false);
        TicketPrincipalResolver administrator = request -> new TicketRequestContext(
                "person-admin", "管理员", true, false);
        TicketPrincipalResolver machine = request -> new TicketRequestContext(
                "credential-1", "凭据", false, true);
        PortalTicketController portal = new PortalTicketController(person, commands, queries,
                mock(TicketTypeRepository.class));
        ManageTicketController manage = new ManageTicketController(administrator, commands, queries);
        OpenApiTicketController openApi = new OpenApiTicketController(machine, commands, queries);

        PageResponse<?> portalPage = portal.myTickets(null, 0, 0, null);
        assertEquals(1, portalPage.getPage());
        assertEquals(1, portalPage.getSize());
        verify(queries).myTickets(eq("person-1"), anyCollection(), nullable(String.class),
                nullable(Instant.class), nullable(Instant.class), nullable(String.class), eq(1), eq(1));

        PageResponse<?> queuePage = manage.queue(null, 0, 101, null);
        assertEquals(1, queuePage.getPage());
        assertEquals(100, queuePage.getSize());
        verify(queries).queue(eq("person-admin"), anyCollection(), nullable(
                io.github.surezzzzzz.sdk.ticket.server.constant.TicketPriority.class), nullable(Boolean.class),
                nullable(String.class), eq(1), eq(100));

        PageResponse<?> openApiPage = openApi.list(null, 2, -1, null);
        assertEquals(2, openApiPage.getPage());
        assertEquals(1, openApiPage.getSize());
        verify(queries).byCredential(eq("credential-1"), anyCollection(), eq(2), eq(1));
    }

    @Test
    void oversizedPageDoesNotOverflowQueryOffset() {
        TicketRepository tickets = mock(TicketRepository.class);
        doReturn(Collections.emptyList()).when(tickets).findMine(anyString(), anyCollection(),
                eq(Integer.MAX_VALUE), eq(100));
        TicketQueryService queries = new TicketQueryService(tickets, mock(TicketTypeRepository.class),
                mock(TicketCommentRepository.class), mock(TicketTransitionRepository.class),
                mock(TicketAttachmentRepository.class), mock(TicketGroupRepository.class));

        queries.myTickets("person-1", Collections.emptyList(), Integer.MAX_VALUE, 100);

        verify(tickets).findMine(eq("person-1"), anyCollection(), eq(Integer.MAX_VALUE), eq(100));
    }
}
