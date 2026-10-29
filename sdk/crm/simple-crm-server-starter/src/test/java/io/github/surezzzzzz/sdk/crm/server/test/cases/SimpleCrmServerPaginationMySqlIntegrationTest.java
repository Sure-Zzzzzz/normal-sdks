package io.github.surezzzzzz.sdk.crm.server.test.cases;

import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataConstraintOperator;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataConstraint;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataPermissionRequest;
import io.github.surezzzzzz.sdk.crm.server.core.command.*;
import io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.CommercialCapabilityType;
import io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.FulfillmentObligationTemplate;
import io.github.surezzzzzz.sdk.crm.server.core.domain.common.Money;
import io.github.surezzzzzz.sdk.crm.server.core.domain.contact.Contact;
import io.github.surezzzzzz.sdk.crm.server.core.domain.customer.Customer;
import io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentConsumer;
import io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentItem;
import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmOwner;
import io.github.surezzzzzz.sdk.crm.server.core.domain.offering.Offering;
import io.github.surezzzzzz.sdk.crm.server.core.domain.order.Order;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.Quotation;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationDraft;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationIssuance;
import io.github.surezzzzzz.sdk.crm.server.core.domain.type.CrmAction;
import io.github.surezzzzzz.sdk.crm.server.core.domain.type.CrmResourceType;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import io.github.surezzzzzz.sdk.crm.server.core.model.CrmListCriteria;
import io.github.surezzzzzz.sdk.crm.server.core.model.CrmPage;
import io.github.surezzzzzz.sdk.crm.server.core.model.CrmPageRequest;
import io.github.surezzzzzz.sdk.crm.server.repository.SimpleCrmServerJdbcAuditRepository;
import io.github.surezzzzzz.sdk.crm.server.repository.SimpleCrmServerJdbcFulfillmentConsumerRepository;
import io.github.surezzzzzz.sdk.crm.server.service.SimpleCrmServerCommandService;
import io.github.surezzzzzz.sdk.crm.server.service.SimpleCrmServerConsumerSelectorAdapter;
import io.github.surezzzzzz.sdk.crm.server.service.SimpleCrmServerQueryService;
import io.github.surezzzzzz.sdk.crm.server.test.SimpleCrmServerTestApplication;
import io.github.surezzzzzz.sdk.crm.server.test.support.SimpleCrmServerTestSchema;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 真实 MySQL 分页契约：全列表族、筛选与 DATA 总数、整批末页和查询范围绑定。
 * 每项测试使用独立归属及标记，业务写入由测试事务回滚，不改其他用例的存量事实。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleCrmServerTestApplication.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SimpleCrmServerPaginationMySqlIntegrationTest {

    @Autowired
    private SimpleCrmServerCommandService commands;
    @Autowired
    private SimpleCrmServerQueryService queries;
    @Autowired
    private SimpleCrmServerJdbcFulfillmentConsumerRepository consumers;
    @Autowired
    private SimpleCrmServerJdbcAuditRepository audits;
    @Autowired
    private DataSource dataSource;

    @BeforeAll
    void ensureSchema() throws Exception {
        SimpleCrmServerTestSchema.ensureExecuted(dataSource);
    }

    @Test
    @Transactional
    void consumerSelectionUsesRegistrationOrderAndSkipsDisabledOrDifferentCapabilities() {
        String marker = marker();
        String capability = marker + "-capability";
        FulfillmentConsumer disabled = new FulfillmentConsumer(marker + "-disabled", capability, 1, false);
        FulfillmentConsumer first = new FulfillmentConsumer(marker + "-z-first", capability, 1, true);
        FulfillmentConsumer second = new FulfillmentConsumer(marker + "-a-second", capability, 1, true);
        consumers.upsert(disabled);
        consumers.upsert(first);
        consumers.upsert(new FulfillmentConsumer(marker + "-other", marker + "-other-capability", 1, true));
        consumers.upsert(second);
        SimpleCrmServerConsumerSelectorAdapter selector = new SimpleCrmServerConsumerSelectorAdapter(consumers);
        FulfillmentObligationTemplate obligation = new FulfillmentObligationTemplate(
                marker + "-subject", marker + "-scope", capability);

        log.info("消费者选择：同能力多候选按注册顺序选择，排除禁用项及不同能力项");
        assertEquals(consumerKeys(java.util.Arrays.asList(first, second)),
                consumerKeys(consumers.findEnabled(capability)));
        assertEquals(first.getConsumerId(), selector.select(obligation).getConsumerId());
        // 更新已有行不改变注册顺序；禁用首项后选次项，再启用时恢复原顺序。
        consumers.upsert(new FulfillmentConsumer(first.getConsumerId(), capability, 2, true));
        assertEquals(first.getConsumerId(), selector.select(obligation).getConsumerId());
        assertEquals(2, selector.select(obligation).getProtocolVersion());
        consumers.upsert(new FulfillmentConsumer(first.getConsumerId(), capability, 2, false));
        assertEquals(second.getConsumerId(), selector.select(obligation).getConsumerId());
        consumers.upsert(new FulfillmentConsumer(first.getConsumerId(), capability, 2, true));
        assertEquals(first.getConsumerId(), selector.select(obligation).getConsumerId());
        consumers.upsert(new FulfillmentConsumer(first.getConsumerId(), capability, 2, false));
        consumers.upsert(new FulfillmentConsumer(second.getConsumerId(), capability, 1, false));
        assertTrue(consumers.findEnabled(capability).isEmpty());
        CrmException missing = assertThrows(CrmException.class, () -> selector.select(obligation));
        assertEquals(CrmErrorCode.INVALID_STATE_TRANSITION, missing.getErrorCode());
    }

    @Test
    @Transactional
    void everyResourceUsesTrueNumberedPagesAndExactMultipleCursorTermination() {
        String marker = marker();
        CrmOwner owner = CrmOwner.human(marker);
        List<Customer> customers = seed(owner, marker, 4);
        String customerId = customers.get(0).getCustomerId();
        for (int index = 0; index < 4; index++) {
            commands.createContact(owner, metadata(),
                    new CreateContactCommand(customerId, marker + "联系人" + index, null));
        }
        CrmListCriteria none = CrmListCriteria.none();
        CrmListCriteria contactCriteria = related(customerId);
        CrmListCriteria offeringsCriteria = keyword(marker);
        assertWindow("客户", request -> queries.listCustomers(owner, null, none, null, 2, request),
                cursor -> queries.listCustomers(owner, null, none, cursor, 2), Customer::getCustomerId);
        assertWindow("联系人", request -> queries.listContacts(owner, null, contactCriteria, null, 2, request),
                cursor -> queries.listContacts(owner, null, contactCriteria, cursor, 2), Contact::getContactId);
        assertWindow("商品", request -> queries.listOfferings(owner, offeringsCriteria, null, 2, request),
                cursor -> queries.listOfferings(owner, offeringsCriteria, cursor, 2), Offering::getOfferingId);
        assertWindow("报价", request -> queries.listQuotations(owner, null, none, null, 2, request),
                cursor -> queries.listQuotations(owner, null, none, cursor, 2), Quotation::getQuotationId);
        assertWindow("订单", request -> queries.listOrders(owner, null, none, null, 2, request),
                cursor -> queries.listOrders(owner, null, none, cursor, 2), Order::getOrderId);
        assertWindow("交付", request -> queries.listFulfillmentItems(owner, null, none, null, 2, request),
                cursor -> queries.listFulfillmentItems(owner, null, none, cursor, 2),
                FulfillmentItem::getFulfillmentId);

        String auditResource = marker + "-audit-resource";
        for (int index = 0; index < 4; index++) {
            audits.record(owner, CrmAction.CUSTOMER_READ, CrmResourceType.CUSTOMER,
                    auditResource, 1L, marker + "-audit-" + index, null, Instant.now());
        }
        CrmListCriteria auditCriteria = related(auditResource);
        assertWindow("审计", request -> queries.listAuditFacts(owner, auditCriteria, null, 2, request),
                cursor -> queries.listAuditFacts(owner, auditCriteria, cursor, 2),
                fact -> fact.getCorrelationId());

        // 消费者不按 owner 隔离，以完整列表对照真实页码和总数，不假设共享测试库原本为空。
        for (int index = 1; index < 4; index++) {
            consumers.upsert(new FulfillmentConsumer(marker + "-consumer-" + index,
                    marker + "-extra-capability", 1, true));
        }
        List<FulfillmentConsumer> all = commands.listFulfillmentConsumers();
        CrmPage<FulfillmentConsumer> second = commands.listFulfillmentConsumers(new CrmPageRequest(2, 2));
        CrmPage<FulfillmentConsumer> beyond = commands.listFulfillmentConsumers(
                new CrmPageRequest(all.size() / 2 + 2, 2));
        log.info("消费者页码：完整条数={}，第二页总数={}，越界行数={}",
                all.size(), second.getTotal(), beyond.getItems().size());
        assertEquals(Long.valueOf(all.size()), second.getTotal());
        assertEquals(consumerKeys(all.subList(2, 4)), consumerKeys(second.getItems()));
        assertTrue(beyond.getItems().isEmpty());
        assertEquals(Long.valueOf(all.size()), beyond.getTotal());
        assertFalse(beyond.hasNext());
    }

    @Test
    @Transactional
    void filteredCountsAndRowsRespectOwnAndGrantedOwnerScopes() {
        String marker = marker();
        CrmOwner owner = CrmOwner.human(marker + "-own");
        CrmOwner teammate = CrmOwner.human(marker + "-team");
        List<Customer> own = seed(owner, marker + "-own", 4);
        List<Customer> other = seed(teammate, marker + "-team", 1);
        DataAccessPlan teamPlan = grantOwner(teammate);
        commands.deactivateCustomer(owner, metadata(), new DeactivateCustomerCommand(own.get(0).getCustomerId()));
        CrmListCriteria active = new CrmListCriteria("active", null, null, null, marker);
        CrmPage<Customer> ownPage = queries.listCustomers(owner, null, active, null, 20,
                new CrmPageRequest(1, 20));
        CrmPage<Customer> teamPage = queries.listCustomers(owner, teamPlan, active, null, 20,
                new CrmPageRequest(1, 20));
        log.info("筛选和 DATA：本人有效客户总数={}，团队有效客户总数={}", ownPage.getTotal(), teamPage.getTotal());
        assertEquals(Long.valueOf(3), ownPage.getTotal());
        assertEquals(3, ownPage.getItems().size());
        assertTrue(ownPage.getItems().stream().allMatch(item -> owner.getOwner().equals(item.getOwner())));
        assertEquals(Long.valueOf(4), teamPage.getTotal());
        assertEquals(4, teamPage.getItems().size());
        assertTrue(teamPage.getItems().stream().anyMatch(item -> teammate.getOwner().equals(item.getOwner())));

        CrmListCriteria none = CrmListCriteria.none();
        assertScopedTotal(queries.listQuotations(owner, null, none, null, 20, new CrmPageRequest(1, 20)), 4);
        assertScopedTotal(queries.listQuotations(owner, teamPlan, none, null, 20, new CrmPageRequest(1, 20)), 5);
        assertScopedTotal(queries.listOrders(owner, null, none, null, 20, new CrmPageRequest(1, 20)), 4);
        assertScopedTotal(queries.listOrders(owner, teamPlan, none, null, 20, new CrmPageRequest(1, 20)), 5);
        assertScopedTotal(queries.listFulfillmentItems(owner, null, none, null, 20, new CrmPageRequest(1, 20)), 4);
        assertScopedTotal(queries.listFulfillmentItems(owner, teamPlan, none, null, 20, new CrmPageRequest(1, 20)), 5);

        Offering ownOffering = queries.listOfferings(owner, keyword(marker + "-own"), null, 1)
                .getItems().get(0);
        commands.disableOffering(owner, metadata(), new DisableOfferingCommand(ownOffering.getOfferingId()));
        CrmListCriteria published = new CrmListCriteria("published", null, null, null, marker);
        assertScopedTotal(queries.listOfferings(owner, published, null, 20, new CrmPageRequest(1, 20)), 4);
        assertScopedTotal(queries.listQuotations(owner, teamPlan,
                new CrmListCriteria("draft", null, null, null, marker), null, 20,
                new CrmPageRequest(1, 20)), 0);
        assertScopedTotal(queries.listQuotations(owner, teamPlan,
                new CrmListCriteria("confirmed", null, null, null, marker), null, 20,
                new CrmPageRequest(1, 20)), 5);

        FulfillmentItem reported = queries.listFulfillmentItems(owner, null, none, null, 1)
                .getItems().get(0);
        commands.reportFulfillmentResult(CrmOwner.service(reported.getConsumerId()), metadata(),
                new ReportFulfillmentResultCommand(reported.getFulfillmentId(), 1, "completed",
                        "pagination-result", null));
        CrmListCriteria pending = new CrmListCriteria("pending_dispatch", null, null, null);
        assertScopedTotal(queries.listFulfillmentItems(owner, null, pending, null, 20,
                new CrmPageRequest(1, 20)), 3);
        assertScopedTotal(queries.listFulfillmentItems(owner, teamPlan, pending, null, 20,
                new CrmPageRequest(1, 20)), 4);

        Customer otherCustomer = other.get(0);
        commands.createContact(teammate, metadata(), new CreateContactCommand(otherCustomer.getCustomerId(),
                marker + "-团队联系人", null));
        CrmException invisible = assertThrows(CrmException.class, () -> queries.listContacts(owner, null,
                related(otherCustomer.getCustomerId()), null, 20, new CrmPageRequest(1, 20)));
        assertEquals(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE, invisible.getErrorCode());
        assertScopedTotal(queries.listContacts(owner, teamPlan, related(otherCustomer.getCustomerId()),
                null, 20, new CrmPageRequest(1, 20)), 1);

        String orderId = queries.listOrders(owner, null, none, null, 1).getItems().get(0).getOrderId();
        assertScopedTotal(queries.listOrders(owner, teamPlan, keyword(orderId), null, 20,
                new CrmPageRequest(1, 20)), 1);
        assertScopedTotal(queries.listFulfillmentItems(owner, teamPlan, related(orderId), null, 20,
                new CrmPageRequest(1, 20)), 1);
        CrmListCriteria missingKeyword = keyword(marker + "-absent");
        assertScopedTotal(queries.listCustomers(owner, teamPlan, missingKeyword, null, 20,
                new CrmPageRequest(1, 20)), 0);
        assertScopedTotal(queries.listOfferings(owner, missingKeyword, null, 20,
                new CrmPageRequest(1, 20)), 0);
        assertScopedTotal(queries.listQuotations(owner, teamPlan, missingKeyword, null, 20,
                new CrmPageRequest(1, 20)), 0);
        assertScopedTotal(queries.listOrders(owner, teamPlan, missingKeyword, null, 20,
                new CrmPageRequest(1, 20)), 0);
        assertScopedTotal(queries.listAuditFacts(owner, related(marker + "-absent-audit-resource"), null, 20,
                new CrmPageRequest(1, 20)), 0);
    }

    @Test
    @Transactional
    void cursorCannotCrossFiltersIdentityResourceFamilyOrDataScope() {
        String marker = marker();
        CrmOwner owner = CrmOwner.human(marker + "-own");
        CrmOwner teammate = CrmOwner.human(marker + "-team");
        seed(owner, marker, 4);
        CrmListCriteria none = CrmListCriteria.none();
        String cursor = queries.listCustomers(owner, null, none, null, 2).getNextCursor();
        log.info("游标范围校验：筛选、主体、列表族和 DATA 变化均须拒绝旧查询游标");
        assertNotNull(cursor);
        assertInvalidCursor(() -> queries.listCustomers(owner, null, keyword(marker), cursor, 2));
        assertInvalidCursor(() -> queries.listCustomers(teammate, null, none, cursor, 2));
        assertInvalidCursor(() -> queries.listOfferings(owner, none, cursor, 2));
        assertInvalidCursor(() -> queries.listCustomers(owner, grantOwner(teammate), none, cursor, 2));
        assertInvalidCursor(() -> queries.listCustomers(owner, null, none, "not-a-query-cursor", 2));
    }

    private <T> void assertWindow(String family, Function<CrmPageRequest, CrmPage<T>> numbered,
                                  Function<String, CrmPage<T>> cursor, Function<T, String> key) {
        CrmPage<T> first = numbered.apply(new CrmPageRequest(1, 2));
        CrmPage<T> second = numbered.apply(new CrmPageRequest(2, 2));
        CrmPage<T> beyond = numbered.apply(new CrmPageRequest(3, 2));
        log.info("{}真实页码：总数={}，首页={}，第二页={}，越界={}", family,
                first.getTotal(), first.getItems().size(), second.getItems().size(), beyond.getItems().size());
        assertScopedTotal(first, 4);
        assertEquals(2, first.getItems().size());
        assertEquals(2, second.getItems().size());
        assertEquals(Long.valueOf(4), second.getTotal());
        assertEquals(Integer.valueOf(2), second.getPage());
        assertEquals(Integer.valueOf(2), second.getPageSize());
        assertTrue(first.hasNext());
        assertFalse(second.hasNext());
        assertTrue(beyond.getItems().isEmpty());
        assertEquals(Long.valueOf(4), beyond.getTotal());
        assertEquals(Integer.valueOf(3), beyond.getPage());
        assertFalse(beyond.hasNext());
        assertNull(first.getNextCursor());
        List<String> keys = new ArrayList<String>();
        keys.addAll(first.getItems().stream().map(key).collect(Collectors.toList()));
        keys.addAll(second.getItems().stream().map(key).collect(Collectors.toList()));
        assertEquals(4, new HashSet<String>(keys).size(), "页码前后两页不能重复或漏行");

        CrmPage<T> cursorFirst = cursor.apply(null);
        assertNotNull(cursorFirst.getNextCursor());
        CrmPage<T> cursorLast = cursor.apply(cursorFirst.getNextCursor());
        assertEquals(2, cursorLast.getItems().size());
        assertNull(cursorLast.getNextCursor(), "整批末页不得多返回一次空页游标");
        assertEquals(first.getItems().stream().map(key).collect(Collectors.toList()),
                cursorFirst.getItems().stream().map(key).collect(Collectors.toList()));
        assertEquals(second.getItems().stream().map(key).collect(Collectors.toList()),
                cursorLast.getItems().stream().map(key).collect(Collectors.toList()));
    }

    private void assertScopedTotal(CrmPage<?> page, long expected) {
        assertEquals(Long.valueOf(expected), page.getTotal());
        assertEquals((int) Math.min(expected, page.getPageSize()), page.getItems().size());
    }

    private void assertInvalidCursor(Runnable request) {
        CrmException failure = assertThrows(CrmException.class, request::run);
        assertEquals(CrmErrorCode.VALIDATION_FAILED, failure.getErrorCode());
    }

    private List<Customer> seed(CrmOwner owner, String marker, int count) {
        String capability = marker + "-capability";
        consumers.upsert(new FulfillmentConsumer(marker + "-consumer", capability, 1, true));
        List<Customer> customers = new ArrayList<Customer>();
        for (int index = 0; index < count; index++) {
            Customer customer = commands.createCustomer(owner, metadata(),
                    new CreateCustomerCommand(marker + "客户" + index));
            customers.add(customer);
            Offering offering = commands.createOffering(owner, metadata(), new CreateOfferingCommand(
                    marker + "-ref-" + index, marker + "商品" + index,
                    CommercialCapabilityType.FIXED_PRICE_FULFILLMENT_V1, capability));
            commands.publishOffering(owner, metadata(), new PublishOfferingCommand(offering.getOfferingId()));
            QuotationLineCommand line = new QuotationLineCommand(offering.getOfferingId(), BigDecimal.ONE, "SET",
                    new Money(BigDecimal.TEN, "CNY"), marker + "-subject", marker + "-scope");
            QuotationDraft draft = commands.createQuotation(owner, metadata(), new CreateQuotationCommand(
                    customer.getCustomerId(), Instant.now().plusSeconds(3600), Collections.singletonList(line)));
            QuotationIssuance issued = commands.issueQuotation(owner, metadata(), new IssueQuotationCommand(
                    draft.getQuotation().getQuotationId(), 1, draft.getQuotation().getAggregateVersion()));
            commands.confirmQuotation(owner, metadata(), new ConfirmQuotationCommand(
                    draft.getQuotation().getQuotationId(), 1, issued.getQuotation().getAggregateVersion()));
        }
        return customers;
    }

    private DataAccessPlan grantOwner(CrmOwner teammate) {
        DataConstraint constraint = new DataConstraint("owner", DataConstraintOperator.IN,
                Collections.singletonList(teammate.getOwner()));
        DataGrant grant = new DataGrant("crm-customer", Collections.singletonList("read"), false,
                Collections.singletonList(constraint));
        return DataAccessPlan.evaluate(new DataGrantDocument("simple-data-permission", "1.0",
                Collections.singletonList(grant)), new DataPermissionRequest("crm-customer", "read"));
    }

    private CrmListCriteria keyword(String value) {
        return new CrmListCriteria(null, null, null, null, value);
    }

    private CrmListCriteria related(String value) {
        return new CrmListCriteria(null, value, null, null);
    }

    private List<String> consumerKeys(List<FulfillmentConsumer> items) {
        return items.stream().map(item -> item.getConsumerId() + ":" + item.getCapability())
                .collect(Collectors.toList());
    }

    private CrmCommandMetadata metadata() {
        return new CrmCommandMetadata(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    }

    private String marker() {
        return "page-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
