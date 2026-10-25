package io.github.surezzzzzz.sdk.feedback.server.test.cases;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.SimpleApplicationAuthorizationConstant;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.SimpleDataPermissionConstant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataPermissionRequest;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceSubjectType;
import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationSourceId;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourcePrincipal;
import io.github.surezzzzzz.sdk.feedback.server.configuration.SimpleFeedbackServerProperties;
import io.github.surezzzzzz.sdk.feedback.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.feedback.server.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.feedback.server.constant.FeedbackPermissionConstant;
import io.github.surezzzzzz.sdk.feedback.server.constant.SimpleFeedbackServerConstant;
import io.github.surezzzzzz.sdk.feedback.server.controller.ApplicationController;
import io.github.surezzzzzz.sdk.feedback.server.controller.SubmissionController;
import io.github.surezzzzzz.sdk.feedback.server.controller.request.ApplicationUpsertRequest;
import io.github.surezzzzzz.sdk.feedback.server.controller.request.SubmissionCreateRequest;
import io.github.surezzzzzz.sdk.feedback.server.controller.request.SubmissionHandlingRequest;
import io.github.surezzzzzz.sdk.feedback.server.controller.response.SubmissionResponse;
import io.github.surezzzzzz.sdk.feedback.server.exception.FeedbackStateException;
import io.github.surezzzzzz.sdk.feedback.server.exception.SimpleFeedbackServerException;
import io.github.surezzzzzz.sdk.feedback.server.exception.SubmissionValidationException;
import io.github.surezzzzzz.sdk.feedback.server.model.FeedbackAttachment;
import io.github.surezzzzzz.sdk.feedback.server.model.FeedbackApplicationAccessScope;
import io.github.surezzzzzz.sdk.feedback.server.model.FeedbackApplication;
import io.github.surezzzzzz.sdk.feedback.server.model.FeedbackSubmission;
import io.github.surezzzzzz.sdk.feedback.server.repository.FeedbackAttachmentRepository;
import io.github.surezzzzzz.sdk.feedback.server.repository.FeedbackSubmissionRepository;
import io.github.surezzzzzz.sdk.feedback.server.service.ApplicationRegistryService;
import io.github.surezzzzzz.sdk.feedback.server.service.ApplicationSourceService;
import io.github.surezzzzzz.sdk.feedback.server.service.AttachmentStorageService;
import io.github.surezzzzzz.sdk.feedback.server.service.FeedbackPrincipalResolver;
import io.github.surezzzzzz.sdk.feedback.server.service.FeedbackRequestContext;
import io.github.surezzzzzz.sdk.feedback.server.service.FeedbackResourceServerPrincipalResolver;
import io.github.surezzzzzz.sdk.feedback.server.service.HintService;
import io.github.surezzzzzz.sdk.feedback.server.service.SubmissionCreateService;
import io.github.surezzzzzz.sdk.feedback.server.service.SubmissionHandlingService;
import io.github.surezzzzzz.sdk.feedback.server.service.SubmissionQueryService;
import io.github.surezzzzzz.sdk.feedback.server.support.FeedbackRequestExceptionHandler;
import io.github.surezzzzzz.sdk.feedback.server.support.FeedbackPreControllerExceptionResolver;
import io.github.surezzzzzz.sdk.feedback.server.support.FeedbackRestExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 反馈 REST 异常响应契约：错误码只决定 HTTP 状态，响应体不泄露内部码或异常细节。
 *
 * @author surezzzzzz
 */
class FeedbackRestExceptionHandlerTest {

    private final FeedbackRestExceptionHandler handler = new FeedbackRestExceptionHandler();
    private final ObjectMapper mapper = new ObjectMapper();

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    private static Map<String, HttpStatus> expectedStatuses() {
        Map<String, HttpStatus> statuses = new LinkedHashMap<>();
        statuses.put(ErrorCode.SCORE_OUT_OF_RANGE, HttpStatus.BAD_REQUEST);
        statuses.put(ErrorCode.CONTENT_INVALID, HttpStatus.BAD_REQUEST);
        statuses.put(ErrorCode.EMAIL_INVALID, HttpStatus.BAD_REQUEST);
        statuses.put(ErrorCode.FIELD_TOO_LONG, HttpStatus.BAD_REQUEST);
        statuses.put(ErrorCode.ENVIRONMENT_KEY_REJECTED, HttpStatus.BAD_REQUEST);
        statuses.put(ErrorCode.APPLICATION_CODE_INVALID, HttpStatus.BAD_REQUEST);
        statuses.put(ErrorCode.ENUM_VALUE_INVALID, HttpStatus.BAD_REQUEST);
        statuses.put(ErrorCode.REQUIRED_FIELD_MISSING, HttpStatus.BAD_REQUEST);
        statuses.put(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST);
        statuses.put(ErrorCode.ATTACHMENT_NOT_ENABLED, HttpStatus.BAD_REQUEST);
        statuses.put(ErrorCode.ATTACHMENT_MEDIA_TYPE_REJECTED, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        statuses.put(ErrorCode.ATTACHMENT_SIZE_EXCEEDED, HttpStatus.PAYLOAD_TOO_LARGE);
        statuses.put(ErrorCode.ATTACHMENT_TOTAL_SIZE_EXCEEDED, HttpStatus.PAYLOAD_TOO_LARGE);
        statuses.put(ErrorCode.ATTACHMENT_COUNT_EXCEEDED, HttpStatus.BAD_REQUEST);
        statuses.put(ErrorCode.ILLEGAL_STATUS_TRANSITION, HttpStatus.CONFLICT);
        statuses.put(ErrorCode.ROW_VERSION_CONFLICT, HttpStatus.CONFLICT);
        statuses.put(ErrorCode.IDEMPOTENCY_DIGEST_MISMATCH, HttpStatus.CONFLICT);
        statuses.put(ErrorCode.APPLICATION_NOT_AVAILABLE, HttpStatus.BAD_REQUEST);
        statuses.put(ErrorCode.SUBMISSION_NOT_FOUND, HttpStatus.NOT_FOUND);
        statuses.put(ErrorCode.APPLICATION_NOT_FOUND, HttpStatus.NOT_FOUND);
        statuses.put(ErrorCode.APPLICATION_ALREADY_REGISTERED, HttpStatus.CONFLICT);
        statuses.put(ErrorCode.RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS);
        statuses.put(ErrorCode.DEPENDENCY_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
        statuses.put(ErrorCode.IAM_SOURCED_FIELD_LOCKED, HttpStatus.CONFLICT);
        statuses.put(ErrorCode.COLLABORATION_ASSEMBLY_INCOMPLETE, HttpStatus.INTERNAL_SERVER_ERROR);
        return statuses;
    }

    @Test
    @DisplayName("每个已声明错误码均有明确 HTTP 状态，新增码不能静默落入默认分支")
    void testAllErrorCodesMapToExpectedStatus() {
        Map<String, HttpStatus> statuses = expectedStatuses();
        Set<String> declaredCodes = new LinkedHashSet<>();
        for (Field field : ErrorCode.class.getDeclaredFields()) {
            if (Modifier.isPublic(field.getModifiers()) && Modifier.isStatic(field.getModifiers())
                    && field.getType() == String.class) {
                declaredCodes.add(assertDoesNotThrow(() -> (String) field.get(null)));
            }
        }
        assertEquals(declaredCodes, statuses.keySet(), "错误码增减时须同步审查 HTTP 映射");
        for (Map.Entry<String, HttpStatus> entry : statuses.entrySet()) {
            ResponseEntity<Map<String, Object>> response = handler.handleDomain(
                    new SimpleFeedbackServerException(entry.getKey(), "安全业务消息"));
            assertEquals(entry.getValue(), response.getStatusCode(), entry.getKey());
        }
    }

    @Test
    @DisplayName("字段超长即使由状态异常子类抛出，也按错误码返回 400")
    void testErrorCodeTakesPrecedenceOverExceptionType() {
        ResponseEntity<Map<String, Object>> response = handler.handleDomain(
                new FeedbackStateException(ErrorCode.FIELD_TOO_LONG, "安全业务消息"));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("未知错误码返回脱敏 500，关联 ID 在响应头与错误体中一致")
    void testUnknownCodeFailsClosedAndCorrelatesResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-Id", "feedback-test-correlation");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        ResponseEntity<Map<String, Object>> response = handler.handleDomain(
                new SimpleFeedbackServerException("UNKNOWN_999", "private internal details"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("feedback-test-correlation", response.getHeaders().getFirst("X-Correlation-Id"));
        assertNotNull(response.getBody());
        assertEquals(new LinkedHashSet<>(Arrays.asList("message", "timestamp", "correlationId")),
                response.getBody().keySet());
        assertEquals(ErrorMessage.INTERNAL_ERROR, response.getBody().get("message"));
        assertEquals("feedback-test-correlation", response.getBody().get("correlationId"));
        assertDoesNotThrow(() -> Instant.parse((String) response.getBody().get("timestamp")));
    }

    @Test
    @DisplayName("数据库访问故障映射为 503，响应只返回安全依赖消息")
    void testDataAccessFailureMapsToServiceUnavailable() {
        ResponseEntity<Map<String, Object>> response = new FeedbackRequestExceptionHandler()
                .handleDataAccessFailure(new DataAccessResourceFailureException("private database details"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals(ErrorMessage.DEPENDENCY_UNAVAILABLE, response.getBody().get("message"));
        assertFalse(response.getBody().toString().contains("private database details"));
    }

    @Test
    @DisplayName("非法关联 ID 不回显，错误体与响应头使用同一服务端标识")
    void testUnsafeCorrelationIdIsReplaced() {
        for (String unsafeId : Arrays.asList("bad id with spaces", "bad\r\nheader",
                String.join("", Collections.nCopies(65, "x")))) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Correlation-Id", unsafeId);
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

            ResponseEntity<Map<String, Object>> response = handler.handleDomain(
                    new SimpleFeedbackServerException("UNKNOWN_999", "private internal details"));

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertNotNull(response.getBody());
            String responseId = response.getHeaders().getFirst("X-Correlation-Id");
            assertEquals(responseId, response.getBody().get("correlationId"));
            assertEquals(responseId, FeedbackRestExceptionHandler.currentCorrelationId());
            assertNotEquals(unsafeId, responseId);
            assertDoesNotThrow(() -> UUID.fromString(responseId));
        }
    }

    @Test
    @DisplayName("非法页码与溢出偏移在查库前统一拒绝为 400")
    void testInvalidPageFailsBeforeDatabaseAccess() {
        FeedbackSubmissionRepository repository = mock(FeedbackSubmissionRepository.class);
        SubmissionQueryService service = new SubmissionQueryService(repository,
                mock(FeedbackAttachmentRepository.class));
        FeedbackSubmissionRepository.Filter filter = new FeedbackSubmissionRepository.Filter(
                null, null, null, null, null, null);
        for (int page : new int[]{0, -1, Integer.MAX_VALUE}) {
            SimpleFeedbackServerException mine = assertThrows(SimpleFeedbackServerException.class,
                    () -> service.mineList("iam:subject-1", null, page, 20));
            SimpleFeedbackServerException admin = assertThrows(SimpleFeedbackServerException.class,
                    () -> service.adminList(filter, FeedbackApplicationAccessScope.all(), page, 20));
            assertEquals(ErrorCode.INVALID_REQUEST, mine.getErrorCode());
            assertEquals(ErrorCode.INVALID_REQUEST, admin.getErrorCode());
            assertEquals(HttpStatus.BAD_REQUEST, handler.handleDomain(mine).getStatusCode());
        }
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("空白或超长幂等键在提交前返回 400")
    void testInvalidIdempotencyKeyFailsBeforeCreate() {
        FeedbackPrincipalResolver resolver = mock(FeedbackPrincipalResolver.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(resolver.resolve(request)).thenReturn(new FeedbackRequestContext("subject-1", "iam:subject-1"));
        SubmissionCreateService createService = mock(SubmissionCreateService.class);
        SubmissionController controller = new SubmissionController(createService,
                mock(SubmissionQueryService.class), mock(SubmissionHandlingService.class),
                mock(HintService.class), mock(AttachmentStorageService.class), resolver,
                mock(ApplicationRegistryService.class), new SimpleFeedbackServerProperties(),
                mock(ObjectProvider.class));
        for (String key : Arrays.asList("", "  ", String.join("", Collections.nCopies(65, "x")))) {
            SubmissionValidationException invalid = assertThrows(SubmissionValidationException.class,
                    () -> controller.create(new SubmissionCreateRequest(), null, key, request));
            assertEquals(HttpStatus.BAD_REQUEST, handler.handleDomain(invalid).getStatusCode());
        }
        verifyNoInteractions(createService);
    }

    @Test
    @DisplayName("负数行版本在处理与注册表更新前返回 400")
    void testNegativeExpectedRowVersionFailsAsBadRequest() {
        FeedbackPrincipalResolver resolver = mock(FeedbackPrincipalResolver.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(resolver.resolve(request)).thenReturn(new FeedbackRequestContext("subject-1", "iam:subject-1"));

        DataGrant submissionGrant = new DataGrant(FeedbackPermissionConstant.DATA_RESOURCE_SUBMISSION,
                Collections.singletonList(FeedbackPermissionConstant.DATA_ACTION_MANAGE), true,
                Collections.emptyList());
        DataGrant applicationGrant = new DataGrant(FeedbackPermissionConstant.DATA_RESOURCE_APPLICATION,
                Collections.singletonList(FeedbackPermissionConstant.DATA_ACTION_MANAGE), true,
                Collections.emptyList());
        DataGrantDocument document = new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL,
                SimpleDataPermissionConstant.VERSION, Arrays.asList(submissionGrant, applicationGrant));

        SubmissionHandlingRequest handling = new SubmissionHandlingRequest();
        handling.setTargetStatus("resolved");
        handling.setExpectedRowVersion(-1);
        SubmissionHandlingService handlingService = mock(SubmissionHandlingService.class);
        SubmissionController submissionController = new SubmissionController(mock(SubmissionCreateService.class),
                mock(SubmissionQueryService.class), handlingService, mock(HintService.class),
                mock(AttachmentStorageService.class), resolver, mock(ApplicationRegistryService.class),
                new SimpleFeedbackServerProperties(), mock(ObjectProvider.class));
        SubmissionValidationException handlingError = assertThrows(SubmissionValidationException.class,
                () -> submissionController.handle("submission-1", handling,
                        DataAccessPlan.evaluate(document, new DataPermissionRequest(
                                FeedbackPermissionConstant.DATA_RESOURCE_SUBMISSION,
                                FeedbackPermissionConstant.DATA_ACTION_MANAGE)), request));
        assertEquals(ErrorCode.INVALID_REQUEST, handlingError.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, handler.handleDomain(handlingError).getStatusCode());
        verifyNoInteractions(handlingService);

        ApplicationRegistryService registryService = mock(ApplicationRegistryService.class);
        when(registryService.findByCode("demo-app")).thenReturn(FeedbackApplication.builder()
                .applicationCode("demo-app").displayName("示例应用").build());
        ApplicationUpsertRequest update = new ApplicationUpsertRequest();
        update.setExpectedRowVersion(-1);
        ApplicationController applicationController = new ApplicationController(registryService,
                mock(ApplicationSourceService.class), resolver);
        SubmissionValidationException updateError = assertThrows(SubmissionValidationException.class,
                () -> applicationController.update("demo-app", update,
                        DataAccessPlan.evaluate(document, new DataPermissionRequest(
                                FeedbackPermissionConstant.DATA_RESOURCE_APPLICATION,
                                FeedbackPermissionConstant.DATA_ACTION_MANAGE)), request));
        assertEquals(ErrorCode.INVALID_REQUEST, updateError.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, handler.handleDomain(updateError).getStatusCode());
    }

    @Test
    @DisplayName("Spring 无法解析的 JSON 返回统一 400，缺失关联 ID 时服务端生成并回显")
    void testMalformedJsonReturnsSafeBadRequest() throws Exception {
        ApplicationController controller = new ApplicationController(mock(ApplicationRegistryService.class),
                mock(ApplicationSourceService.class), mock(FeedbackPrincipalResolver.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(handler, new FeedbackRequestExceptionHandler())
                .addPlaceholderValue(SimpleFeedbackServerConstant.CONFIG_PREFIX + ".api-base-path", "/api/feedback")
                .build();

        MvcResult result = mvc.perform(post("/api/feedback/applications")
                        .contentType("application/json")
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andReturn();

        JsonNode body = mapper.readTree(result.getResponse().getContentAsByteArray());
        assertEquals(ErrorMessage.INVALID_REQUEST, body.path("message").asText());
        assertEquals(3, body.size());
        assertDoesNotThrow(() -> Instant.parse(body.path("timestamp").asText()));
        String correlationId = body.path("correlationId").asText();
        assertEquals(correlationId, result.getResponse().getHeader("X-Correlation-Id"));
        assertDoesNotThrow(() -> UUID.fromString(correlationId));
    }

    @Test
    @DisplayName("反馈端点媒体类型不支持时返回三字段 415，宿主自定义 Advice 保持原响应")
    void testUnsupportedMediaTypeKeepsHostBoundary() throws Exception {
        FeedbackPreControllerExceptionResolver resolver =
                new FeedbackPreControllerExceptionResolver(new SimpleFeedbackServerProperties());
        HttpMediaTypeNotSupportedException exception =
                new HttpMediaTypeNotSupportedException(MediaType.APPLICATION_JSON,
                        Collections.<MediaType>emptyList());
        MockHttpServletRequest feedback = new MockHttpServletRequest("POST", "/api/feedback/submissions");
        MockHttpServletResponse feedbackResponse = new MockHttpServletResponse();
        assertNotNull(resolver.resolveException(feedback, feedbackResponse, null, exception));
        assertEquals(415, feedbackResponse.getStatus());
        JsonNode body = mapper.readTree(feedbackResponse.getContentAsByteArray());
        assertEquals(3, body.size());
        assertEquals(ErrorMessage.UNSUPPORTED_MEDIA_TYPE, body.path("message").asText());
        assertEquals(body.path("correlationId").asText(),
                feedbackResponse.getHeader("X-Correlation-Id"));

        MockHttpServletRequest hostRequest = new MockHttpServletRequest("POST", "/host-upload");
        assertNull(resolver.resolveException(hostRequest, new MockHttpServletResponse(), null, exception));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new HostController())
                .setControllerAdvice(handler, new FeedbackRequestExceptionHandler(), new HostAdvice())
                .build();
        MvcResult host = mvc.perform(post("/host-upload")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andReturn();
        assertEquals("host-custom-415", mapper.readTree(host.getResponse().getContentAsByteArray())
                .path("hostMessage").asText());
        assertNull(host.getResponse().getHeader("X-Correlation-Id"));
    }

    @Test
    @DisplayName("控制器内未预期运行时异常返回脱敏 500")
    void testUnexpectedRuntimeReturnsSafeInternalError() throws Exception {
        HintService hintService = mock(HintService.class);
        FeedbackPrincipalResolver principalResolver = mock(FeedbackPrincipalResolver.class);
        when(principalResolver.resolve(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new FeedbackRequestContext("subject-1", "iam:subject-1"));
        when(hintService.hint("subject-1")).thenThrow(new RuntimeException("private internal details"));
        SubmissionController controller = submissionController(hintService, principalResolver);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(handler, new FeedbackRequestExceptionHandler())
                .addPlaceholderValue(SimpleFeedbackServerConstant.CONFIG_PREFIX + ".api-base-path", "/api/feedback")
                .build();

        MvcResult result = mvc.perform(get("/api/feedback/submission-hints"))
                .andExpect(status().isInternalServerError())
                .andReturn();
        JsonNode body = mapper.readTree(result.getResponse().getContentAsByteArray());
        assertEquals(ErrorMessage.INTERNAL_ERROR, body.path("message").asText());
        assertEquals(3, body.size());
        assertFalse(body.toString().contains("private internal details"));
        assertEquals(body.path("correlationId").asText(), result.getResponse().getHeader("X-Correlation-Id"));
    }

    @Test
    @DisplayName("未预期故障脱敏为 500，领域冲突和依赖故障仍按 409 与 503 映射")
    void testUnexpectedErrorDoesNotOverrideDomainStatus() throws Exception {
        HintService hintService = mock(HintService.class);
        FeedbackPrincipalResolver resolver = mock(FeedbackPrincipalResolver.class);
        when(resolver.resolve(any())).thenReturn(new FeedbackRequestContext("subject-1", "iam:subject-1"));
        SubmissionController controller = submissionController(hintService, resolver);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new FeedbackRequestExceptionHandler(), handler)
                .addPlaceholderValue(SimpleFeedbackServerConstant.CONFIG_PREFIX + ".api-base-path", "/api/feedback")
                .build();

        when(hintService.hint("subject-1")).thenThrow(new NullPointerException("private internal details"));
        MvcResult unexpected = mvc.perform(get("/api/feedback/submission-hints"))
                .andExpect(status().isInternalServerError())
                .andReturn();
        JsonNode unexpectedBody = mapper.readTree(unexpected.getResponse().getContentAsByteArray());
        assertEquals(3, unexpectedBody.size());
        assertEquals(ErrorMessage.INTERNAL_ERROR, unexpectedBody.path("message").asText());
        assertFalse(unexpectedBody.toString().contains("private internal details"));
        assertEquals(unexpectedBody.path("correlationId").asText(),
                unexpected.getResponse().getHeader("X-Correlation-Id"));

        doThrow(new FeedbackStateException(ErrorCode.ROW_VERSION_CONFLICT, ErrorMessage.ROW_VERSION_CONFLICT))
                .when(hintService).hint("subject-1");
        MvcResult conflict = mvc.perform(get("/api/feedback/submission-hints"))
                .andExpect(status().isConflict())
                .andReturn();
        assertEquals(ErrorMessage.ROW_VERSION_CONFLICT,
                mapper.readTree(conflict.getResponse().getContentAsByteArray()).path("message").asText());

        doThrow(new SimpleFeedbackServerException(ErrorCode.DEPENDENCY_UNAVAILABLE,
                ErrorMessage.DEPENDENCY_UNAVAILABLE)).when(hintService).hint("subject-1");
        MvcResult unavailable = mvc.perform(get("/api/feedback/submission-hints"))
                .andExpect(status().isServiceUnavailable())
                .andReturn();
        assertEquals(ErrorMessage.DEPENDENCY_UNAVAILABLE,
                mapper.readTree(unavailable.getResponse().getContentAsByteArray()).path("message").asText());
    }

    @Test
    @DisplayName("身份解析为空时返回安全三字段 401，不再误报依赖故障 503")
    void testMissingFeedbackIdentityReturnsUnauthorized() throws Exception {
        FeedbackPrincipalResolver resolver = mock(FeedbackPrincipalResolver.class);
        SubmissionController controller = submissionController(mock(HintService.class), resolver);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(handler, new FeedbackRequestExceptionHandler())
                .addPlaceholderValue(SimpleFeedbackServerConstant.CONFIG_PREFIX + ".api-base-path", "/api/feedback")
                .build();

        MvcResult result = mvc.perform(get("/api/feedback/submission-hints"))
                .andExpect(status().isUnauthorized())
                .andReturn();
        JsonNode body = mapper.readTree(result.getResponse().getContentAsByteArray());
        assertEquals(ErrorMessage.AUTHENTICATION_REQUIRED, body.path("message").asText());
        assertEquals(3, body.size());
        assertEquals(body.path("correlationId").asText(), result.getResponse().getHeader("X-Correlation-Id"));

        ApplicationController applicationController = new ApplicationController(mock(ApplicationRegistryService.class),
                mock(ApplicationSourceService.class), resolver);
        assertThrows(InsufficientAuthenticationException.class,
                () -> applicationController.list(null, new MockHttpServletRequest()));
    }

    @Test
    @DisplayName("已认证机器主体进入反馈端点并归一为 aksk owner")
    void testServicePrincipalIsAccepted() {
        String subjectId = "service-a";
        ApplicationAuthorizationContext authorization = new ApplicationAuthorizationContext(
                SimpleApplicationAuthorizationConstant.PROTOCOL,
                SimpleApplicationAuthorizationConstant.VERSION,
                ApplicationAuthorizationSubjectType.SERVICE, subjectId, "application-a", true,
                Collections.<String>emptyList(), Collections.<String>emptyList(),
                Collections.singletonList("api.read"), null, 1L, "manifest-1", "digest-a",
                Instant.ofEpochSecond(100L), Instant.ofEpochSecond(200L));
        VerifiedResourcePrincipal principal = new VerifiedResourcePrincipal(
                new ResourceAuthenticationSourceId("provider-a"), ResourceSubjectType.SERVICE, subjectId);
        VerifiedResourceContext verified = new VerifiedResourceContext(principal, authorization, "request-a");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                verified, null, Collections.emptyList()));
        FeedbackRequestContext context = new FeedbackResourceServerPrincipalResolver()
                .resolve(new MockHttpServletRequest());
        assertEquals(subjectId, context.getSubjectId());
        assertEquals("aksk:" + subjectId, context.getOwner());
    }

    @Test
    @DisplayName("创建 Location 保留自定义 API 路径与 servlet context path")
    void testCreateLocationPointsToReachableResource() {
        FeedbackPrincipalResolver resolver = mock(FeedbackPrincipalResolver.class);
        FeedbackRequestContext context = new FeedbackRequestContext("subject-1", "iam:subject-1");
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(resolver.resolve(request)).thenReturn(context);
        SubmissionCreateService createService = mock(SubmissionCreateService.class);
        when(createService.create(any(FeedbackSubmission.class), anyList())).thenAnswer(invocation ->
                new SubmissionCreateService.CreateResult(invocation.getArgument(0), false));
        SubmissionQueryService queryService = mock(SubmissionQueryService.class);
        when(queryService.attachments(any(String.class))).thenReturn(Collections.singletonList(
                FeedbackAttachment.builder()
                        .id("attachment-1")
                        .submissionId("submission-1")
                        .originalFilename("example.txt")
                        .contentType("text/plain")
                        .sizeBytes(4)
                        .build()));
        SimpleFeedbackServerProperties properties = new SimpleFeedbackServerProperties();
        properties.setApiBasePath("/feedback-v2");
        SubmissionController submissionController = new SubmissionController(createService,
                queryService, mock(SubmissionHandlingService.class), mock(HintService.class),
                mock(AttachmentStorageService.class), resolver, mock(ApplicationRegistryService.class),
                properties, mock(ObjectProvider.class));
        SubmissionCreateRequest submission = new SubmissionCreateRequest();
        submission.setApplicationCode("demo-app");
        submission.setKind("proactive");
        submission.setType("bug");
        submission.setScore(7);
        submission.setContent("示例反馈");
        request.setContextPath("/tenant");
        request.setRequestURI("/tenant/feedback-v2/submissions");

        ResponseEntity<SubmissionResponse> createdSubmission = submissionController.create(submission, null,
                "idem-a", request);
        assertNotNull(createdSubmission.getBody());
        assertEquals("/tenant/feedback-v2/me/submissions/" + createdSubmission.getBody().getId(),
                createdSubmission.getHeaders().getLocation().getPath());
        assertEquals("attachment-1", createdSubmission.getBody().getAttachments().get(0).getId());

        ApplicationSourceService sourceService = mock(ApplicationSourceService.class);
        ApplicationController applicationController = new ApplicationController(mock(ApplicationRegistryService.class),
                sourceService, resolver);
        ApplicationUpsertRequest application = new ApplicationUpsertRequest();
        application.setApplicationCode("demo-app");
        application.setDisplayName("示例应用");
        DataGrant grant = new DataGrant(FeedbackPermissionConstant.DATA_RESOURCE_APPLICATION,
                Collections.singletonList(FeedbackPermissionConstant.DATA_ACTION_MANAGE), true,
                Collections.emptyList());
        DataGrantDocument document = new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL,
                SimpleDataPermissionConstant.VERSION, Collections.singletonList(grant));
        DataAccessPlan plan = DataAccessPlan.evaluate(document,
                new DataPermissionRequest(FeedbackPermissionConstant.DATA_RESOURCE_APPLICATION,
                        FeedbackPermissionConstant.DATA_ACTION_MANAGE));
        request.setRequestURI("/tenant/feedback-v2/applications");

        ResponseEntity<?> createdApplication = applicationController.create(application, plan, request);
        assertEquals("/tenant/feedback-v2/applications/demo-app",
                createdApplication.getHeaders().getLocation().getPath());
    }

    @Test
    @DisplayName("前置 multipart 解析器仅接管反馈路径")
    void testMaxUploadDoesNotTakeOverHost() {
        FeedbackPreControllerExceptionResolver resolver =
                new FeedbackPreControllerExceptionResolver(new SimpleFeedbackServerProperties());
        MaxUploadSizeExceededException exception = new MaxUploadSizeExceededException(1L);
        MockHttpServletRequest host = new MockHttpServletRequest();
        host.setRequestURI("/host-upload");
        assertNull(resolver.resolveException(host, new MockHttpServletResponse(), null, exception));

        MockHttpServletRequest feedback = new MockHttpServletRequest();
        feedback.setRequestURI("/api/feedback/submissions");
        MockHttpServletResponse feedbackResponse = new MockHttpServletResponse();
        assertNotNull(resolver.resolveException(feedback, feedbackResponse, null, exception));
        assertEquals(413, feedbackResponse.getStatus());
        assertEquals(feedbackResponse.getHeader("X-Correlation-Id"),
                assertDoesNotThrow(() -> mapper.readTree(feedbackResponse.getContentAsByteArray()))
                        .path("correlationId").asText());
    }

    @SuppressWarnings("unchecked")
    private static SubmissionController submissionController(HintService hintService,
                                                             FeedbackPrincipalResolver principalResolver) {
        return new SubmissionController(mock(SubmissionCreateService.class), mock(SubmissionQueryService.class),
                mock(SubmissionHandlingService.class), hintService, mock(AttachmentStorageService.class),
                principalResolver, mock(ApplicationRegistryService.class), new SimpleFeedbackServerProperties(),
                mock(ObjectProvider.class));
    }

    @RestController
    public static class HostController {

        @PostMapping(value = "/host-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public void upload() {
        }
    }

    @RestControllerAdvice
    public static class HostAdvice {

        @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
        public ResponseEntity<Map<String, String>> unsupportedMediaType(HttpMediaTypeNotSupportedException exception) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .body(Collections.singletonMap("hostMessage", "host-custom-415"));
        }
    }
}
