package io.github.surezzzzzz.sdk.crm.client.test.cases;

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
    void completeConsumerListWithoutCursorRemainsCompatible() {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("items", Collections.emptyList());
        CrmPageView page = CrmResponseValidator.page(payload, "items", ARCHIVE, ARCHIVE);
        log.info("完整消费者列表兼容：行数={}，续页={}", page.getItems().size(), page.hasNext());
        assertTrue(page.getItems().isEmpty());
        assertFalse(page.hasNext());
        assertNull(page.getNextCursor());
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
}
