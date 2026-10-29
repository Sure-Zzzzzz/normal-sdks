package io.github.surezzzzzz.sdk.crm.server.test.cases;

import io.github.surezzzzzz.sdk.crm.server.core.command.CreateCustomerCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.CreateOfferingCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.CreateQuotationCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.CrmCommandMetadata;
import io.github.surezzzzzz.sdk.crm.server.core.command.PublishOfferingCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.QuotationLineCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.TransferQuotationCommand;
import io.github.surezzzzzz.sdk.crm.server.core.domain.common.Money;
import io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.CommercialCapabilityType;
import io.github.surezzzzzz.sdk.crm.server.core.domain.customer.Customer;
import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmOwner;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.Quotation;
import io.github.surezzzzzz.sdk.crm.server.core.domain.type.CrmAction;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import io.github.surezzzzzz.sdk.crm.server.repository.SimpleCrmServerJdbcCustomerRepository;
import io.github.surezzzzzz.sdk.crm.server.repository.SimpleCrmServerJdbcQuotationRepository;
import io.github.surezzzzzz.sdk.crm.server.service.SimpleCrmServerCommandService;
import io.github.surezzzzzz.sdk.crm.server.test.SimpleCrmServerTestApplication;
import io.github.surezzzzzz.sdk.crm.server.test.support.SimpleCrmServerTestSchema;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 MySQL 并发回归：客户转移读到的旧报价不能覆盖已提交的报价转移。
 *
 * @author surezzzzzz
 */
@SpringBootTest(classes = SimpleCrmServerTestApplication.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SimpleCrmServerQuotationOwnerCasMySqlIntegrationTest {

    @Autowired
    private SimpleCrmServerCommandService commandService;

    @Autowired
    private SimpleCrmServerJdbcCustomerRepository customerRepository;

    @Autowired
    private SimpleCrmServerJdbcQuotationRepository quotationRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    @BeforeAll
    void ensureSchema() throws Exception {
        SimpleCrmServerTestSchema.ensureExecuted(dataSource);
        jdbc = new JdbcTemplate(dataSource);
    }

    /**
     * 旧快照的客户转移必须整体回滚，保留并发报价转移的归属和唯一审计。
     *
     * @throws Exception 并发执行失败
     */
    @Test
    void staleCustomerTransferCannotOverwriteQuotationOwner() throws Exception {
        CrmOwner originalOwner = CrmOwner.human("owner-cas-original");
        CrmOwner customerDestination = CrmOwner.human("owner-cas-customer");
        CrmOwner quotationDestination = CrmOwner.human("owner-cas-quotation");
        String suffix = UUID.randomUUID().toString();
        Customer customer = commandService.createCustomer(originalOwner, metadata(),
                new CreateCustomerCommand("CAS 并发客户"));
        String offeringId = commandService.createOffering(originalOwner, metadata(),
                new CreateOfferingCommand("owner-cas-" + suffix, "CAS 并发商品",
                        CommercialCapabilityType.FIXED_PRICE_FULFILLMENT_V1,
                        "owner-cas-capability")).getOfferingId();
        commandService.publishOffering(originalOwner, metadata(),
                new PublishOfferingCommand(offeringId));
        Quotation quotation = commandService.createQuotationResource(originalOwner, metadata(),
                new CreateQuotationCommand(customer.getCustomerId(), Instant.now().plusSeconds(86400),
                        Collections.singletonList(new QuotationLineCommand(offeringId, BigDecimal.ONE,
                                "SET", new Money(new BigDecimal("19.90"), "CNY"),
                                "owner-cas-subject", "owner-cas-scope"))));

        CountDownLatch staleRead = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Void> customerTransfer = executor.submit(() -> {
                TransactionTemplate transaction = new TransactionTemplate(transactionManager);
                transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
                return transaction.execute(status -> {
                    Customer currentCustomer = customerRepository.findById(customer.getCustomerId()).get();
                    Quotation staleQuotation = quotationRepository.findOpenByCustomer(
                            customer.getCustomerId()).get(0);
                    customerRepository.update(currentCustomer.transferredTo(
                            customerDestination.getOwner(), Instant.now()),
                            currentCustomer.getAggregateVersion());
                    staleRead.countDown();
                    try {
                        if (!release.await(20, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("并发测试会合超时");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("并发测试已中断", e);
                    }
                    quotationRepository.updateOwner(staleQuotation, customerDestination.getOwner());
                    return null;
                });
            });
            assertTrue(staleRead.await(20, TimeUnit.SECONDS), "客户转移必须先读取旧报价");
            commandService.transferQuotation(originalOwner, metadata(),
                    new TransferQuotationCommand(quotation.getQuotationId(),
                            quotationDestination.getOwner()));
            release.countDown();

            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> customerTransfer.get(30, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof CrmException);
            assertEquals(CrmErrorCode.AGGREGATE_VERSION_CONFLICT,
                    ((CrmException) failure.getCause()).getErrorCode());
            assertEquals(originalOwner.getOwner(), jdbc.queryForObject(
                    "SELECT owner FROM crm_customer WHERE customer_id = ?", String.class,
                    customer.getCustomerId()), "客户转移事务必须整体回滚");
            assertEquals(quotationDestination.getOwner(), jdbc.queryForObject(
                    "SELECT owner FROM crm_quotation WHERE quotation_id = ?", String.class,
                    quotation.getQuotationId()), "已提交的报价转移不得被旧快照覆盖");
            assertEquals(1L, auditCount(quotation.getQuotationId(), CrmAction.QUOTATION_TRANSFER));
            assertEquals(0L, auditCount(customer.getCustomerId(), CrmAction.CUSTOMER_TRANSFER));
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "测试工作线程必须退出");
        }
    }

    private long auditCount(String resourceId, CrmAction action) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM crm_audit WHERE target_resource_id = ?"
                        + " AND action = ?", Long.class, resourceId, action.getCode());
    }

    private CrmCommandMetadata metadata() {
        String suffix = UUID.randomUUID().toString();
        return new CrmCommandMetadata("owner-cas-" + suffix, "idem-" + suffix);
    }
}
