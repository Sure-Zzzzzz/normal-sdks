package io.github.surezzzzzz.sdk.crm.client.test.cases;

import io.github.surezzzzzz.sdk.crm.client.CrmClient;
import io.github.surezzzzzz.sdk.crm.client.CrmClientFieldSet;
import io.github.surezzzzzz.sdk.crm.client.command.CrmClientCommand;
import io.github.surezzzzzz.sdk.crm.client.exception.CrmClientProtocolException;
import io.github.surezzzzzz.sdk.crm.client.model.CrmPageView;
import io.github.surezzzzzz.sdk.crm.client.model.CrmResourceView;
import io.github.surezzzzzz.sdk.crm.client.validation.CrmResponseValidator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * client-core 契约测试：响应校验器（未知字段/必填缺失拒绝）、命令元数据、视图模型。
 *
 * @author surezzzzzz
 */
@Slf4j
class CrmClientCoreContractTest {

    private static final java.util.Set<String> ARCHIVE = new java.util.HashSet<>(
            Arrays.asList("id", "displayName", "state", "owner"));

    @Test
    void validatorAcceptsExactFieldsAndBuildsImmutableView() {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("id", "customer-1");
        payload.put("displayName", "客户A");
        payload.put("state", "active");
        payload.put("owner", "iam:actor-1");
        CrmResourceView view = CrmResponseValidator.object(payload, ARCHIVE, ARCHIVE);

        log.info("视图字段：id={}，state={}", view.getString("id"), view.getString("state"));
        assertEquals("customer-1", view.getString("id"));
        assertEquals("iam:actor-1", view.getString("owner"));
        assertThrows(IllegalArgumentException.class, () -> view.getString("notExist"));
    }

    @Test
    void validatorRejectsUnknownFieldAndMissingRequired() {
        Map<String, Object> withUnknown = new LinkedHashMap<String, Object>();
        withUnknown.put("id", "customer-1");
        withUnknown.put("displayName", "A");
        withUnknown.put("state", "active");
        withUnknown.put("owner", "iam:x");
        withUnknown.put("tenantId", "外字段");
        CrmClientProtocolException unknown = assertThrows(CrmClientProtocolException.class,
                () -> CrmResponseValidator.object(withUnknown, ARCHIVE, ARCHIVE));
        log.info("未知字段拒绝：{}", unknown.getMessage());

        Map<String, Object> missing = new LinkedHashMap<String, Object>();
        missing.put("id", "customer-1");
        CrmClientProtocolException missingEx = assertThrows(CrmClientProtocolException.class,
                () -> CrmResponseValidator.object(missing, ARCHIVE, ARCHIVE));
        log.info("必填缺失拒绝：{}", missingEx.getMessage());
        assertTrue(missingEx.getMessage().contains("缺失"), "必填缺失必须显式拒绝（不构造半成品）");
    }

    @Test
    void validatorBuildsPageWithCursorAndEmptyItems() {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        Map<String, Object> row = new HashMap<String, Object>();
        row.put("id", "c-1");
        row.put("displayName", "A");
        row.put("state", "active");
        row.put("owner", "iam:x");
        items.add(row);
        payload.put("customers", items);
        payload.put("nextCursor", "Y3Vyc29yLTEw");
        CrmPageView page = CrmResponseValidator.page(payload, "customers", ARCHIVE, ARCHIVE);

        log.info("列表页：行数={}，hasNext={}", page.getItems().size(), page.hasNext());
        assertEquals(1, page.getItems().size());
        assertTrue(page.hasNext());
        assertEquals("Y3Vyc29yLTEw", page.getNextCursor());

        payload.put("nextCursor", null);
        CrmPageView last = CrmResponseValidator.page(payload, "customers", ARCHIVE, ARCHIVE);
        assertFalse(last.hasNext(), "nextCursor=null 即末页");
        assertNull(last.getNextCursor());
    }

    @Test
    void machinePageRejectsNumberedOrMixedPaginationInsteadOfTruncatingConsumption() {
        Map<String, Object> numbered = new LinkedHashMap<String, Object>();
        numbered.put("items", Collections.emptyList());
        numbered.put("page", 1);
        numbered.put("pageSize", 20);
        numbered.put("total", 45);
        CrmClientProtocolException exception = assertThrows(CrmClientProtocolException.class,
                () -> CrmResponseValidator.page(numbered, "items", ARCHIVE, ARCHIVE));
        log.info("机器消费拒绝页码响应：{}", exception.getMessage());
        assertTrue(exception.getMessage().contains("未知列表字段"));

        numbered.put("nextCursor", "opaque-cursor");
        assertThrows(CrmClientProtocolException.class,
                () -> CrmResponseValidator.page(numbered, "items", ARCHIVE, ARCHIVE),
                "混合响应不能仅凭 nextCursor 被接受");
    }

    @Test
    void machinePageRejectsUnknownEnvelopeAndInvalidCursorTypes() {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("items", Collections.emptyList());
        payload.put("nextCursor", 42);
        log.info("机器消费校验：拒绝数字游标、空白游标和未知封装字段");
        assertThrows(CrmClientProtocolException.class,
                () -> CrmResponseValidator.page(payload, "items", ARCHIVE, ARCHIVE));
        payload.put("nextCursor", " ");
        assertThrows(CrmClientProtocolException.class,
                () -> CrmResponseValidator.page(payload, "items", ARCHIVE, ARCHIVE));
        payload.put("nextCursor", null);
        payload.put("extraField", "unsupported");
        assertThrows(CrmClientProtocolException.class,
                () -> CrmResponseValidator.page(payload, "items", ARCHIVE, ARCHIVE));
    }

    @Test
    void machinePageRequiresExplicitNextCursorField() {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("items", Collections.emptyList());
        log.info("机器列表缺少 nextCursor 时拒绝，避免把截断结果误判为末页");
        assertThrows(CrmClientProtocolException.class,
                () -> CrmResponseValidator.page(payload, "items", ARCHIVE, ARCHIVE));
    }

    @Test
    void orderListAndDetailUseSeparateOpenApiFieldSets() {
        Map<String, Object> money = new LinkedHashMap<String, Object>();
        money.put("amount", "12.50");
        money.put("currency", "CNY");
        Map<String, Object> obligation = new LinkedHashMap<String, Object>();
        obligation.put("subjectReference", "subject-1");
        obligation.put("fulfillmentScope", "scope-1");
        obligation.put("requiredConsumerCapability", "capability-1");
        Map<String, Object> line = new LinkedHashMap<String, Object>();
        line.put("orderLineId", "line-1");
        line.put("sourceQuotationLineId", "quotation-line-1");
        line.put("offeringReference", "offering-1");
        line.put("quantity", "1");
        line.put("unit", "set");
        line.put("unitPrice", money);
        line.put("lineTotal", money);
        line.put("obligation", obligation);

        Map<String, Object> detail = new LinkedHashMap<String, Object>();
        detail.put("orderId", "order-1");
        detail.put("sourceQuotationId", "quotation-1");
        detail.put("sourceQuotationVersion", 1);
        detail.put("customerId", "customer-1");
        detail.put("settlementCurrency", "CNY");
        detail.put("totalAmount", money);
        detail.put("owner", "iam:owner-1");
        detail.put("displayState", "pending_fulfillment");
        detail.put("lines", Collections.singletonList(line));
        CrmResourceView detailView = CrmResponseValidator.object(detail,
                CrmClientFieldSet.ORDER_REQUIRED, CrmClientFieldSet.ORDER);
        log.info("订单详情校验：orderId={}，lines={}", detailView.getString("orderId"),
                ((List<?>) detailView.get("lines")).size());
        assertEquals("order-1", detailView.getString("orderId"));

        Map<String, Object> list = new LinkedHashMap<String, Object>();
        list.put("orderId", "order-1");
        list.put("sourceQuotationId", "quotation-1");
        list.put("customerId", "customer-1");
        list.put("totalAmount", money);
        list.put("displayState", "pending_fulfillment");
        CrmResourceView listView = CrmResponseValidator.object(list,
                CrmClientFieldSet.ORDER_LIST_REQUIRED, CrmClientFieldSet.ORDER_LIST);
        assertEquals("order-1", listView.getString("orderId"));
        list.put("lines", Collections.singletonList(line));
        assertThrows(CrmClientProtocolException.class, () -> CrmResponseValidator.object(list,
                CrmClientFieldSet.ORDER_LIST_REQUIRED, CrmClientFieldSet.ORDER_LIST));
    }

    @Test
    void quotationVersionAndConfirmationRequireTheirOpenApiPayloads() {
        Map<String, Object> money = new LinkedHashMap<String, Object>();
        money.put("amount", "12.50");
        money.put("currency", "CNY");
        Map<String, Object> obligation = new LinkedHashMap<String, Object>();
        obligation.put("subjectReference", "subject-1");
        obligation.put("fulfillmentScope", "scope-1");
        obligation.put("requiredConsumerCapability", "capability-1");
        Map<String, Object> line = new LinkedHashMap<String, Object>();
        line.put("quotationLineId", "line-1");
        line.put("offeringId", "offering-1");
        line.put("offeringReference", "offering-ref-1");
        line.put("quantity", "1");
        line.put("unit", "set");
        line.put("unitPrice", money);
        line.put("lineTotal", money);
        line.put("obligation", obligation);
        Map<String, Object> version = new LinkedHashMap<String, Object>();
        version.put("quotationId", "quotation-1");
        version.put("version", 1);
        version.put("state", "issued");
        version.put("settlementCurrency", "CNY");
        version.put("validUntil", "2030-01-01T00:00:00Z");
        version.put("totalAmount", money);
        version.put("issuedAt", null);
        version.put("terminalAt", null);
        version.put("lines", Collections.singletonList(line));
        assertEquals("quotation-1", CrmResponseValidator.object(version,
                CrmClientFieldSet.QUOTATION_VERSION_REQUIRED, CrmClientFieldSet.QUOTATION_VERSION)
                .getString("quotationId"));

        Map<String, Object> confirmation = new LinkedHashMap<String, Object>();
        confirmation.put("state", "confirmed");
        confirmation.put("quotationId", "quotation-1");
        confirmation.put("customerId", "customer-1");
        confirmation.put("opportunityId", null);
        confirmation.put("owner", "iam:owner-1");
        confirmation.put("aggregateVersion", 2);
        confirmation.put("currentVersion", 1);
        confirmation.put("currentConfirmableVersion", null);
        confirmation.put("confirmedOrderId", "order-1");
        confirmation.put("supersedesQuotationId", null);
        confirmation.put("orderId", "order-1");
        confirmation.put("fulfillmentItemIds", Collections.singletonList("fulfillment-1"));
        assertEquals("order-1", CrmResponseValidator.object(confirmation,
                CrmClientFieldSet.QUOTATION_CONFIRMATION_REQUIRED, CrmClientFieldSet.QUOTATION_CONFIRMATION)
                .getString("orderId"));
        confirmation.remove("fulfillmentItemIds");
        assertThrows(CrmClientProtocolException.class, () -> CrmResponseValidator.object(confirmation,
                CrmClientFieldSet.QUOTATION_CONFIRMATION_REQUIRED, CrmClientFieldSet.QUOTATION_CONFIRMATION));
    }

    @Test
    void fulfillmentAndOperationLogHonorNullableResponseFields() {
        Map<String, Object> obligation = new LinkedHashMap<String, Object>();
        obligation.put("subjectReference", "subject-1");
        obligation.put("fulfillmentScope", "scope-1");
        obligation.put("requiredConsumerCapability", "capability-1");
        Map<String, Object> fulfillment = new LinkedHashMap<String, Object>();
        fulfillment.put("fulfillmentId", "fulfillment-1");
        fulfillment.put("orderId", "order-1");
        fulfillment.put("orderLineId", "line-1");
        fulfillment.put("owner", "iam:owner-1");
        fulfillment.put("assignedConsumerId", "consumer-1");
        fulfillment.put("state", "pending_dispatch");
        fulfillment.put("reportSequence", 0);
        fulfillment.put("consumerProtocolVersion", 1);
        fulfillment.put("obligation", obligation);
        fulfillment.put("resultData", null);
        fulfillment.put("note", null);
        fulfillment.put("reportedBy", null);
        assertEquals("fulfillment-1", CrmResponseValidator.object(fulfillment,
                CrmClientFieldSet.FULFILLMENT_REQUIRED, CrmClientFieldSet.FULFILLMENT)
                .getString("fulfillmentId"));
        fulfillment.put("reportSequence", "0");
        assertThrows(CrmClientProtocolException.class, () -> CrmResponseValidator.object(fulfillment,
                CrmClientFieldSet.FULFILLMENT_REQUIRED, CrmClientFieldSet.FULFILLMENT));

        Map<String, Object> operation = new LinkedHashMap<String, Object>();
        operation.put("owner", "iam:owner-1");
        operation.put("action", "create");
        operation.put("targetResourceType", "customer");
        operation.put("targetResourceId", "customer-1");
        operation.put("correlationId", null);
        operation.put("resultCode", null);
        operation.put("occurredAt", "2030-01-01T00:00:00Z");
        assertNull(CrmResponseValidator.object(operation, CrmClientFieldSet.OPERATION_LOG_REQUIRED,
                CrmClientFieldSet.OPERATION_LOG).getNullable("correlationId"));
        operation.remove("resultCode");
        assertThrows(CrmClientProtocolException.class, () -> CrmResponseValidator.object(operation,
                CrmClientFieldSet.OPERATION_LOG_REQUIRED, CrmClientFieldSet.OPERATION_LOG));
    }

    @Test
    void completeConsumerListWithoutCursorRemainsCompatible() {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("items", Collections.emptyList());
        CrmPageView page = CrmResponseValidator.completeList(payload, "items", ARCHIVE, ARCHIVE);
        log.info("完整消费者列表兼容：行数={}，续页={}", page.getItems().size(), page.hasNext());
        assertTrue(page.getItems().isEmpty());
        assertFalse(page.hasNext());
        assertNull(page.getNextCursor());

        payload.put("nextCursor", null);
        assertThrows(CrmClientProtocolException.class,
                () -> CrmResponseValidator.completeList(payload, "items", ARCHIVE, ARCHIVE),
                "完整列表不能混入机器游标字段");
    }

    @Test
    void commandRequiresIdempotencyKeyAndFillsCorrelationOptional() {
        assertThrows(IllegalArgumentException.class, () -> new CrmClientCommand(" ", null));
        CrmClientCommand withCorrelation = new CrmClientCommand("idem-1", "corr-1");
        CrmClientCommand withoutCorrelation = new CrmClientCommand("idem-1", "  ");
        log.info("命令元数据：幂等键={}，关联={}|空白归 null={}", withCorrelation.getIdempotencyKey(),
                withCorrelation.getCorrelationId(), withoutCorrelation.getCorrelationId() == null);
        assertEquals("idem-1", withCorrelation.getIdempotencyKey());
        assertEquals("corr-1", withCorrelation.getCorrelationId());
        assertNull(withoutCorrelation.getCorrelationId(), "空白关联 ID 归 null（传输层生成）");
    }

    @Test
    void opportunityExtensionsRemainDefaultMethodsForLegacyImplementations() throws Exception {
        assertTrue(CrmClient.class.getMethod("createOpportunity", CrmClientCommand.class, String.class,
                String.class, String.class, String.class, String.class, String.class, String.class).isDefault());
        assertTrue(CrmClient.class.getMethod("getOpportunity", String.class).isDefault());
        assertTrue(CrmClient.class.getMethod("listOpportunities", String.class, String.class,
                String.class, String.class, int.class).isDefault());
        assertTrue(CrmClient.class.getMethod("patchOpportunityStage", CrmClientCommand.class, String.class,
                String.class, String.class).isDefault());
        assertTrue(CrmClient.class.getMethod("patchOpportunityOwner", CrmClientCommand.class, String.class,
                String.class).isDefault());
        assertTrue(CrmClient.class.getMethod("createQuotation", CrmClientCommand.class, String.class,
                String.class, String.class, List.class).isDefault());
        log.info("商机和报价关联扩展保留默认方法，旧版客户端实现仍可加载");
    }
}
