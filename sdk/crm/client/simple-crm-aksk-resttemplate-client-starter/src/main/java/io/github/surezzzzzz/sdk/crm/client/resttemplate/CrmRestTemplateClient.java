package io.github.surezzzzzz.sdk.crm.client.resttemplate;

import io.github.surezzzzzz.sdk.crm.client.CrmClient;
import io.github.surezzzzzz.sdk.crm.client.CrmClientFieldSet;
import io.github.surezzzzzz.sdk.crm.client.CrmPatchRouting;
import io.github.surezzzzzz.sdk.crm.client.command.CrmClientCommand;
import io.github.surezzzzzz.sdk.crm.client.codec.CrmWireCodec;
import io.github.surezzzzzz.sdk.crm.client.exception.CrmClientProtocolException;
import io.github.surezzzzzz.sdk.crm.client.exception.CrmClientRetryableException;
import io.github.surezzzzzz.sdk.crm.client.model.CrmPageView;
import io.github.surezzzzzz.sdk.crm.client.model.CrmResourceView;
import io.github.surezzzzzz.sdk.crm.client.validation.CrmResponseValidator;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CRM RestTemplate 客户端：方法体 = 底座认证传输 exchange + 路径常量拼装 + wire 解析校验。
 *
 * <p>非 2xx：4xx 抛 {@link CrmClientProtocolException}（不可重试），5xx/网络抛
 * {@link CrmClientRetryableException}（可重试；写命令重发安全由服务端幂等键保证）。</p>
 *
 * @author surezzzzzz
 */
public class CrmRestTemplateClient implements CrmClient {

    /**
     * API 基路径。
     */
    public static final String API_BASE = "/api/crm";
    private static final String FIELD_STATE = "state";
    private static final String FIELD_OWNER = "owner";
    private static final String STATE_MERGED = "merged";

    private final RestTemplate restTemplate;
    private final String baseUrl;

    /**
     * 创建客户端。
     *
     * @param restTemplate AKSK 底座认证传输（akskClientRestTemplate）
     * @param baseUrl CRM Server 基地址
     */
    public CrmRestTemplateClient(RestTemplate restTemplate, String baseUrl) {
        this.restTemplate = restTemplate;
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("CRM Server baseUrl must be configured");
        }
        this.baseUrl = baseUrl.trim();
    }

    @Override
    public CrmResourceView createCustomer(CrmClientCommand command, String displayName) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("displayName", displayName);
        return post("/customers", command, body, CrmClientFieldSet.ARCHIVE_REQUIRED,
                CrmClientFieldSet.ARCHIVE);
    }

    @Override
    public CrmResourceView getCustomer(String customerId) {
        return get("/customers/" + customerId, CrmClientFieldSet.ARCHIVE_REQUIRED,
                CrmClientFieldSet.ARCHIVE);
    }

    @Override
    public CrmPageView listCustomers(String state, String cursor, int limit) {
        return listCustomers(state, null, cursor, limit);
    }

    @Override
    public CrmPageView listCustomers(String state, String keyword, String cursor, int limit) {
        return page("/customers", params(state, keyword, cursor, limit), "items",
                CrmClientFieldSet.ARCHIVE_REQUIRED, CrmClientFieldSet.ARCHIVE);
    }

    @Override
    public CrmResourceView patchCustomer(CrmClientCommand command, String customerId,
                                        Map<String, Object> patch) {
        if (patch != null && STATE_MERGED.equals(patch.get(FIELD_STATE))) {
            return CrmResponseValidator.customerMerge(exchange(HttpMethod.PATCH,
                    "/customers/" + customerId, command, patch, CrmPatchRouting.of(patch)));
        }
        if (patch != null && patch.get(FIELD_OWNER) != null) {
            return CrmResponseValidator.customerTransfer(exchange(HttpMethod.PATCH,
                    "/customers/" + customerId, command, patch, CrmPatchRouting.of(patch)));
        }
        return patch("/customers/" + customerId, command, patch,
                CrmClientFieldSet.ARCHIVE_REQUIRED, CrmClientFieldSet.ARCHIVE);
    }




    @Override
    public CrmResourceView createContact(CrmClientCommand command, String customerId,
                                         String displayName, String title) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("customerId", customerId);
        body.put("displayName", displayName);
        if (title != null) {
            body.put("title", title);
        }
        return post("/contacts", command, body, CrmClientFieldSet.CONTACT_REQUIRED,
                CrmClientFieldSet.CONTACT);
    }

    @Override
    public CrmResourceView getContact(String contactId) {
        return get("/contacts/" + contactId, CrmClientFieldSet.CONTACT_REQUIRED,
                CrmClientFieldSet.CONTACT);
    }

    @Override
    public CrmPageView listContacts(String customerId, String cursor, int limit) {
        Map<String, String> query = new LinkedHashMap<String, String>();
        query.put("customerId", customerId);
        if (cursor != null) {
            query.put("cursor", cursor);
        }
        query.put("limit", String.valueOf(limit));
        return page("/contacts", query, "items", CrmClientFieldSet.CONTACT_REQUIRED,
                CrmClientFieldSet.CONTACT);
    }

    @Override
    public CrmResourceView createOffering(CrmClientCommand command, String offeringReference,
                                          String displayName, String capabilityType,
                                          String requiredConsumerCapability) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("offeringReference", offeringReference);
        body.put("displayName", displayName);
        body.put("capabilityType", capabilityType);
        body.put("requiredConsumerCapability", requiredConsumerCapability);
        return post("/offerings", command, body, CrmClientFieldSet.OFFERING_REQUIRED,
                CrmClientFieldSet.OFFERING);
    }

    @Override
    public CrmResourceView getOffering(String offeringId) {
        return get("/offerings/" + offeringId, CrmClientFieldSet.OFFERING_REQUIRED,
                CrmClientFieldSet.OFFERING);
    }

    @Override
    public CrmPageView listOfferings(String state, String cursor, int limit) {
        return listOfferings(state, null, cursor, limit);
    }

    @Override
    public CrmPageView listOfferings(String state, String keyword, String cursor, int limit) {
        return page("/offerings", params(state, keyword, cursor, limit), "items",
                CrmClientFieldSet.OFFERING_REQUIRED, CrmClientFieldSet.OFFERING);
    }

    @Override
    public CrmResourceView createOpportunity(CrmClientCommand command, String customerId, String title,
                                              String primaryOfferingId, String contactId,
                                              String expectedCloseDate, String estimatedAmount, String currency) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("customerId", customerId);
        body.put("title", title);
        putIfPresent(body, "primaryOfferingId", primaryOfferingId);
        putIfPresent(body, "contactId", contactId);
        putIfPresent(body, "expectedCloseDate", expectedCloseDate);
        putIfPresent(body, "estimatedAmount", estimatedAmount);
        putIfPresent(body, "currency", currency);
        return post("/opportunities", command, body, CrmClientFieldSet.OPPORTUNITY_REQUIRED,
                CrmClientFieldSet.OPPORTUNITY);
    }

    @Override
    public CrmResourceView getOpportunity(String opportunityId) {
        return get("/opportunities/" + opportunityId, CrmClientFieldSet.OPPORTUNITY_REQUIRED,
                CrmClientFieldSet.OPPORTUNITY);
    }

    @Override
    public CrmPageView listOpportunities(String stage, String customerId, String keyword,
                                         String cursor, int limit) {
        Map<String, String> query = new LinkedHashMap<String, String>();
        putIfPresent(query, "stage", stage);
        putIfPresent(query, "customerId", customerId);
        putIfPresent(query, "keyword", keyword);
        putIfPresent(query, "cursor", cursor);
        query.put("limit", String.valueOf(limit));
        return page("/opportunities", query, "items", CrmClientFieldSet.OPPORTUNITY_REQUIRED,
                CrmClientFieldSet.OPPORTUNITY);
    }

    @Override
    public CrmResourceView patchOpportunityStage(CrmClientCommand command, String opportunityId,
                                                 String stage, String lostReason) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("stage", stage);
        putIfPresent(body, "lostReason", lostReason);
        Map<String, String> query = java.util.Collections.singletonMap("stage", stage);
        return CrmResponseValidator.object(exchange(HttpMethod.PATCH, "/opportunities/" + opportunityId,
                        command, body, query), CrmClientFieldSet.OPPORTUNITY_REQUIRED,
                CrmClientFieldSet.OPPORTUNITY);
    }

    @Override
    public CrmResourceView patchOpportunityOwner(CrmClientCommand command, String opportunityId,
                                                 String owner) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("owner", owner);
        Map<String, String> query = java.util.Collections.singletonMap("owner", owner);
        return CrmResponseValidator.object(exchange(HttpMethod.PATCH, "/opportunities/" + opportunityId,
                        command, body, query), CrmClientFieldSet.OPPORTUNITY_REQUIRED,
                CrmClientFieldSet.OPPORTUNITY);
    }



    @Override
    public CrmResourceView createQuotation(CrmClientCommand command, String customerId,
                                           String validUntil, List<Map<String, Object>> lines) {
        return createQuotation(command, customerId, null, validUntil, lines);
    }

    @Override
    public CrmResourceView createQuotation(CrmClientCommand command, String customerId,
                                           String opportunityId, String validUntil,
                                           List<Map<String, Object>> lines) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("customerId", customerId);
        putIfPresent(body, "opportunityId", opportunityId);
        body.put("validUntil", validUntil);
        body.put("lines", lines);
        return post("/quotations", command, body, CrmClientFieldSet.QUOTATION_REQUIRED,
                CrmClientFieldSet.QUOTATION);
    }

    @Override
    public CrmResourceView editQuotation(CrmClientCommand command, String quotationId,
                                              String validUntil, List<Map<String, Object>> lines) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("validUntil", validUntil);
        body.put("lines", lines);
        return put("/quotations/" + quotationId, command, body,
                CrmClientFieldSet.QUOTATION_REQUIRED, CrmClientFieldSet.QUOTATION);
    }

    @Override
    public CrmResourceView getQuotation(String quotationId) {
        return get("/quotations/" + quotationId, CrmClientFieldSet.QUOTATION_REQUIRED,
                CrmClientFieldSet.QUOTATION);
    }

    @Override
    public CrmResourceView getQuotationVersion(String quotationId, int version) {
        Map<String, Object> payload = exchange(HttpMethod.GET,
                "/quotations/" + quotationId + "/versions/" + version, null, null, null);
        return CrmResponseValidator.object(payload, CrmClientFieldSet.QUOTATION_VERSION_REQUIRED,
                CrmClientFieldSet.QUOTATION_VERSION);
    }

    @Override
    public CrmPageView listQuotations(String state, String cursor, int limit) {
        return listQuotations(state, null, cursor, limit);
    }

    @Override
    public CrmPageView listQuotations(String state, String keyword, String cursor, int limit) {
        return page("/quotations", params(state, keyword, cursor, limit), "items",
                CrmClientFieldSet.QUOTATION_REQUIRED, CrmClientFieldSet.QUOTATION);
    }




    @Override
    public CrmResourceView renegotiateQuotation(CrmClientCommand command,
                                                String sourceQuotationId) {
        // v2：重议=POST /quotations?sourceQuotationId=…（查询参数分派挂专属码，body 可省略）
        return post("/quotations",
                java.util.Collections.singletonMap("sourceQuotationId", sourceQuotationId),
                command, null, CrmClientFieldSet.QUOTATION_REQUIRED,
                CrmClientFieldSet.QUOTATION);
    }


    @Override
    public CrmResourceView getOrder(String orderId) {
        return get("/orders/" + orderId, CrmClientFieldSet.ORDER_REQUIRED,
                CrmClientFieldSet.ORDER);
    }

    @Override
    public CrmPageView listOrders(String cursor, int limit) {
        return listOrders(null, cursor, limit);
    }

    @Override
    public CrmPageView listOrders(String keyword, String cursor, int limit) {
        Map<String, String> query = new LinkedHashMap<String, String>();
        if (keyword != null) {
            query.put("keyword", keyword);
        }
        if (cursor != null) {
            query.put("cursor", cursor);
        }
        query.put("limit", String.valueOf(limit));
        return page("/orders", query, "items", CrmClientFieldSet.ORDER_LIST_REQUIRED,
                CrmClientFieldSet.ORDER_LIST);
    }

    @Override
    public CrmResourceView getFulfillmentItem(String fulfillmentId) {
        return get("/fulfillment-items/" + fulfillmentId, CrmClientFieldSet.FULFILLMENT_REQUIRED,
                CrmClientFieldSet.FULFILLMENT);
    }

    @Override
    public CrmPageView listFulfillmentItems(String state, String orderId, String cursor, int limit) {
        Map<String, String> query = new LinkedHashMap<String, String>();
        if (state != null) {
            query.put("state", state);
        }
        if (orderId != null) {
            query.put("orderId", orderId);
        }
        if (cursor != null) {
            query.put("cursor", cursor);
        }
        query.put("limit", String.valueOf(limit));
        return page("/fulfillment-items", query, "items",
                CrmClientFieldSet.FULFILLMENT_REQUIRED, CrmClientFieldSet.FULFILLMENT);
    }



    @Override
    public CrmResourceView upsertFulfillmentConsumer(CrmClientCommand command,
                                                      String consumerId, String capability,
                                                      int protocolVersion, boolean enabled) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("consumerId", consumerId);
        body.put("capability", capability);
        body.put("protocolVersion", protocolVersion);
        body.put("enabled", enabled);
        return post("/fulfillment-consumers", command, body, CrmClientFieldSet.CONSUMER_UPSERT_REQUIRED,
                CrmClientFieldSet.CONSUMER);
    }

    @Override
    public CrmPageView listFulfillmentConsumers() {
        Map<String, Object> payload = exchange(HttpMethod.GET, "/fulfillment-consumers", null,
                null, null);
        return CrmResponseValidator.completeList(payload, "items",
                CrmClientFieldSet.CONSUMER_UPSERT_REQUIRED, CrmClientFieldSet.CONSUMER);
    }

    @Override
    public CrmPageView listOperationLogs(String resourceId, String cursor, int limit) {
        Map<String, String> query = new LinkedHashMap<String, String>();
        if (resourceId != null) {
            query.put("resourceId", resourceId);
        }
        if (cursor != null) {
            query.put("cursor", cursor);
        }
        query.put("limit", String.valueOf(limit));
        return page("/operation-logs", query, "items", AUDIT_REQUIRED, AUDIT_ALLOWED);
    }

    // ===== 传输与映射 =====

    private static final java.util.Set<String> AUDIT_REQUIRED = CrmClientFieldSet.OPERATION_LOG_REQUIRED;
    private static final java.util.Set<String> AUDIT_ALLOWED = CrmClientFieldSet.OPERATION_LOG;

    private static boolean isConfirmationPatch(Map<String, Object> patch) {
        return patch != null && "confirmed".equals(patch.get(FIELD_STATE));
    }

    /**
     * 查询串值编码：判别值（如 owner=iam:xxx）与关键字（可含中文）必须百分号编码后拼入 URL。
     */
    private static String encode(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8");
        } catch (java.io.UnsupportedEncodingException exception) {
            throw new IllegalStateException("UTF-8 不可用", exception);
        }
    }

    private static java.util.Set<String> unmodifiable(String... keys) {
        return java.util.Collections.unmodifiableSet(
                new java.util.LinkedHashSet<String>(java.util.Arrays.asList(keys)));
    }

    private Map<String, String> params(String state, String keyword, String cursor, int limit) {
        Map<String, String> query = new LinkedHashMap<String, String>();
        if (state != null) {
            query.put("state", state);
        }
        if (keyword != null) {
            query.put("keyword", keyword);
        }
        if (cursor != null) {
            query.put("cursor", cursor);
        }
        query.put("limit", String.valueOf(limit));
        return query;
    }

    private static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private static void putIfPresent(Map<String, String> target, String key, String value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    @Override
    public CrmResourceView patchOffering(CrmClientCommand command, String offeringId,
                                         Map<String, Object> patch) {
        return patch("/offerings/" + offeringId, command, patch,
                CrmClientFieldSet.OFFERING_REQUIRED, CrmClientFieldSet.OFFERING);
    }

    @Override
    public CrmResourceView patchQuotation(CrmClientCommand command, String quotationId,
                                          Map<String, Object> patch) {
        java.util.Set<String> required = isConfirmationPatch(patch)
                ? CrmClientFieldSet.QUOTATION_CONFIRMATION_REQUIRED
                : CrmClientFieldSet.QUOTATION_REQUIRED;
        java.util.Set<String> allowed = isConfirmationPatch(patch)
                ? CrmClientFieldSet.QUOTATION_CONFIRMATION
                : CrmClientFieldSet.QUOTATION;
        return patch("/quotations/" + quotationId, command, patch,
                required, allowed);
    }

    @Override
    public CrmResourceView patchFulfillmentItem(CrmClientCommand command, String fulfillmentId,
                                                Map<String, Object> patch) {
        return patch("/fulfillment-items/" + fulfillmentId, command, patch,
                CrmClientFieldSet.FULFILLMENT_REQUIRED, CrmClientFieldSet.FULFILLMENT);
    }

    private CrmResourceView get(String path, java.util.Set<String> required,
                                java.util.Set<String> allowed) {
        return CrmResponseValidator.object(exchange(HttpMethod.GET, path, null, null, null), required,
                allowed);
    }

    private CrmResourceView post(String path, CrmClientCommand command,
                                 Map<String, Object> body, java.util.Set<String> required,
                                 java.util.Set<String> allowed) {
        return post(path, null, command, body, required, allowed);
    }

    private CrmResourceView post(String path, Map<String, String> query, CrmClientCommand command,
                                 Map<String, Object> body, java.util.Set<String> required,
                                 java.util.Set<String> allowed) {
        return CrmResponseValidator.object(
                exchange(HttpMethod.POST, path, command, body, query), required, allowed);
    }

    private CrmResourceView put(String path, CrmClientCommand command,
                                Map<String, Object> body, java.util.Set<String> required,
                                java.util.Set<String> allowed) {
        return CrmResponseValidator.object(
                exchange(HttpMethod.PUT, path, command, body, null), required, allowed);
    }

    private CrmResourceView patch(String path, CrmClientCommand command,
                                 Map<String, Object> body, java.util.Set<String> required,
                                 java.util.Set<String> allowed) {
        return CrmResponseValidator.object(
                exchange(HttpMethod.PATCH, path, command, body, CrmPatchRouting.of(body)),
                required, allowed);
    }

    private CrmPageView page(String path, Map<String, String> query, String itemsKey,
                             java.util.Set<String> required, java.util.Set<String> allowed) {
        Map<String, Object> payload = exchange(HttpMethod.GET, path, null, null, query);
        return CrmResponseValidator.page(payload, itemsKey, required, allowed);
    }

    private Map<String, Object> exchange(HttpMethod method, String path, CrmClientCommand command,
                                         Map<String, Object> body, Map<String, String> query) {
        StringBuilder url = new StringBuilder(baseUrl).append(API_BASE).append(path);
        if (query != null && !query.isEmpty()) {
            url.append('?');
            boolean first = true;
            for (Map.Entry<String, String> entry : query.entrySet()) {
                if (!first) {
                    url.append('&');
                }
                url.append(entry.getKey()).append('=').append(encode(entry.getValue()));
                first = false;
            }
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (command != null) {
            headers.set("Idempotency-Key", command.getIdempotencyKey());
            headers.set("X-Correlation-Id", command.getCorrelationId() == null
                    ? UUID.randomUUID().toString() : command.getCorrelationId());
        }
        String json = body == null ? null : CrmWireCodec.write(body);
        try {
            ResponseEntity<String> response = restTemplate.exchange(java.net.URI.create(url.toString()), method,
                    new HttpEntity<String>(json, headers), String.class);
            String responseBody = response.getBody();
            return responseBody == null || responseBody.trim().isEmpty()
                    ? new HashMap<String, Object>() : CrmWireCodec.readMap(responseBody);
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().is4xxClientError()) {
                throw new CrmClientProtocolException(e.getRawStatusCode(),
                        "CRM 拒绝: " + e.getStatusCode().value());
            }
            throw new CrmClientRetryableException("CRM 不可用: " + e.getStatusCode().value(), e);
        } catch (org.springframework.web.client.ResourceAccessException e) {
            throw new CrmClientRetryableException("CRM 网络不可达", e);
        }
    }
}
