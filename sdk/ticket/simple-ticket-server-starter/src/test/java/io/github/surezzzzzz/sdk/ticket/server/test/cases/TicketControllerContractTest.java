package io.github.surezzzzzz.sdk.ticket.server.test.cases;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.ticket.server.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.ticket.server.constant.SimpleTicketServerConstant;
import io.github.surezzzzzz.sdk.ticket.server.constant.TicketServerLogConstant;
import io.github.surezzzzzz.sdk.ticket.server.constant.TicketPriority;
import io.github.surezzzzzz.sdk.ticket.server.constant.TicketStatus;
import io.github.surezzzzzz.sdk.ticket.server.controller.ManageTicketController;
import io.github.surezzzzzz.sdk.ticket.server.controller.ManageAttachmentController;
import io.github.surezzzzzz.sdk.ticket.server.controller.OpenApiTicketController;
import io.github.surezzzzzz.sdk.ticket.server.controller.PortalAttachmentController;
import io.github.surezzzzzz.sdk.ticket.server.controller.PortalTicketController;
import io.github.surezzzzzz.sdk.ticket.server.controller.TicketExceptionAdvice;
import io.github.surezzzzzz.sdk.ticket.server.controller.TicketAdminController;
import io.github.surezzzzzz.sdk.ticket.server.dto.AssignRequest;
import io.github.surezzzzzz.sdk.ticket.server.dto.AttachmentPresignRequest;
import io.github.surezzzzzz.sdk.ticket.server.dto.CreateTicketRequest;
import io.github.surezzzzzz.sdk.ticket.server.dto.OpenApiCreateTicketRequest;
import io.github.surezzzzzz.sdk.ticket.server.dto.RequestInfoRequest;
import io.github.surezzzzzz.sdk.ticket.server.dto.TicketErrorResponse;
import io.github.surezzzzzz.sdk.ticket.server.dto.TicketGroupRequest;
import io.github.surezzzzzz.sdk.ticket.server.dto.TicketTypeRequest;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketAttachmentRejectException;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketForbiddenActionException;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketGroupHasOpenTicketsException;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketServiceException;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketTypeNotFoundException;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketValidationException;
import io.github.surezzzzzz.sdk.ticket.server.model.Ticket;
import io.github.surezzzzzz.sdk.ticket.server.repository.TicketTypeRepository;
import io.github.surezzzzzz.sdk.ticket.server.service.TicketCommandService;
import io.github.surezzzzzz.sdk.ticket.server.service.TicketPrincipalResolver;
import io.github.surezzzzzz.sdk.ticket.server.service.TicketQueryService;
import io.github.surezzzzzz.sdk.ticket.server.service.TicketRequestContext;
import io.github.surezzzzzz.sdk.ticket.server.service.TypeGroupAdminService;
import io.github.surezzzzzz.sdk.ticket.server.test.support.TicketLogCapture;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Controller 权限和 HTTP 响应契约。
 */
@Slf4j
class TicketControllerContractTest {

    @Test
    void createAndAttachmentMethodsCarryApiPermissions() throws Exception {
        assertNotNull(OpenApiTicketController.class.getMethod("create", HttpServletRequest.class,
                String.class, OpenApiCreateTicketRequest.class).getAnnotation(RequireApiPermission.class));
        assertNotNull(PortalAttachmentController.class.getMethod("presign", HttpServletRequest.class,
                AttachmentPresignRequest.class).getAnnotation(RequireApiPermission.class));
        assertNotNull(PortalAttachmentController.class.getMethod("downloadUrl", HttpServletRequest.class,
                String.class, Long.class).getAnnotation(RequireApiPermission.class));
        assertNotNull(ManageAttachmentController.class.getMethod("downloadUrl", HttpServletRequest.class,
                String.class, Long.class).getAnnotation(RequireApiPermission.class));
    }

    @Test
    void adminAndAssignmentMethodsDeclareExactApiPermissions() throws Exception {
        assertApiPermission(ManageTicketController.class.getMethod("detail", HttpServletRequest.class,
                String.class), SimpleTicketServerConstant.PERMISSION_READ);
        assertApiPermission(ManageTicketController.class.getMethod("timeline", HttpServletRequest.class,
                String.class, String.class, int.class), SimpleTicketServerConstant.PERMISSION_READ);
        assertApiPermission(TicketAdminController.class.getMethod("types", HttpServletRequest.class),
                SimpleTicketServerConstant.PERMISSION_TYPE_ADMIN);
        assertApiPermission(TicketAdminController.class.getMethod("saveType", HttpServletRequest.class,
                TicketTypeRequest.class),
                SimpleTicketServerConstant.PERMISSION_TYPE_ADMIN);
        assertApiPermission(TicketAdminController.class.getMethod("groups", HttpServletRequest.class),
                SimpleTicketServerConstant.PERMISSION_GROUP_ADMIN);
        assertApiPermission(TicketAdminController.class.getMethod("saveGroup", HttpServletRequest.class,
                TicketGroupRequest.class),
                SimpleTicketServerConstant.PERMISSION_GROUP_ADMIN);
        assertApiPermission(TicketAdminController.class.getMethod("deleteGroup", HttpServletRequest.class,
                String.class), SimpleTicketServerConstant.PERMISSION_GROUP_ADMIN);
        assertApiPermission(TicketAdminController.class.getMethod("report", HttpServletRequest.class,
                String.class, String.class),
                SimpleTicketServerConstant.PERMISSION_REPORT);
        assertApiPermission(ManageTicketController.class.getMethod("assign", HttpServletRequest.class,
                String.class, AssignRequest.class), SimpleTicketServerConstant.PERMISSION_ASSIGN);
        assertApiPermission(ManageTicketController.class.getMethod("transfer", HttpServletRequest.class,
                String.class, AssignRequest.class), SimpleTicketServerConstant.PERMISSION_TRANSFER);
    }

    @Test
    void pageAllDataIdentityDoesNotGrantAdminApiPermissions() {
        TicketPrincipalResolver resolver = request -> new TicketRequestContext(
                "person-admin", "管理员", true, false);
        TypeGroupAdminService adminService = mock(TypeGroupAdminService.class);
        TicketAdminController controller = new TicketAdminController(resolver, adminService,
                mock(NamedParameterJdbcTemplate.class));

        assertThrows(TicketForbiddenActionException.class, () -> controller.types(null));
        assertThrows(TicketForbiddenActionException.class, () -> controller.groups(null));
        assertThrows(TicketForbiddenActionException.class, () -> controller.report(null, null, null));
        verifyNoInteractions(adminService);
    }

    @Test
    void firstCreateIs201AndReplayIs200() {
        TicketCommandService commands = mock(TicketCommandService.class);
        OpenApiTicketController controller = new OpenApiTicketController(
                request -> new TicketRequestContext("credential-1", "credential-1", false, true),
                commands, mock(TicketQueryService.class));
        OpenApiCreateTicketRequest body = OpenApiCreateTicketRequest.builder()
                .typeCode("fault").subject("标题").ownerSubjectId("person-1").build();
        Ticket ticket = Ticket.builder().ticketNo("TK261015000001").typeCode("fault")
                .subject("标题").status(TicketStatus.SUBMITTED).priority(TicketPriority.NORMAL)
                .ownerSubjectId("person-1").createdAt(Instant.now()).lastActivityAt(Instant.now()).build();
        when(commands.createOpenApiResult("credential-1", "key-1", "fault", "标题", null,
                null, "person-1", null)).thenReturn(new TicketCommandService.OpenApiCreateResult(ticket, true),
                new TicketCommandService.OpenApiCreateResult(ticket, false));
        assertEquals(HttpStatus.CREATED, controller.create(null, "key-1", body).getStatusCode());
        assertEquals(HttpStatus.OK, controller.create(null, "key-1", body).getStatusCode());
    }

    @Test
    void portalCreateRequiresIdempotencyKeyBeforeCallingCommandService() {
        TicketCommandService commands = mock(TicketCommandService.class);
        PortalTicketController controller = new PortalTicketController(
                request -> new TicketRequestContext("person-1", "人员", false, false),
                commands, mock(TicketQueryService.class), mock(TicketTypeRepository.class));
        CreateTicketRequest body = CreateTicketRequest.builder()
                .typeCode("fault")
                .subject("标题")
                .build();

        TicketValidationException error = assertThrows(TicketValidationException.class,
                () -> controller.create(null, " ", body));
        assertEquals("idempotencyKey", error.getField());
        verifyNoInteractions(commands);
    }

    @Test
    void adviceReturnsSafeMessageAndHttpStatus() {
        TicketExceptionAdvice advice = new TicketExceptionAdvice();
        assertEquals(HttpStatus.NOT_FOUND, advice.handle(new TicketTypeNotFoundException()).getStatusCode());
        TicketErrorResponse body = advice.handle(new TicketTypeNotFoundException()).getBody();
        assertEquals(ErrorMessage.TICKET_TYPE_NOT_FOUND, body.getMessage());
        assertNotNull(Instant.parse(body.getTime()));
        TicketErrorResponse validationBody = advice.handle(
                new TicketValidationException("private-field", "private-reason")).getBody();
        assertEquals(ErrorMessage.TICKET_VALIDATION_FAILED, validationBody.getMessage());
        assertFalse(validationBody.toString().contains("private-field"));
        assertFalse(validationBody.toString().contains("private-reason"));
        assertEquals(HttpStatus.BAD_REQUEST,
                advice.handle(new TicketAttachmentRejectException()).getStatusCode());
        assertEquals(HttpStatus.CONFLICT,
                advice.handle(new TicketGroupHasOpenTicketsException()).getStatusCode());
    }

    @Test
    void internalFailureDoesNotExposeCauseInResponseOrLog() {
        log.debug("验证内部错误响应与日志脱敏契约");
        TicketServiceException error = new TicketServiceException(
                privateServiceFailure());

        try (TicketLogCapture capture = new TicketLogCapture(TicketExceptionAdvice.class, Level.ERROR)) {
            org.springframework.http.ResponseEntity<TicketErrorResponse> response =
                    new TicketExceptionAdvice().handle(error);

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertEquals(ErrorMessage.TICKET_SERVICE_ERROR, response.getBody().getMessage());
            assertEquals(1, capture.events().size());
            String message = capture.events().get(0).getMessage().getFormattedMessage();
            assertTrue(message.contains("stack=["));
            assertTrue(message.contains("causeType=RuntimeException"));
            assertTrue(message.contains("causeStack=["));
            assertTrue(message.contains("TicketControllerContractTest#privateServiceFailure:"));
            assertFalse(message.contains("private-service-value"));
            assertFalse(message.contains(".java:"));
            String frames = message.substring(message.indexOf("stack=[") + "stack=[".length(),
                    message.indexOf(']', message.indexOf("stack=[")));
            assertTrue(frames.split(", ").length <= TicketServerLogConstant.MAX_ERROR_STACK_FRAMES);
            String causeFrames = message.substring(message.indexOf("causeStack=[") + "causeStack=[".length(),
                    message.lastIndexOf(']'));
            assertTrue(causeFrames.split(", ").length <= TicketServerLogConstant.MAX_ERROR_STACK_FRAMES);
            assertNull(capture.events().get(0).getThrown());
        }
    }

    @Test
    void frameworkRequestFailuresUseSafeErrorContract() {
        TicketExceptionAdvice advice = new TicketExceptionAdvice();
        assertSafeBadRequest(advice.handleInvalidRequest(new HttpMessageNotReadableException("raw body")));
        assertSafeBadRequest(advice.handleInvalidRequest(mock(MethodArgumentNotValidException.class)));
        assertSafeBadRequest(advice.handleInvalidRequest(mock(MethodArgumentTypeMismatchException.class)));
        assertSafeBadRequest(advice.handleInvalidRequest(
                new MissingServletRequestParameterException("page", "int")));
        assertSafeBadRequest(advice.handleInvalidRequest(mock(MissingRequestHeaderException.class)));
    }

    @Test
    void unexpectedFailureUsesSafeInternalErrorContract() {
        TicketExceptionAdvice advice = new TicketExceptionAdvice();
        TicketErrorResponse response = advice.handleUnexpected(new RuntimeException("token=private-value"))
                .getBody();
        assertEquals(ErrorMessage.TICKET_SERVICE_ERROR, response.getMessage());
        assertFalse(response.toString().contains("private-value"));
        assertNotNull(Instant.parse(response.getTime()));
    }

    private static void assertSafeBadRequest(ResponseEntity<TicketErrorResponse> response) {
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(ErrorMessage.TICKET_VALIDATION_FAILED, response.getBody().getMessage());
        assertNotNull(Instant.parse(response.getBody().getTime()));
    }

    private static RuntimeException privateServiceFailure() {
        return new RuntimeException("secret=private-service-value");
    }

    @Test
    void invalidStatusFiltersReturnFieldLevel400BeforeQueries() {
        TicketQueryService queries = mock(TicketQueryService.class);
        TicketCommandService commands = mock(TicketCommandService.class);
        TicketPrincipalResolver person = request -> new TicketRequestContext("person-1", "人员", true, false);
        TicketPrincipalResolver machine = request -> new TicketRequestContext("credential-1", "机器", false, true);
        PortalTicketController portal = new PortalTicketController(person, commands, queries,
                mock(TicketTypeRepository.class));
        ManageTicketController manage = new ManageTicketController(person, commands, queries);
        OpenApiTicketController openApi = new OpenApiTicketController(machine, commands, queries);

        assertBadRequestField("status", () -> portal.myTickets(null, 1, 20,
                Collections.singletonList("UNKNOWN")));
        assertBadRequestField("status", () -> manage.queue(null, 1, 20,
                Collections.singletonList("UNKNOWN")));
        assertBadRequestField("status", () -> manage.allTickets(null, 1, 20,
                Collections.singletonList("UNKNOWN")));
        assertBadRequestField("status", () -> openApi.list(null, 1, 20,
                Collections.singletonList("UNKNOWN")));
        verifyNoInteractions(queries, commands);
    }

    @Test
    void invalidReasonAndTimelineCursorReturnFieldLevel400BeforeServices() {
        TicketQueryService queries = mock(TicketQueryService.class);
        TicketCommandService commands = mock(TicketCommandService.class);
        TicketPrincipalResolver person = request -> new TicketRequestContext("person-1", "人员", false, false);
        PortalTicketController portal = new PortalTicketController(person, commands, queries,
                mock(TicketTypeRepository.class));
        ManageTicketController manage = new ManageTicketController(person, commands, queries);

        assertBadRequestField("reason", () -> manage.requestInfo(null, "TK1",
                RequestInfoRequest.builder().reason("UNKNOWN").build()));
        assertBadRequestField("before", () -> portal.timeline(null, "TK1", "not-an-instant", 20));
        verifyNoInteractions(queries, commands);
    }

    private static void assertBadRequestField(String field, org.junit.jupiter.api.function.Executable call) {
        TicketValidationException error = assertThrows(TicketValidationException.class, call);
        assertEquals(field, error.getField());
        assertEquals(HttpStatus.BAD_REQUEST, new TicketExceptionAdvice().handle(error).getStatusCode());
    }

    private static void assertApiPermission(Method method, String expected) {
        RequireApiPermission annotation = method.getAnnotation(RequireApiPermission.class);
        assertNotNull(annotation, method.getName() + " 必须声明精确 API 权限");
        assertEquals(expected, annotation.value(), method.getName() + " API 权限码不匹配");
    }
}
