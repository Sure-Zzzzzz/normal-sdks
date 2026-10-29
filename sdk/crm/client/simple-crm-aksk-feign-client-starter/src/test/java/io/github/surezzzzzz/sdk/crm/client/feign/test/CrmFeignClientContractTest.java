package io.github.surezzzzzz.sdk.crm.client.feign.test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.codec.DecodeException;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.TokenManager;
import io.github.surezzzzzz.sdk.crm.client.CrmClientFieldSet;
import io.github.surezzzzzz.sdk.crm.client.CrmPatchRouting;
import io.github.surezzzzzz.sdk.crm.client.codec.CrmWireCodec;
import io.github.surezzzzzz.sdk.crm.client.exception.CrmClientProtocolException;
import io.github.surezzzzzz.sdk.crm.client.feign.CrmFeignClient;
import io.github.surezzzzzz.sdk.crm.client.feign.model.CrmResourceResponse;
import io.github.surezzzzzz.sdk.crm.client.feign.model.CrmPageResponse;
import io.github.surezzzzzz.sdk.crm.client.feign.model.CrmCompleteListResponse;
import io.github.surezzzzzz.sdk.crm.client.validation.CrmResponseValidator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRM Feign 契约测试：JDK HttpServer 桩出服务端，经 @EnableFeignClients 真实 Feign 链路
 * （含底座拦截器装配）断言路径/方法/幂等键 header 与 wire 解析。令牌链为固定桩，不连真实凭据源。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = CrmFeignClientContractTest.ContractTestApplication.class,
        properties = "io.github.surezzzzzz.sdk.auth.aksk.client.enable=true",
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CrmFeignClientContractTest {

    private static HttpServer server;
    private static final AtomicReference<String> lastMethod = new AtomicReference<String>();
    private static final AtomicReference<String> lastPath = new AtomicReference<String>();
    private static final AtomicReference<String> lastIdempotencyKey =
            new AtomicReference<String>();

    @Autowired
    private CrmFeignClient client;

    @DynamicPropertySource
    static void baseUrl(DynamicPropertyRegistry registry) {
        registry.add("io.github.surezzzzzz.sdk.crm.client.base-url",
                () -> "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @BeforeAll
    static void startStub() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", CrmFeignClientContractTest::handle);
        server.start();
    }

    @AfterAll
    static void stopStub() {
        server.stop(0);
    }

    private static void handle(HttpExchange exchange) {
        lastMethod.set(exchange.getRequestMethod());
        lastPath.set(exchange.getRequestURI().getPath());
        lastIdempotencyKey.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
        String path = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getRawQuery();
        Map<String, Object> body = archiveRow();
        if (path.equals("/api/crm/customers") && "GET".equals(exchange.getRequestMethod())) {
            body = new LinkedHashMap<String, Object>();
            body.put("items", Collections.singletonList(archiveRow()));
            body.put("nextCursor", "opaque-cursor");
            if (query != null && query.contains("numbered-response")) {
                body.remove("nextCursor");
                body.put("page", 1);
                body.put("pageSize", 20);
                body.put("total", 45);
            } else if (query != null && query.contains("missing-cursor")) {
                body.remove("nextCursor");
            } else if (query != null && query.contains("missing-items")) {
                body.remove("items");
            } else if (query != null && query.contains("unknown-envelope")) {
                body.put("extraField", "unsupported");
            } else if (query != null && query.contains("numeric-cursor")) {
                body.put("nextCursor", 42);
            } else if (query != null && query.contains("blank-cursor")) {
                body.put("nextCursor", " ");
            } else if (query != null && query.contains("object-cursor")) {
                body.put("nextCursor", Collections.singletonMap("value", "unsupported"));
            }
        } else if (path.startsWith("/api/crm/contacts")) {
            body = contactRow();
            if (path.endsWith("/null-title")) {
                body.put("title", null);
            } else if (path.endsWith("/unknown-contact-field")) {
                body.put("owner", "customer-target");
            } else if (path.endsWith("/numeric-title")) {
                body.put("title", 1);
            }
            if (path.equals("/api/crm/contacts") && "GET".equals(exchange.getRequestMethod())) {
                Map<String, Object> page = new LinkedHashMap<String, Object>();
                page.put("items", Collections.singletonList(body));
                page.put("nextCursor", null);
                body = page;
            }
        } else if (path.contains("/quotations/") && path.contains("/versions/")) {
            body = quotationVersionRow();
        } else if (path.equals("/api/crm/fulfillment-consumers")) {
            body = new LinkedHashMap<String, Object>();
            body.put("items", Collections.emptyList());
        } else if (path.startsWith("/api/crm/offerings")) {
            body.put("id", "offering-1");
            body.put("state", "published");
            body.put("offeringReference", "reference-1");
            body.put("capabilityType", "fixed_price_fulfillment_v1");
            body.put("requiredConsumerCapability", "delivery-capability");
        } else if (path.startsWith("/api/crm/customers/") && query != null && query.contains("state=merged")) {
            body = new LinkedHashMap<String, Object>();
            Map<String, Object> source = archiveRow();
            source.put("state", "merged");
            Map<String, Object> target = archiveRow();
            target.put("id", "customer-target");
            body.put("source", source);
            body.put("target", target);
            body.put("relocatedContacts", Collections.singletonList(contactRow()));
        } else if (path.startsWith("/api/crm/customers/") && query != null && query.startsWith("owner=")) {
            body = new LinkedHashMap<String, Object>();
            Map<String, Object> customer = archiveRow();
            customer.put("owner", "iam:target-owner");
            body.put("customer", customer);
            Map<String, Object> quotation = new LinkedHashMap<String, Object>();
            quotation.put("quotationId", "quotation-1");
            quotation.put("owner", "iam:target-owner");
            body.put("transferredQuotations", Collections.singletonList(quotation));
        }
        byte[] bytes = CrmWireCodec.write(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        try {
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void createCustomerPostsToCustomersWithIdempotencyKey() {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("displayName", "桩客户A");
        CrmResourceResponse response = client.createCustomer("idem-feign-1", null, body);
        log.info("Feign 创建：method={}，path={}，幂等键={}，id={}", lastMethod.get(),
                lastPath.get(), lastIdempotencyKey.get(), response.get("id"));
        assertEquals("POST", lastMethod.get());
        assertEquals("/api/crm/customers", lastPath.get(), "Feign path 必须拼上 /api/crm 基路径");
        assertEquals("idem-feign-1", lastIdempotencyKey.get(), "Feign 写命令必须带幂等键 header");
        assertEquals("customer-1", response.get("id"));
    }

    @Test
    void getCustomerUsesExactPath() {
        client.getCustomer("customer-9");
        log.info("Feign 点查：method={}，path={}", lastMethod.get(), lastPath.get());
        assertEquals("GET", lastMethod.get());
        assertEquals("/api/crm/customers/customer-9", lastPath.get());
    }

    @Test
    void contactCreateReadAndListKeepCustomerAndNullableTitle() {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("customerId", "customer-target");
        body.put("displayName", "示例联系人");
        body.put("title", "示例职务");
        CrmResourceResponse created = client.createContact("idem-feign-contact-1", null, body);
        log.info("Feign 联系人创建、点查与列表保留客户归属和职务，可空职务保留空值");
        assertEquals("POST", lastMethod.get());
        assertEquals("/api/crm/contacts", lastPath.get());
        assertEquals("idem-feign-contact-1", lastIdempotencyKey.get());
        assertEquals("customer-target", created.get("customerId"));
        assertEquals("示例职务", created.get("title"));
        CrmResourceResponse loaded = client.getContact("contact-1");
        assertEquals("/api/crm/contacts/contact-1", lastPath.get());
        assertEquals("customer-target", loaded.get("customerId"));
        assertEquals("示例职务", loaded.get("title"));
        CrmPageResponse listed = client.listContacts("customer-target", null, 20);
        assertEquals("customer-target", listed.getItems().get(0).get("customerId"));
        assertEquals("示例职务", listed.getItems().get(0).get("title"));
        CrmResourceResponse nullable = client.getContact("null-title");
        assertNull(nullable.get("title"));
        assertEquals("customer-target", CrmResponseValidator.object(nullable.getFields(),
                CrmClientFieldSet.CONTACT_REQUIRED, CrmClientFieldSet.CONTACT).getString("customerId"));
    }

    @Test
    void callerCanStrictlyValidateContactWireFields() {
        CrmResourceResponse unknown = client.getContact("unknown-contact-field");
        CrmResourceResponse invalid = client.getContact("numeric-title");
        log.info("Feign 保留原始资源响应，由调用方显式拒绝联系人未知字段及非法职务类型");
        assertEquals("customer-target", unknown.get("owner"));
        assertEquals(1, invalid.get("title"));
        assertThrows(CrmClientProtocolException.class, () -> CrmResponseValidator.object(
                unknown.getFields(), CrmClientFieldSet.CONTACT_REQUIRED, CrmClientFieldSet.CONTACT));
        assertThrows(CrmClientProtocolException.class, () -> CrmResponseValidator.object(
                invalid.getFields(), CrmClientFieldSet.CONTACT_REQUIRED, CrmClientFieldSet.CONTACT));
    }

    @Test
    @SuppressWarnings("unchecked")
    void quotationVersionKeepsNestedObligationFields() {
        CrmResourceResponse result = client.getQuotationVersion("quotation-1", 1);
        Map<String, Object> line = ((List<Map<String, Object>>) result.get("lines")).get(0);
        Map<String, Object> obligation = (Map<String, Object>) line.get("obligation");
        log.info("Feign 报价版本保留每行的履约对象、范围及消费者能力");
        assertEquals("/api/crm/quotations/quotation-1/versions/1", lastPath.get());
        assertEquals("subject-reference-1", obligation.get("subjectReference"));
        assertEquals("scope-reference-1", obligation.get("fulfillmentScope"));
        assertEquals("delivery-capability", obligation.get("requiredConsumerCapability"));
    }

    @Test
    void reportFulfillmentResultPatchesFulfillmentResource() {
        java.util.Map<String, Object> reportPatch = new java.util.LinkedHashMap<String, Object>();
        reportPatch.put("sequence", 3);
        reportPatch.put("state", "in_progress");
        client.patchFulfillmentItem("fulfillment-1", "idem-report-1", reportPatch,
                io.github.surezzzzzz.sdk.crm.client.CrmPatchRouting.of(reportPatch));
        assertEquals("PATCH", lastMethod.get());
        assertEquals("/api/crm/fulfillment-items/fulfillment-1", lastPath.get());
        assertEquals("idem-report-1", lastIdempotencyKey.get());
    }

    @Test
    void offeringResponseKeepsBusinessReferenceAndCapabilityFields() {
        CrmResourceResponse result = client.getOffering("offering-1");
        log.info("Feign 商品业务字段：reference={}，capability={}",
                result.get("offeringReference"), result.get("capabilityType"));
        assertEquals("reference-1", result.get("offeringReference"));
        assertEquals("delivery-capability", result.get("requiredConsumerCapability"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void customerGovernanceResponsesKeepNestedArchivesAndArrays() {
        Map<String, Object> merge = Collections.<String, Object>singletonMap("state", "merged");
        CrmResourceResponse merged = client.patchCustomer("customer-1", "idem-feign-merge-1", merge,
                CrmPatchRouting.of(merge));
        log.info("Feign 合并结果：source={}，target={}",
                ((Map<String, Object>) merged.get("source")).get("id"),
                ((Map<String, Object>) merged.get("target")).get("id"));
        assertEquals("customer-1", ((Map<String, Object>) merged.get("source")).get("id"));
        assertEquals("merged", ((Map<String, Object>) merged.get("source")).get("state"));
        assertEquals("customer-target", ((Map<String, Object>) merged.get("target")).get("id"));
        List<Map<String, Object>> contacts = (List<Map<String, Object>>) merged.get("relocatedContacts");
        assertEquals("customer-target", contacts.get(0).get("customerId"));
        assertEquals("示例职务", contacts.get(0).get("title"));
        assertEquals("customer-target", ((List<Map<String, Object>>) CrmResponseValidator
                .customerMerge(merged.getFields()).get("relocatedContacts")).get(0).get("customerId"));
        Map<String, Object> transfer = Collections.<String, Object>singletonMap("owner", "iam:target-owner");
        CrmResourceResponse transferred = client.patchCustomer("customer-1", "idem-feign-transfer-1", transfer,
                CrmPatchRouting.of(transfer));
        assertEquals("customer-1", ((Map<String, Object>) transferred.get("customer")).get("id"));
        assertEquals("iam:target-owner", ((Map<String, Object>) transferred.get("customer")).get("owner"));
        assertEquals(1, ((java.util.List<?>) transferred.get("transferredQuotations")).size());
    }

    @Test
    void machinePaginationDecodesCursorAndCompleteConsumerList() {
        CrmPageResponse page = client.listCustomers("active", null, 20);
        log.info("Feign 机器分页：条数={}，游标={}", page.getItems().size(), page.getNextCursor());
        assertEquals(1, page.getItems().size());
        assertEquals("opaque-cursor", page.getNextCursor());
        CrmCompleteListResponse complete = client.listFulfillmentConsumers();
        assertTrue(complete.getItems().isEmpty());
    }

    @Test
    void machinePaginationRejectsNumberedAndUnknownEnvelopesThroughRealDecoder() {
        log.info("Feign 网络解码拒绝页码封装与未知分页字段");
        assertProtocolDecodeFailure("numbered-response");
        assertProtocolDecodeFailure("unknown-envelope");
    }

    @Test
    void machinePaginationRejectsMissingEnvelopeFields() {
        assertProtocolDecodeFailure("missing-cursor");
        assertProtocolDecodeFailure("missing-items");
        assertThrows(Exception.class, () -> new ObjectMapper().readValue("{}", CrmCompleteListResponse.class));
    }

    @Test
    void machinePaginationRejectsNonStringAndBlankCursorThroughRealDecoder() {
        log.info("Feign 网络解码拒绝数字、对象和空白游标，不允许自动类型转换");
        assertProtocolDecodeFailure("numeric-cursor");
        assertProtocolDecodeFailure("blank-cursor");
        assertProtocolDecodeFailure("object-cursor");
    }

    private void assertProtocolDecodeFailure(String state) {
        DecodeException exception = assertThrows(DecodeException.class,
                () -> client.listCustomers(state, null, 20));
        Throwable cause = exception;
        while (cause != null && !(cause instanceof CrmClientProtocolException)) {
            cause = cause.getCause();
        }
        assertTrue(cause instanceof CrmClientProtocolException,
                "解码失败必须源自分页契约校验，不能用网络故障冒充");
    }

    private static Map<String, Object> archiveRow() {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("id", "customer-1");
        row.put("displayName", "示例客户");
        row.put("state", "active");
        row.put("owner", "iam:actor-1");
        return row;
    }

    private static Map<String, Object> contactRow() {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("id", "contact-1");
        row.put("displayName", "示例联系人");
        row.put("state", "active");
        row.put("customerId", "customer-target");
        row.put("title", "示例职务");
        return row;
    }

    private static Map<String, Object> quotationVersionRow() {
        Map<String, Object> obligation = new LinkedHashMap<String, Object>();
        obligation.put("subjectReference", "subject-reference-1");
        obligation.put("fulfillmentScope", "scope-reference-1");
        obligation.put("requiredConsumerCapability", "delivery-capability");
        Map<String, Object> money = new LinkedHashMap<String, Object>();
        money.put("amount", "10.00");
        money.put("currency", "CNY");
        Map<String, Object> line = new LinkedHashMap<String, Object>();
        line.put("quotationLineId", "quotation-line-1");
        line.put("offeringId", "offering-1");
        line.put("offeringReference", "reference-1");
        line.put("quantity", "1");
        line.put("unit", "项");
        line.put("unitPrice", money);
        line.put("lineTotal", money);
        line.put("obligation", obligation);
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("quotationId", "quotation-1");
        row.put("version", 1);
        row.put("state", "draft");
        row.put("settlementCurrency", "CNY");
        row.put("validUntil", "2026-08-15T00:00:00Z");
        row.put("totalAmount", money);
        row.put("issuedAt", null);
        row.put("terminalAt", null);
        row.put("lines", Collections.singletonList(line));
        return row;
    }

    /**
     * 契约测试启动类：@SpringBootApplication 让底座 AutoConfig 与元注解处理器生效，
     * @Primary 桩令牌链满足 @ConditionalOnBean(TokenManager)。
     */
    @SpringBootApplication
    @EnableFeignClients(basePackageClasses = CrmFeignClient.class)
    static class ContractTestApplication {

        /**
         * 测试桩令牌链：固定令牌，不连接任何真实凭据源。
         */
        @Bean
        @Primary
        public TokenManager testTokenManager() {
            return new TokenManager() {
                @Override
                public String getToken() {
                    return "test-token";
                }

                @Override
                public void clearToken() {
                    // 桩实现无缓存可清
                }
            };
        }
    }
}
