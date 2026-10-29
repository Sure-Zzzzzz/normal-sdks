package io.github.surezzzzzz.sdk.crm.client.test.cases;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.surezzzzzz.sdk.crm.client.CrmClient;
import io.github.surezzzzzz.sdk.crm.client.command.CrmClientCommand;
import io.github.surezzzzzz.sdk.crm.client.exception.CrmClientProtocolException;
import io.github.surezzzzzz.sdk.crm.client.exception.CrmClientRetryableException;
import io.github.surezzzzzz.sdk.crm.client.model.CrmPageView;
import io.github.surezzzzzz.sdk.crm.client.model.CrmResourceView;
import io.github.surezzzzzz.sdk.crm.client.resttemplate.CrmRestTemplateClient;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RT 契约桩测试：内置 HttpServer 桩按公开 HTTP 契约断言路径/方法/幂等键 header/wire 格式与错误映射。
 *
 * <p>桩钉的是 wire 契约（不是 mock 行为）；服务端发版后的端到端证明由真链验收补足。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
class CrmRestTemplateClientContractTest {

    private static HttpServer server;
    private static CrmClient client;
    private static final AtomicReference<String> lastMethod = new AtomicReference<String>();
    private static final AtomicReference<String> lastPath = new AtomicReference<String>();
    private static final AtomicReference<String> lastQuery = new AtomicReference<String>();
    private static final AtomicReference<String> lastIdempotencyKey =
            new AtomicReference<String>();
    private static final AtomicReference<Map<String, Object>> lastContactRequest =
            new AtomicReference<Map<String, Object>>();

    /**
     * 起桩。
     *
     * @throws Exception 端口占用
     */
    @BeforeAll
    @SuppressWarnings("unchecked")
    static void startStub() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        server.createContext("/", (HttpExchange exchange) -> {
            lastMethod.set(exchange.getRequestMethod());
            lastPath.set(exchange.getRequestURI().getPath());
            lastQuery.set(exchange.getRequestURI().getRawQuery());
            lastIdempotencyKey.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            String path = exchange.getRequestURI().getPath();
            String query = exchange.getRequestURI().getRawQuery();
            String response;
            int status = 200;
            if (path.endsWith("/customers") && "GET".equals(exchange.getRequestMethod())) {
                Map<String, Object> row = archiveRow();
                List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
                items.add(row);
                Map<String, Object> body = new LinkedHashMap<String, Object>();
                body.put("items", items);
                body.put("nextCursor", "Y3Vyc29y");
                if (query != null && query.contains("numbered-response")) {
                    body.remove("nextCursor");
                    body.put("page", 1);
                    body.put("pageSize", 20);
                    body.put("total", 45);
                }
                response = mapper.writeValueAsString(body);
            } else if (path.startsWith("/api/crm/contacts")) {
                Map<String, Object> row = contactRow();
                if ("POST".equals(exchange.getRequestMethod())) {
                    lastContactRequest.set(mapper.readValue(exchange.getRequestBody(), Map.class));
                }
                if (path.endsWith("/null-title")) {
                    row.put("title", null);
                } else if (path.endsWith("/unknown-contact-field")
                        || query != null && query.contains("unknown-contact-field")) {
                    row.put("owner", "customer-target");
                } else if (path.endsWith("/numeric-title")
                        || query != null && query.contains("numeric-title")) {
                    row.put("title", 1);
                }
                if (path.endsWith("/contacts") && "GET".equals(exchange.getRequestMethod())) {
                    Map<String, Object> body = new LinkedHashMap<String, Object>();
                    body.put("items", Collections.singletonList(row));
                    body.put("nextCursor", null);
                    response = mapper.writeValueAsString(body);
                } else {
                    response = mapper.writeValueAsString(row);
                }
            } else if (path.contains("/quotations/") && path.contains("/versions/")) {
                response = mapper.writeValueAsString(quotationVersionRow());
            } else if (path.startsWith("/api/crm/offerings")) {
                Map<String, Object> row = offeringRow();
                if (path.endsWith("/offerings") && "GET".equals(exchange.getRequestMethod())) {
                    Map<String, Object> body = new LinkedHashMap<String, Object>();
                    body.put("items", Collections.singletonList(row));
                    body.put("nextCursor", null);
                    response = mapper.writeValueAsString(body);
                } else {
                    response = mapper.writeValueAsString(row);
                }
            } else if (path.startsWith("/api/crm/customers/") && "PATCH".equals(exchange.getRequestMethod())
                    && query != null && query.contains("state=merged")) {
                Map<String, Object> body = mergeRow();
                if (path.endsWith("malformed-merge")) {
                    ((Map<String, Object>) body.get("source")).put("extraField", "unsupported");
                }
                response = mapper.writeValueAsString(body);
            } else if (path.startsWith("/api/crm/customers/") && "PATCH".equals(exchange.getRequestMethod())
                    && query != null && query.startsWith("owner=")) {
                Map<String, Object> body = transferRow();
                if (path.endsWith("malformed-transfer")) {
                    body.put("transferredQuotations", "not-an-array");
                }
                response = mapper.writeValueAsString(body);
            } else if (path.contains("/customers/") && path.contains("/notfound")) {
                status = 404;
                response = "{\"message\":\"资源不存在或不可访问\"}";
            } else if (path.contains("/customers/") && path.contains("/conflict")) {
                status = 409;
                response = "{\"message\":\"请求与当前资源状态冲突\"}";
            } else if (path.contains("/quotations/") && path.contains("/server-error")) {
                status = 503;
                response = "{\"message\":\"依赖能力暂不可用\"}";
            } else if (path.contains("/unknown-field")) {
                Map<String, Object> body = archiveRow();
                body.put("tenantId", "脏字段");
                response = mapper.writeValueAsString(body);
            } else if (path.startsWith("/api/crm/fulfillment-items")
                    && "GET".equals(exchange.getRequestMethod())) {
                Map<String, Object> row = fulfillmentRow();
                List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
                items.add(row);
                Map<String, Object> body = new LinkedHashMap<String, Object>();
                body.put("items", items);
                body.put("nextCursor", null);
                response = mapper.writeValueAsString(body);
            } else if (path.startsWith("/api/crm/fulfillment")) {
                response = mapper.writeValueAsString(fulfillmentRow());
            } else if (path.endsWith("/quotations") && "GET".equals(exchange.getRequestMethod())) {
                Map<String, Object> body = new LinkedHashMap<String, Object>();
                body.put("items", Collections.singletonList(quotationRow()));
                body.put("nextCursor", null);
                response = mapper.writeValueAsString(body);
            } else if (path.startsWith("/api/crm/quotations")
                    && "PATCH".equals(exchange.getRequestMethod())) {
                response = mapper.writeValueAsString(quotationConfirmationRow());
            } else if (path.startsWith("/api/crm/quotations")
                    || path.startsWith("/api/crm/quotation-")) {
                response = mapper.writeValueAsString(quotationRow());
            } else {
                response = mapper.writeValueAsString(archiveRow());
            }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        int port = server.getAddress().getPort();
        // 与生产同构：akskClientRestTemplate 用 Apache HttpClient 5（PATCH 原生支持）

        org.springframework.http.client.HttpComponentsClientHttpRequestFactory factory =
                new org.springframework.http.client.HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(2000);
        RestTemplate restTemplate = new RestTemplate(factory);
        client = new CrmRestTemplateClient(restTemplate, "http://127.0.0.1:" + port);
        log.info("契约桩启动：port={}", port);
    }

    /**
     * 停桩。
     */
    @AfterAll
    static void stopStub() {
        server.stop(0);
    }

    @Test
    void createCustomerPostsJsonWithIdempotencyKeyHeader() {
        CrmResourceView view = client.createCustomer(new CrmClientCommand("idem-create-1", null),
                "桩客户A");
        log.info("创建客户：method={}，path={}，幂等键={}，返回 id={}", lastMethod.get(),
                lastPath.get(), lastIdempotencyKey.get(), view.getString("id"));
        assertEquals("POST", lastMethod.get());
        assertEquals("/api/crm/customers", lastPath.get());
        assertEquals("idem-create-1", lastIdempotencyKey.get(), "写命令必须带幂等键 header");
        assertEquals("customer-1", view.getString("id"));
        assertEquals("active", view.getString("state"));
    }

    @Test
    void getCustomerUsesExactPath() {
        client.getCustomer("customer-9");
        log.info("点查客户：method={}，path={}", lastMethod.get(), lastPath.get());
        assertEquals("GET", lastMethod.get());
        assertEquals("/api/crm/customers/customer-9", lastPath.get());
    }

    @Test
    void contactCreateReadAndListRetainCustomerIdAndTitle() {
        CrmResourceView created = client.createContact(new CrmClientCommand("idem-contact-1", null),
                "customer-target", "示例联系人", "示例职务");
        log.info("联系人传输：创建、点查、列表均保留客户归属与职务");
        assertEquals("POST", lastMethod.get());
        assertEquals("/api/crm/contacts", lastPath.get());
        assertEquals("idem-contact-1", lastIdempotencyKey.get());
        assertEquals("customer-target", lastContactRequest.get().get("customerId"));
        assertEquals("示例职务", lastContactRequest.get().get("title"));
        assertEquals("customer-target", created.getString("customerId"));
        assertEquals("示例职务", created.getString("title"));
        CrmResourceView loaded = client.getContact("contact-1");
        assertEquals("/api/crm/contacts/contact-1", lastPath.get());
        assertEquals("customer-target", loaded.getString("customerId"));
        assertEquals("示例职务", loaded.getString("title"));
        CrmPageView listed = client.listContacts("customer-target", null, 20);
        assertEquals("customerId=customer-target&limit=20", lastQuery.get());
        assertEquals("customer-target", listed.getItems().get(0).getString("customerId"));
        assertEquals("示例职务", listed.getItems().get(0).getString("title"));
        assertNull(client.getContact("null-title").getNullable("title"));
        client.createContact(new CrmClientCommand("idem-contact-no-title", null),
                "customer-target", "示例联系人", null);
        assertFalse(lastContactRequest.get().containsKey("title"), "空职务请求应省略可选字段");
    }

    @Test
    void contactReadAndListRejectUnknownFieldsAndInvalidTitle() {
        log.info("联系人传输拒绝旧 owner 字段及非字符串职务，列表行同样校验");
        assertThrows(CrmClientProtocolException.class, () -> client.getContact("unknown-contact-field"));
        assertThrows(CrmClientProtocolException.class, () -> client.getContact("numeric-title"));
        assertThrows(CrmClientProtocolException.class,
                () -> client.listContacts("unknown-contact-field", null, 20));
        assertThrows(CrmClientProtocolException.class,
                () -> client.listContacts("numeric-title", null, 20));
    }

    @Test
    @SuppressWarnings("unchecked")
    void quotationVersionRetainsNestedObligationFields() {
        CrmResourceView result = client.getQuotationVersion("quotation-1", 1);
        Map<String, Object> line = ((List<Map<String, Object>>) result.get("lines")).get(0);
        Map<String, Object> obligation = (Map<String, Object>) line.get("obligation");
        log.info("报价版本传输保留每行的履约对象、范围及消费者能力");
        assertEquals("/api/crm/quotations/quotation-1/versions/1", lastPath.get());
        assertEquals("subject-reference-1", obligation.get("subjectReference"));
        assertEquals("scope-reference-1", obligation.get("fulfillmentScope"));
        assertEquals("delivery-capability", obligation.get("requiredConsumerCapability"));
    }

    @Test
    void listCustomersParsesCursorPage() {
        CrmPageView page = client.listCustomers("active", null, 20);
        log.info("列表：行数={}，nextCursor={}", page.getItems().size(), page.getNextCursor());
        assertEquals(1, page.getItems().size());
        assertTrue(page.hasNext());
    }

    @Test
    void listKeywordOverloadsSendOpenApiFilter() {
        client.listCustomers("active", "customer-9", null, 20);
        assertEquals("state=active&keyword=customer-9&limit=20", lastQuery.get());
        client.listOfferings("published", "reference-9", null, 20);
        assertEquals("state=published&keyword=reference-9&limit=20", lastQuery.get());
        client.listQuotations("issued", "customer-9", null, 20);
        assertEquals("state=issued&keyword=customer-9&limit=20", lastQuery.get());
        log.info("列表关键字过滤：客户、目录、报价均透传 OpenAPI keyword 参数");
    }

    @Test
    void editQuotationDraftUsesPutToDraftPath() {
        client.editQuotation(new CrmClientCommand("idem-edit-1", null), "quotation-1",
                "2026-08-15T00:00:00Z", Collections.<Map<String, Object>>emptyList());
        log.info("编辑草稿：method={}，path={}", lastMethod.get(), lastPath.get());
        assertEquals("PUT", lastMethod.get());
        assertEquals("/api/crm/quotations/quotation-1", lastPath.get());
    }

    @Test
    void confirmQuotationPatchesQuotationResource() {
        client.patchQuotation(new CrmClientCommand("idem-confirm-1", null), "quotation-1",
                java.util.Collections.<String, Object>singletonMap("state", "confirmed"));
        log.info("确认报价：method={}，path={}，幂等键={}", lastMethod.get(), lastPath.get(),
                lastIdempotencyKey.get());
        assertEquals("PATCH", lastMethod.get());
        assertEquals("/api/crm/quotations/quotation-1", lastPath.get());
        assertEquals("idem-confirm-1", lastIdempotencyKey.get());
    }

    @Test
    void fulfillmentPullAndReportPaths() {
        client.listFulfillmentItems("pending_dispatch", null, null, 20);
        log.info("履约拉取：path={}", lastPath.get());
        assertEquals("/api/crm/fulfillment-items", lastPath.get());

        client.patchFulfillmentItem(new CrmClientCommand("idem-report-1", null),
                "fulfillment-1", new java.util.LinkedHashMap<String, Object>() {{
                    put("sequence", 1); put("state", "in_progress"); put("note", "已认领");
                }});
        assertEquals("PATCH", lastMethod.get());
        assertEquals("/api/crm/fulfillment-items/fulfillment-1", lastPath.get());
        // 分派判别值必须镜像进查询串（服务端按 ?sequence 路由挂专属码）
        assertEquals("state=in_progress&sequence=1", lastQuery.get());
    }

    @Test
    void renegotiateQuotationPostsSourceParamToQuotations() {
        client.renegotiateQuotation(new CrmClientCommand("idem-renego-1", null),
                "quotation-1");
        assertEquals("POST", lastMethod.get());
        assertEquals("/api/crm/quotations", lastPath.get());
        // 重议=POST /quotations?sourceQuotationId=…（查询参数分派挂专属码）
        assertEquals("sourceQuotationId=quotation-1", lastQuery.get());
        assertEquals("idem-renego-1", lastIdempotencyKey.get());
    }

    @Test
    void notFoundMapsToProtocolExceptionWith404() {
        CrmClientProtocolException exception = assertThrows(CrmClientProtocolException.class,
                () -> client.getCustomer("notfound"));
        log.info("404 映射：statusCode={}", exception.getStatusCode());
        assertEquals(404, exception.getStatusCode(), "4xx 必须映射为不可重试协议异常");
    }

    @Test
    void conflictMapsToProtocolExceptionWith409() {
        CrmClientProtocolException exception = assertThrows(CrmClientProtocolException.class,
                () -> client.getCustomer("conflict"));
        log.info("409 映射：statusCode={}", exception.getStatusCode());
        assertEquals(409, exception.getStatusCode());
    }

    @Test
    void serverErrorMapsToRetryableException() {
        CrmClientRetryableException exception = assertThrows(CrmClientRetryableException.class,
                () -> client.getQuotation("server-error"));
        log.info("5xx 映射：{}（可重试，写重发安全=幂等键）", exception.getMessage());
        assertNotNull(exception, "5xx 必须映射为可重试异常");
    }

    @Test
    void unknownResponseFieldRejectedByValidator() {
        CrmClientProtocolException exception = assertThrows(CrmClientProtocolException.class,
                () -> client.getCustomer("unknown-field"));
        log.info("未知字段拒绝：{}", exception.getMessage());
        assertTrue(exception.getMessage().contains("未知字段"), "校验器必须拒绝未知字段");
    }

    @Test
    void offeringCreateReadListAndPatchKeepBusinessFields() {
        CrmResourceView created = client.createOffering(new CrmClientCommand("idem-offering-1", null),
                "reference-1", "示例商品", "fixed_price_fulfillment_v1", "delivery-capability");
        CrmResourceView loaded = client.getOffering("offering-1");
        CrmPageView listed = client.listOfferings("published", null, 20);
        CrmResourceView patched = client.patchOffering(new CrmClientCommand("idem-offering-patch-1", null),
                "offering-1", Collections.<String, Object>singletonMap("state", "published"));
        log.info("商品契约：创建/点查/列表/上下架均保留稳定业务引用");
        assertEquals("reference-1", created.getString("offeringReference"));
        assertEquals("reference-1", loaded.getString("offeringReference"));
        assertEquals("reference-1", listed.getItems().get(0).getString("offeringReference"));
        assertEquals("reference-1", patched.getString("offeringReference"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void mergeUsesNestedResultAndRejectsUnknownNestedFields() {
        Map<String, Object> patch = new LinkedHashMap<String, Object>();
        patch.put("state", "merged");
        patch.put("mergeTargetId", "customer-target");
        CrmResourceView result = client.patchCustomer(new CrmClientCommand("idem-merge-1", null),
                "customer-source", patch);
        log.info("合并结果：source={}，target={}", ((Map<String, Object>) result.get("source")).get("id"),
                ((Map<String, Object>) result.get("target")).get("id"));
        assertEquals("customer-source", ((Map<String, Object>) result.get("source")).get("id"));
        List<Map<String, Object>> contacts = (List<Map<String, Object>>) result.get("relocatedContacts");
        assertEquals("customer-target", contacts.get(0).get("customerId"));
        assertEquals("示例职务", contacts.get(0).get("title"));
        assertEquals("state=merged", lastQuery.get());
        assertThrows(CrmClientProtocolException.class, () -> client.patchCustomer(
                new CrmClientCommand("idem-malformed-merge-1", null), "malformed-merge", patch));
    }

    @Test
    @SuppressWarnings("unchecked")
    void transferUsesNestedResultAndEncodesOwnerExactlyOnce() {
        Map<String, Object> patch = Collections.<String, Object>singletonMap("owner", "iam:target-owner");
        CrmResourceView result = client.patchCustomer(new CrmClientCommand("idem-transfer-1", null),
                "customer-1", patch);
        log.info("转移结果：customer={}，查询串={}", ((Map<String, Object>) result.get("customer")).get("id"),
                lastQuery.get());
        assertEquals("customer-1", ((Map<String, Object>) result.get("customer")).get("id"));
        assertEquals("iam:target-owner", ((Map<String, Object>) result.get("customer")).get("owner"));
        assertEquals("owner=iam%3Atarget-owner", lastQuery.get());
        assertThrows(CrmClientProtocolException.class, () -> client.patchCustomer(
                new CrmClientCommand("idem-malformed-transfer-1", null), "malformed-transfer", patch));
    }

    @Test
    void machineListRejectsNumberedHttpResponse() {
        log.info("机器传输契约：拒绝服务端误回页码响应");
        assertThrows(CrmClientProtocolException.class, () -> client.listCustomers("numbered-response", null, 20));
    }

    private static Map<String, Object> offeringRow() {
        Map<String, Object> row = archiveRow();
        row.put("id", "offering-1");
        row.put("state", "published");
        row.put("offeringReference", "reference-1");
        row.put("capabilityType", "fixed_price_fulfillment_v1");
        row.put("requiredConsumerCapability", "delivery-capability");
        return row;
    }

    private static Map<String, Object> mergeRow() {
        Map<String, Object> source = archiveRow();
        source.put("id", "customer-source");
        source.put("state", "merged");
        Map<String, Object> target = archiveRow();
        target.put("id", "customer-target");
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("source", source);
        row.put("target", target);
        row.put("relocatedContacts", Collections.singletonList(contactRow()));
        return row;
    }

    private static Map<String, Object> transferRow() {
        Map<String, Object> quotation = new LinkedHashMap<String, Object>();
        quotation.put("quotationId", "quotation-1");
        quotation.put("owner", "iam:target-owner");
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        Map<String, Object> customer = archiveRow();
        customer.put("owner", "iam:target-owner");
        row.put("customer", customer);
        row.put("transferredQuotations", Collections.singletonList(quotation));
        return row;
    }

    private static Map<String, Object> quotationRow() {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("quotationId", "quotation-1");
        row.put("customerId", "customer-1");
        row.put("owner", "iam:actor-1");
        row.put("aggregateVersion", 2);
        row.put("currentVersion", 1);
        row.put("state", "issued");
        return row;
    }

    private static Map<String, Object> quotationConfirmationRow() {
        Map<String, Object> row = quotationRow();
        row.put("state", "confirmed");
        row.put("orderId", "order-1");
        row.put("fulfillmentItemIds", Collections.singletonList("fulfillment-1"));
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

    private static Map<String, Object> fulfillmentRow() {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("fulfillmentId", "fulfillment-1");
        row.put("orderId", "order-1");
        row.put("orderLineId", "order-line-1");
        row.put("owner", "iam:actor-1");
        row.put("assignedConsumerId", "consumer-int-1");
        row.put("state", "pending_dispatch");
        row.put("reportSequence", 0);
        row.put("consumerProtocolVersion", 1);
        Map<String, Object> obligation = new LinkedHashMap<String, Object>();
        obligation.put("subjectReference", "subject-reference-1");
        obligation.put("fulfillmentScope", "scope-reference-1");
        obligation.put("requiredConsumerCapability", "delivery-capability");
        row.put("obligation", obligation);
        return row;
    }

    private static Map<String, Object> archiveRow() {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("id", "customer-1");
        row.put("displayName", "桩客户A");
        row.put("state", "active");
        row.put("owner", "iam:actor-1");
        return row;
    }
}
