package io.github.surezzzzzz.sdk.crm.server.test.cases;

import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.crm.server.controller.SimpleCrmServerArchiveController;
import io.github.surezzzzzz.sdk.crm.server.controller.SimpleCrmServerExceptionHandler;
import io.github.surezzzzzz.sdk.crm.server.controller.SimpleCrmServerFulfillmentController;
import io.github.surezzzzzz.sdk.crm.server.controller.SimpleCrmServerOpportunityController;
import io.github.surezzzzzz.sdk.crm.server.controller.SimpleCrmServerQuotationController;
import io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentConsumer;
import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmOwner;
import io.github.surezzzzzz.sdk.crm.server.core.model.CrmPage;
import io.github.surezzzzzz.sdk.crm.server.core.model.CrmPageRequest;
import io.github.surezzzzzz.sdk.crm.server.service.SimpleCrmServerCommandService;
import io.github.surezzzzzz.sdk.crm.server.service.SimpleCrmServerOwnerResolverBridge;
import io.github.surezzzzzz.sdk.crm.server.service.SimpleCrmServerQueryService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP 分页参数绑定和响应契约；数据库筛选及授权总数由真实 MySQL 测试验证。
 * 此处只替换业务服务，不装配身份权限链，不将 MVC 绑定测试当作三权验收。
 *
 * @author surezzzzzz
 */
@Slf4j
class SimpleCrmServerPaginationHttpContractTest {

    private static final String CONSUMERS_PATH = "/api/crm/fulfillment-consumers";
    private static final List<String> CURSOR_PATHS = Arrays.asList(
            "/api/crm/customers", "/api/crm/contacts", "/api/crm/offerings",
            "/api/crm/quotations", "/api/crm/orders", "/api/crm/fulfillment-items",
            "/api/crm/operation-logs");

    private SimpleCrmServerCommandService commands;
    private SimpleCrmServerQueryService queries;
    private SimpleCrmServerOwnerResolverBridge ownerResolver;
    private MockMvc mvc;
    private CrmPageRequest observedPage;
    private int observedLimit;

    @BeforeEach
    void setUp() {
        commands = mock(SimpleCrmServerCommandService.class);
        queries = mock(SimpleCrmServerQueryService.class, invocation -> {
            if (invocation.getMethod().getReturnType() == CrmPage.class) {
                Object[] arguments = invocation.getArguments();
                observedPage = (CrmPageRequest) arguments[arguments.length - 1];
                observedLimit = (Integer) arguments[arguments.length - 2];
                return observedPage == null
                        ? new CrmPage<Object>(Collections.emptyList(), "next-page")
                        : new CrmPage<Object>(Collections.emptyList(), observedPage, 5);
            }
            return RETURNS_DEFAULTS.answer(invocation);
        });
        ownerResolver = mock(SimpleCrmServerOwnerResolverBridge.class);
        when(ownerResolver.resolve()).thenReturn(CrmOwner.human("http-test-subject"));
        List<FulfillmentConsumer> consumers = Arrays.asList(
                new FulfillmentConsumer("consumer-a", "test-capability", 1, true),
                new FulfillmentConsumer("consumer-b", "test-capability", 1, false),
                new FulfillmentConsumer("consumer-c", "test-capability", 1, true));
        when(commands.listFulfillmentConsumers()).thenReturn(consumers);
        when(commands.listFulfillmentConsumers(any(CrmPageRequest.class))).thenAnswer(invocation -> {
            observedPage = invocation.getArgument(0);
            return new CrmPage<FulfillmentConsumer>(Collections.emptyList(), observedPage, 3);
        });
        mvc = MockMvcBuilders.standaloneSetup(
                        new SimpleCrmServerArchiveController(commands, queries, ownerResolver),
                        new SimpleCrmServerOpportunityController(commands, queries, ownerResolver),
                        new SimpleCrmServerQuotationController(commands, queries, ownerResolver),
                        new SimpleCrmServerFulfillmentController(commands, queries, ownerResolver))
                .setControllerAdvice(new SimpleCrmServerExceptionHandler())
                .setCustomArgumentResolvers(new NullDataPlanResolver())
                .build();
    }

    @Test
    void allNumberedEndpointsBindPagesSizesAndDefaults() throws Exception {
        for (String path : allPaths()) {
            log.info("HTTP 页码元数据与默认值：path={}", path);
            long total = CONSUMERS_PATH.equals(path) ? 3 : 5;
            mvc.perform(request(path).param("page", "2").param("pageSize", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page").value(2))
                    .andExpect(jsonPath("$.pageSize").value(2))
                    .andExpect(jsonPath("$.total").value(total))
                    .andExpect(jsonPath("$.nextCursor").doesNotExist());
            assertEquals(2, observedPage.getPage());
            assertEquals(2, observedPage.getPageSize());
            mvc.perform(request(path).param("page", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pageSize").value(20));
            assertEquals(2, observedPage.getPage());
            assertEquals(20, observedPage.getPageSize());
            mvc.perform(request(path).param("pageSize", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page").value(1));
            assertEquals(1, observedPage.getPage());
        }
    }

    /** 联系人的客户归属与职务必须可以创建后回读，不把客户 ID 伪装为主体 owner。 */
    @Test
    void contactCreateAndReadRetainCustomerAndTitle() throws Exception {
        java.time.Instant now = java.time.Instant.now();
        io.github.surezzzzzz.sdk.crm.server.core.domain.contact.Contact contact =
                new io.github.surezzzzzz.sdk.crm.server.core.domain.contact.Contact(
                        "contact-http", "customer-http", "联系人", "采购负责人",
                        io.github.surezzzzzz.sdk.crm.server.core.domain.contact.ContactState.ACTIVE,
                        1, now, now);
        when(commands.createContact(any(), any(), any())).thenReturn(contact);
        when(queries.findContact(any(), any(), any())).thenReturn(contact);
        mvc.perform(post("/api/crm/contacts").header("Idempotency-Key", "contact-roundtrip")
                        .contentType("application/json")
                        .content("{\"customerId\":\"customer-http\",\"displayName\":\"联系人\",\"title\":\"采购负责人\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value("customer-http"))
                .andExpect(jsonPath("$.title").value("采购负责人"))
                .andExpect(jsonPath("$.owner").doesNotExist());
        mvc.perform(get("/api/crm/contacts/contact-http"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value("customer-http"))
                .andExpect(jsonPath("$.title").value("采购负责人"));
    }

    /** 缺少通用交付对象与范围的报价不得被服务器默认值补成另一份业务请求。 */
    @Test
    void quotationRejectsMissingDeliveryFieldsBeforeCommand() throws Exception {
        String line = "{\"offeringId\":\"offering-http\",\"quantity\":\"1\",\"unit\":\"SET\","
                + "\"unitPrice\":{\"amount\":\"10\",\"currency\":\"CNY\"}";
        for (String extra : Arrays.asList("", ",\"subjectReference\":\"server-sn\"",
                ",\"fulfillmentScope\":\"production\"",
                ",\"subjectReference\":\" \",\"fulfillmentScope\":\"production\"")) {
            mvc.perform(post("/api/crm/quotations").header("Idempotency-Key", "missing-obligation")
                            .contentType("application/json")
                            .content("{\"customerId\":\"customer-http\",\"validUntil\":\"2027-01-01T00:00:00Z\",\"lines\":["
                                    + line + extra + "}]}"))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(commands);
    }

    /** 旧履约序号是业务冲突，不能错误地暴露为服务端故障。 */
    @Test
    void staleFulfillmentSequenceReturnsConflict() throws Exception {
        when(commands.reportFulfillmentResult(any(), any(), any())).thenThrow(
                new io.github.surezzzzzz.sdk.crm.server.core.error.CrmException(
                        io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode.FULFILLMENT_RESULT_CONSISTENCY_FAILURE,
                        "sequence"));
        mvc.perform(patch("/api/crm/fulfillment-items/item-http").param("sequence", "1")
                        .header("Idempotency-Key", "stale-sequence").contentType("application/json")
                        .content("{\"state\":\"in_progress\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("请求与当前资源状态冲突"));
    }

    @Test
    void patchRejectsConflictingDispatchParameters() throws Exception {
        log.info("PATCH 分派参数互斥：state/owner 与 stage/owner 混用返回 400");
        mvc.perform(patch("/api/crm/customers/customer-http")
                        .param("state", "inactive").param("owner", "iam:target")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/crm/quotations/quotation-http")
                        .param("state", "issued").param("owner", "iam:target")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/crm/opportunities/opportunity-http")
                        .param("stage", "qualified").param("owner", "iam:target")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(commands, ownerResolver);
    }

    @Test
    void allNumberedEndpointsRejectInvalidAndMixedParametersBeforeQuery() throws Exception {
        for (String path : allPaths()) {
            log.info("HTTP 非法页码及分页模式混用拒绝：path={}", path);
            String[][] invalid = {{"page", "0"}, {"page", "-1"}, {"page", "text"},
                    {"pageSize", "0"}, {"pageSize", "101"}, {"pageSize", "text"},
                    {"page", ""}, {"pageSize", ""}};
            for (String[] parameter : invalid) {
                mvc.perform(request(path).param(parameter[0], parameter[1]))
                        .andExpect(status().isBadRequest());
            }
            for (String numbered : Arrays.asList("page", "pageSize")) {
                for (String cursor : Arrays.asList("cursor", "limit")) {
                    mvc.perform(request(path).param(numbered, "1").param(cursor, "1"))
                            .andExpect(status().isBadRequest());
                }
            }
            mvc.perform(request(path).param("page", "1").param("limit", ""))
                    .andExpect(status().isBadRequest());
            mvc.perform(request(path).param("page", "").param("limit", "20"))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(commands, queries);
    }

    @Test
    void existingCursorEndpointsKeepTheirResponseAndValidateLimit() throws Exception {
        for (String path : CURSOR_PATHS) {
            log.info("HTTP 游标契约保留：path={}", path);
            mvc.perform(request(path))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nextCursor").value("next-page"))
                    .andExpect(jsonPath("$.total").doesNotExist());
            assertNull(observedPage);
            assertEquals(20, observedLimit);
            mvc.perform(request(path).param("cursor", "current-page").param("limit", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nextCursor").value("next-page"));
            assertNull(observedPage);
            assertEquals(2, observedLimit);
            for (String limit : Arrays.asList("0", "101", "text", "")) {
                mvc.perform(request(path).param("limit", limit))
                        .andExpect(status().isBadRequest());
            }
            mvc.perform(request(path).param("cursor", ""))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void consumersWithoutPaginationRetainCompleteItems() throws Exception {
        mvc.perform(request(CONSUMERS_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].consumerId").value("consumer-a"))
                .andExpect(jsonPath("$.items[1].enabled").value(false))
                .andExpect(jsonPath("$.items[2].consumerId").value("consumer-c"))
                .andExpect(jsonPath("$.page").doesNotExist())
                .andExpect(jsonPath("$.total").doesNotExist())
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
        verify(commands).listFulfillmentConsumers();
        verify(commands, never()).listFulfillmentConsumers(any(CrmPageRequest.class));
    }

    @Test
    void consumersRejectAnyCursorParameterIncludingEmptyOrMalformedValues() throws Exception {
        for (String parameter : Arrays.asList("cursor", "limit")) {
            for (String value : Arrays.asList("", "1", "text")) {
                mvc.perform(request(CONSUMERS_PATH).param(parameter, value))
                        .andExpect(status().isBadRequest());
                mvc.perform(request(CONSUMERS_PATH).param("page", "1").param(parameter, value))
                        .andExpect(status().isBadRequest());
            }
        }
        verifyNoInteractions(commands, queries);
    }

    private List<String> allPaths() {
        List<String> paths = new ArrayList<String>(CURSOR_PATHS);
        paths.add(CONSUMERS_PATH);
        return paths;
    }

    private MockHttpServletRequestBuilder request(String path) {
        MockHttpServletRequestBuilder request = get(path);
        if ("/api/crm/contacts".equals(path)) {
            request.param("customerId", "test-customer");
        }
        return request;
    }

    /** MVC 绑定测试中不计算授权计划，身份范围另由真实组合验收。 */
    private static final class NullDataPlanResolver implements HandlerMethodArgumentResolver {

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.getParameterType() == DataAccessPlan.class;
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                                      NativeWebRequest request, WebDataBinderFactory binderFactory) {
            return null;
        }
    }
}
