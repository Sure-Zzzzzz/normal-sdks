package io.github.surezzzzzz.sdk.crm.server.test.cases;

import io.github.surezzzzzz.sdk.crm.server.core.command.ConfirmQuotationCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.CreateCustomerCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.CreateOfferingCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.CreateQuotationCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.CrmCommandMetadata;
import io.github.surezzzzzz.sdk.crm.server.core.command.EditQuotationCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.IssueQuotationCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.PublishOfferingCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.QuotationLineCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.ReportFulfillmentResultCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.ResetFulfillmentItemCommand;
import io.github.surezzzzzz.sdk.crm.server.core.domain.common.Money;
import io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.CommercialCapabilityType;
import io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentConsumer;
import io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentItem;
import io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentState;
import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmOwner;
import io.github.surezzzzzz.sdk.crm.server.core.domain.order.Order;
import io.github.surezzzzzz.sdk.crm.server.core.domain.order.OrderDisplayState;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationConfirmation;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationDraft;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationIssuance;
import io.github.surezzzzzz.sdk.crm.server.core.domain.type.CrmAction;
import io.github.surezzzzzz.sdk.crm.server.repository.SimpleCrmServerJdbcFulfillmentConsumerRepository;
import io.github.surezzzzzz.sdk.crm.server.service.SimpleCrmServerCommandService;
import io.github.surezzzzzz.sdk.crm.server.service.SimpleCrmServerQueryService;
import io.github.surezzzzzz.sdk.crm.server.test.SimpleCrmServerTestApplication;
import io.github.surezzzzzz.sdk.crm.server.test.support.SimpleCrmServerTestSchema;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 MySQL 履约并发回归：独立事务验证整单展示态、当前幂等读和重放无重复审计。
 *
 * @author surezzzzzz
 */
@SpringBootTest(classes = SimpleCrmServerTestApplication.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SimpleCrmServerFulfillmentConcurrencyMySqlIntegrationTest {

    @Autowired
    private SimpleCrmServerCommandService commandService;

    @Autowired
    private SimpleCrmServerQueryService queryService;

    @Autowired
    private SimpleCrmServerJdbcFulfillmentConsumerRepository consumerRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DataSource dataSource;

    private final CrmOwner owner = CrmOwner.human("mysql-concurrency-owner");
    private final CrmOwner consumer = CrmOwner.service("consumer-concurrency");
    private JdbcTemplate jdbc;

    /**
     * 仅在专用测试数据源执行测试 schema，并登记本类独立消费者。
     *
     * @throws Exception SQL 执行失败
     */
    @BeforeAll
    void ensureSchema() throws Exception {
        SimpleCrmServerTestSchema.ensureExecuted(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        consumerRepository.upsert(new FulfillmentConsumer("consumer-concurrency",
                "capability-concurrency", 1, true));
    }

    /**
     * 两个事务先建立旧快照，再同时完成同单不同交付；锁后必须读取已提交的整单当前事实。
     *
     * @throws Exception 并发执行或断言失败
     */
    @Test
    void completingDifferentItemsWithExistingSnapshotsFulfillsWholeOrder() throws Exception {
        QuotationConfirmation confirmation = createOrder(2);
        FulfillmentItem first = confirmation.getFulfillmentItems().get(0);
        FulfillmentItem second = confirmation.getFulfillmentItems().get(1);
        CrmCommandMetadata firstMetadata = metadata();
        CrmCommandMetadata secondMetadata = metadata();

        List<FulfillmentItem> completed = runWithExistingSnapshots(confirmation.getOrder().getOrderId(),
                () -> commandService.reportFulfillmentResult(consumer, firstMetadata,
                        completedCommand(first)),
                () -> commandService.reportFulfillmentResult(consumer, secondMetadata,
                        completedCommand(second)));

        assertEquals(FulfillmentState.COMPLETED, completed.get(0).getState());
        assertEquals(FulfillmentState.COMPLETED, completed.get(1).getState());
        Order order = queryService.findOrder(owner, null, confirmation.getOrder().getOrderId());
        assertEquals(OrderDisplayState.FULFILLED, order.getDisplayState(),
                "两个旧快照不能把整单状态覆盖回部分履约");
        assertEquals(confirmation.getOrder().getOrderVersion() + 2, order.getOrderVersion(),
                "仅待履约→部分履约→已履约两次派生态变化增加版本");
        assertEquals(1L, auditCount(first.getFulfillmentId(), CrmAction.FULFILLMENT_REPORT));
        assertEquals(1L, auditCount(second.getFulfillmentId(), CrmAction.FULFILLMENT_REPORT));
    }

    /**
     * 同项同键并发重放不因等待锁前的旧快照重复推进，也不报水位冲突或重复审计。
     *
     * @throws Exception 并发执行或断言失败
     */
    @Test
    void concurrentSameCommandReplaysAfterOrderLock() throws Exception {
        QuotationConfirmation confirmation = createOrder(1);
        FulfillmentItem item = confirmation.getFulfillmentItems().get(0);
        CrmCommandMetadata metadata = metadata();
        ReportFulfillmentResultCommand command = completedCommand(item);

        List<FulfillmentItem> completed = runWithExistingSnapshots(confirmation.getOrder().getOrderId(),
                () -> commandService.reportFulfillmentResult(consumer, metadata, command),
                () -> commandService.reportFulfillmentResult(consumer, metadata, command));

        assertEquals(item.getVersion() + 1, completed.get(0).getVersion());
        assertEquals(completed.get(0).getVersion(), completed.get(1).getVersion());
        assertEquals(1L, auditCount(item.getFulfillmentId(), CrmAction.FULFILLMENT_REPORT));
        Order order = queryService.findOrder(owner, null, confirmation.getOrder().getOrderId());
        assertEquals(OrderDisplayState.FULFILLED, order.getDisplayState());
        assertEquals(confirmation.getOrder().getOrderVersion() + 1, order.getOrderVersion());
    }

    /**
     * 回写重放不修复旧派生态；首响应、交付事实、订单版本与审计均保持不变。
     */
    @Test
    void reportReplayDoesNotRepairStaleOrderDisplayOrRepeatBusinessFacts() {
        QuotationConfirmation confirmation = createOrder(1);
        FulfillmentItem item = confirmation.getFulfillmentItems().get(0);
        CrmCommandMetadata metadata = metadata();
        ReportFulfillmentResultCommand command = completedCommand(item);
        FulfillmentItem first = commandService.reportFulfillmentResult(consumer, metadata, command);
        Instant recordedAt = queryService.findFulfillmentItem(consumer, null, item.getFulfillmentId())
                .getResult().getReportedAt();
        long orderVersion = queryService.findOrder(owner, null, item.getOrderId()).getOrderVersion();
        // 仅专用测试库制造历史派生态偏差，不改变交付事实或版本。
        assertEquals(1, jdbc.update("UPDATE crm_order SET display_state = ? WHERE order_id = ?",
                OrderDisplayState.PENDING_FULFILLMENT.getCode(), item.getOrderId()));

        FulfillmentItem replay = commandService.reportFulfillmentResult(consumer, metadata, command);
        assertEquals(first.getVersion(), replay.getVersion());
        assertEquals(first.getReportSequence(), replay.getReportSequence());
        assertEquals(recordedAt, replay.getResult().getReportedAt());
        assertEquals(1L, auditCount(item.getFulfillmentId(), CrmAction.FULFILLMENT_REPORT));
        Order unchanged = queryService.findOrder(owner, null, item.getOrderId());
        assertEquals(OrderDisplayState.PENDING_FULFILLMENT, unchanged.getDisplayState());
        assertEquals(orderVersion, unchanged.getOrderVersion());
        commandService.reportFulfillmentResult(consumer, metadata, command);
        assertEquals(unchanged.getOrderVersion(),
                queryService.findOrder(owner, null, item.getOrderId()).getOrderVersion(),
                "重放不得以修复派生态为由增加订单版本");
    }

    /**
     * 重置重放保持原水位和单次重置审计，不隐式修复派生态。
     */
    @Test
    void resetReplayPreservesWatermarkWithoutRepairingStaleDisplay() {
        QuotationConfirmation confirmation = createOrder(1);
        FulfillmentItem item = confirmation.getFulfillmentItems().get(0);
        commandService.reportFulfillmentResult(consumer, metadata(),
                new ReportFulfillmentResultCommand(item.getFulfillmentId(), 1, "failed",
                        "consumer-failed", null));
        CrmCommandMetadata metadata = metadata();
        ResetFulfillmentItemCommand command = new ResetFulfillmentItemCommand(item.getFulfillmentId());
        FulfillmentItem first = commandService.resetFulfillmentItem(owner, metadata, command);
        assertEquals(1, jdbc.update("UPDATE crm_order SET display_state = ? WHERE order_id = ?",
                OrderDisplayState.FULFILLED.getCode(), item.getOrderId()));

        FulfillmentItem replay = commandService.resetFulfillmentItem(owner, metadata, command);
        assertEquals(FulfillmentState.PENDING_DISPATCH, replay.getState());
        assertEquals(first.getVersion(), replay.getVersion());
        assertEquals(1, replay.getReportSequence());
        assertEquals(1L, auditCount(item.getFulfillmentId(), CrmAction.FULFILLMENT_RESET));
        assertEquals(OrderDisplayState.FULFILLED,
                queryService.findOrder(owner, null, item.getOrderId()).getDisplayState());
    }

    /**
     * 直接执行交付的修复 SQL：只修指定差异，不改正常订单与范围外订单，重复执行不加版本。
     *
     * @throws Exception SQL 读取或执行失败
     */
    @Test
    void repairScriptOnlyChangesSpecifiedStaleDisplayAndIsRepeatable() throws Exception {
        QuotationConfirmation stale = createOrder(1);
        FulfillmentItem item = stale.getFulfillmentItems().get(0);
        commandService.reportFulfillmentResult(consumer, metadata(), completedCommand(item));
        QuotationConfirmation correct = createOrder(1);
        QuotationConfirmation outside = createOrder(1);
        long staleVersion = queryService.findOrder(owner, null, item.getOrderId()).getOrderVersion();
        jdbc.update("UPDATE crm_order SET display_state = ? WHERE order_id = ?",
                OrderDisplayState.PENDING_FULFILLMENT.getCode(), item.getOrderId());
        jdbc.update("UPDATE crm_order SET display_state = ? WHERE order_id = ?",
                OrderDisplayState.FULFILLED.getCode(), outside.getOrder().getOrderId());

        assertEquals(1L, executeRepairScript(item.getOrderId(), correct.getOrder().getOrderId()));
        Order repaired = queryService.findOrder(owner, null, item.getOrderId());
        assertEquals(OrderDisplayState.FULFILLED, repaired.getDisplayState());
        assertEquals(staleVersion + 1, repaired.getOrderVersion());
        assertEquals(correct.getOrder().getOrderVersion(),
                queryService.findOrder(owner, null, correct.getOrder().getOrderId()).getOrderVersion());
        Order untouched = queryService.findOrder(owner, null, outside.getOrder().getOrderId());
        assertEquals(OrderDisplayState.FULFILLED, untouched.getDisplayState(), "范围外偏差不自动修复");
        assertEquals(outside.getOrder().getOrderVersion(), untouched.getOrderVersion());
        assertEquals(0L, executeRepairScript(item.getOrderId(), correct.getOrder().getOrderId()));
        assertEquals(repaired.getOrderVersion(),
                queryService.findOrder(owner, null, item.getOrderId()).getOrderVersion());
        assertEquals(1L, auditCount(item.getFulfillmentId(), CrmAction.FULFILLMENT_REPORT),
                "运维修复不伪造额外履约审计");
    }

    /**
     * 范围中任一订单不存在时禁止部分修复；已关闭与变更中状态不被履约派生态覆盖。
     *
     * @throws Exception SQL 读取或执行失败
     */
    @Test
    void repairScriptRejectsIncompleteScopeAndPreservesOtherBusinessStates() throws Exception {
        QuotationConfirmation stale = createOrder(1);
        FulfillmentItem item = stale.getFulfillmentItems().get(0);
        commandService.reportFulfillmentResult(consumer, metadata(), completedCommand(item));
        long version = queryService.findOrder(owner, null, item.getOrderId()).getOrderVersion();
        jdbc.update("UPDATE crm_order SET display_state = ? WHERE order_id = ?",
                OrderDisplayState.PENDING_FULFILLMENT.getCode(), item.getOrderId());
        assertEquals(0L, executeRepairScript(item.getOrderId(), "missing-" + UUID.randomUUID()));
        Order rejected = queryService.findOrder(owner, null, item.getOrderId());
        assertEquals(OrderDisplayState.PENDING_FULFILLMENT, rejected.getDisplayState());
        assertEquals(version, rejected.getOrderVersion());

        QuotationConfirmation changing = createOrder(1);
        jdbc.update("UPDATE crm_order SET display_state = ? WHERE order_id = ?",
                OrderDisplayState.CLOSED.getCode(), item.getOrderId());
        jdbc.update("UPDATE crm_order SET display_state = ? WHERE order_id = ?",
                OrderDisplayState.CHANGE_IN_PROGRESS.getCode(), changing.getOrder().getOrderId());
        assertEquals(0L, executeRepairScript(item.getOrderId(), changing.getOrder().getOrderId()));
        assertEquals(OrderDisplayState.CLOSED,
                queryService.findOrder(owner, null, item.getOrderId()).getDisplayState());
        assertEquals(version, queryService.findOrder(owner, null, item.getOrderId()).getOrderVersion());
        assertEquals(OrderDisplayState.CHANGE_IN_PROGRESS,
                queryService.findOrder(owner, null, changing.getOrder().getOrderId()).getDisplayState());
    }

    /**
     * 两个事务在相同旧快照上会合；业务命令必须通过当前读获取锁后最新事实。
     */
    private List<FulfillmentItem> runWithExistingSnapshots(String orderId,
            Supplier<FulfillmentItem> first, Supplier<FulfillmentItem> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<FulfillmentItem> firstFuture = executor.submit(
                    transactionTask(orderId, first, ready, start));
            Future<FulfillmentItem> secondFuture = executor.submit(
                    transactionTask(orderId, second, ready, start));
            assertTrue(ready.await(20, TimeUnit.SECONDS), "两事务必须都建立快照后再执行");
            start.countDown();
            List<FulfillmentItem> result = new ArrayList<FulfillmentItem>();
            result.add(firstFuture.get(30, TimeUnit.SECONDS));
            result.add(secondFuture.get(30, TimeUnit.SECONDS));
            return result;
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "测试工作线程必须退出");
        }
    }

    private Callable<FulfillmentItem> transactionTask(String orderId,
            Supplier<FulfillmentItem> action, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
            return transaction.execute(status -> {
                jdbc.queryForList("SELECT state FROM crm_fulfillment_item WHERE order_id = ?", orderId);
                ready.countDown();
                try {
                    if (!start.await(20, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("并发测试会合超时");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("并发测试已中断", e);
                }
                return action.get();
            });
        };
    }

    private QuotationConfirmation createOrder(int itemCount) {
        String suffix = UUID.randomUUID().toString();
        String customerId = commandService.createCustomer(owner, metadata(),
                new CreateCustomerCommand("并发测试客户")).getCustomerId();
        String offeringId = commandService.createOffering(owner, metadata(),
                new CreateOfferingCommand("concurrency-" + suffix, "并发测试商品",
                        CommercialCapabilityType.FIXED_PRICE_FULFILLMENT_V1,
                        "capability-concurrency")).getOfferingId();
        commandService.publishOffering(owner, metadata(), new PublishOfferingCommand(offeringId));
        List<QuotationLineCommand> lines = new ArrayList<QuotationLineCommand>();
        for (int index = 0; index < itemCount; index++) {
            lines.add(new QuotationLineCommand(offeringId, BigDecimal.ONE, "SET",
                    new Money(new BigDecimal("19.90"), "CNY"), "subject-" + index, "scope-" + index));
        }
        QuotationDraft draft = commandService.createQuotation(owner, metadata(),
                new CreateQuotationCommand(customerId, Instant.now().plusSeconds(86400), lines));
        assertEquals(itemCount, draft.getQuotationVersion().getLines().size(),
                "同一商品的多条业务行必须全部保留");
        if (itemCount > 1) {
            draft = commandService.editQuotationDraft(owner, metadata(),
                    new EditQuotationCommand(draft.getQuotation().getQuotationId(),
                            Instant.now().plusSeconds(86400), lines));
            assertEquals(itemCount, draft.getQuotationVersion().getLines().size(),
                    "编辑同商品多行也不得合并或拒绝业务行");
        }
        QuotationIssuance issued = commandService.issueQuotation(owner, metadata(),
                new IssueQuotationCommand(draft.getQuotation().getQuotationId(), 1,
                        draft.getQuotation().getAggregateVersion()));
        return commandService.confirmQuotation(owner, metadata(),
                new ConfirmQuotationCommand(issued.getQuotation().getQuotationId(), 1,
                        issued.getQuotation().getAggregateVersion()));
    }

    private ReportFulfillmentResultCommand completedCommand(FulfillmentItem item) {
        return new ReportFulfillmentResultCommand(item.getFulfillmentId(), 1, "completed",
                "result-" + item.getFulfillmentId(), null);
    }

    private long auditCount(String fulfillmentId, CrmAction action) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM crm_audit WHERE target_resource_id = ?"
                + " AND action = ?", Long.class, fulfillmentId, action.getCode());
    }

    private CrmCommandMetadata metadata() {
        String suffix = UUID.randomUUID().toString();
        return new CrmCommandMetadata("concurrency-" + suffix, "idem-" + suffix);
    }

    /**
     * 使用受控测试生成的订单标识执行原始交付脚本，读取本连接的修复前范围事实。
     */
    private long executeRepairScript(String firstOrderId, String secondOrderId) throws Exception {
        String script = new String(Files.readAllBytes(Paths.get("docs", "migration",
                "order-display-state.repair.sql")), StandardCharsets.UTF_8)
                .replace("order-example-001", firstOrderId)
                .replace("order-example-002", secondOrderId);
        try (Connection connection = dataSource.getConnection()) {
            JdbcTemplate session = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            try {
                ScriptUtils.executeSqlScript(connection, new EncodedResource(
                        new ByteArrayResource(script.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
                assertEquals(2L, session.queryForObject("SELECT @crm_display_repair_expected", Long.class));
                return session.queryForObject("SELECT @crm_display_repaired_orders", Long.class);
            } finally {
                // 脚本失败也释放事务锁与会话临时表，避免污染测试连接池。
                session.execute("ROLLBACK");
                session.execute("DROP TEMPORARY TABLE IF EXISTS crm_order_display_repair_facts");
                session.execute("DROP TEMPORARY TABLE IF EXISTS crm_order_display_repair_scope");
            }
        }
    }
}
