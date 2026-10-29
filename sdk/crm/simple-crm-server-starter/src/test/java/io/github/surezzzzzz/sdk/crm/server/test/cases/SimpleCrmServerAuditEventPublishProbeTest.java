package io.github.surezzzzzz.sdk.crm.server.test.cases;

import io.github.surezzzzzz.sdk.crm.server.core.command.CreateCustomerCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.CrmCommandMetadata;
import io.github.surezzzzzz.sdk.crm.server.core.domain.customer.Customer;
import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmOwner;
import io.github.surezzzzzz.sdk.crm.server.core.domain.event.CrmAuditEvent;
import io.github.surezzzzzz.sdk.crm.server.service.SimpleCrmServerCommandService;
import io.github.surezzzzzz.sdk.crm.server.test.SimpleCrmServerTestApplication;
import io.github.surezzzzzz.sdk.crm.server.test.support.SimpleCrmServerTestSchema;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 审计事件发布探针：提交后必发一条与落库事实一致的事件；回滚不发。
 * 探针方式：程序化挂事件捕获器 + TransactionTemplate 分别走提交/回滚两条路径，
 * 逐字段断言事件即已提交审计行的投影。
 */
@Slf4j
@SpringBootTest(classes = SimpleCrmServerTestApplication.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SimpleCrmServerAuditEventPublishProbeTest {

    @Autowired
    private SimpleCrmServerCommandService commandService;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ConfigurableApplicationContext applicationContext;

    private final List<CrmAuditEvent> captured = new CopyOnWriteArrayList<>();

    private final CrmOwner owner = CrmOwner.human("audit-probe-owner");

    private final AtomicInteger seq = new AtomicInteger();

    @BeforeAll
    void schema() throws Exception {
        SimpleCrmServerTestSchema.ensureExecuted(dataSource);
        applicationContext.addApplicationListener((ApplicationListener<ApplicationEvent>) event -> {
            Object payload = event instanceof PayloadApplicationEvent
                    ? ((PayloadApplicationEvent<?>) event).getPayload() : event;
            if (payload instanceof CrmAuditEvent) {
                captured.add((CrmAuditEvent) payload);
            }
        });
    }

    @Test
    void committedCommandPublishesExactlyOneAuditEventMatchingFact() {
        int before = captured.size();
        Customer customer = transactionTemplate.execute(status -> commandService.createCustomer(
                owner, meta(), new CreateCustomerCommand("审计事件探针客户")));
        assertTrue(customer != null, "提交路径必须产生客户事实");
        assertEquals(before + 1, captured.size(), "提交后必须恰好发布一条审计事件");
        CrmAuditEvent event = captured.get(captured.size() - 1);
        log.info("验证审计事件投影：action={}，resourceType={}，resourceId={}", event.getActionCode(),
                event.getResourceTypeCode(), event.getResourceId());
        assertEquals(owner.getOwner(), event.getOwner(), "事件必须携带归属坐标");
        assertEquals("customer_create", event.getActionCode(), "事件动作码与审计行动作一致");
        assertEquals("customer", event.getResourceTypeCode(), "事件资源类型码与审计资源一致");
        assertEquals(customer.getCustomerId(), event.getResourceId(), "事件目标即新建客户");
        assertEquals(customer.getAggregateVersion(), event.getAggregateVersion(), "事件版本即聚合版本");
        assertNull(event.getResultCode(), "成功审计不携带结果码");
        assertEquals(CrmAuditEvent.CURRENT_EVENT_VERSION, event.getEventVersion(), "事件版本恒为当前契约");
    }

    @Test
    void rolledBackCommandPublishesNoAuditEvent() {
        int before = captured.size();
        long rowsBefore = count("SELECT COUNT(*) FROM crm_customer");
        transactionTemplate.execute(status -> {
            commandService.createCustomer(owner, meta(), new CreateCustomerCommand("回滚探针客户"));
            status.setRollbackOnly();
            return null;
        });
        long rowsAfter = count("SELECT COUNT(*) FROM crm_customer");
        assertEquals(rowsBefore, rowsAfter, "回滚后客户零残留（事务原子性）");
        assertEquals(before, captured.size(), "回滚事务不得发布审计事件（无幻影审计）");
    }

    private long count(String sql) {
        try (java.sql.Connection connection = dataSource.getConnection();
             java.sql.Statement statement = connection.createStatement();
             java.sql.ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private CrmCommandMetadata meta() {
        seq.incrementAndGet();
        return new CrmCommandMetadata("audit-probe-" + seq.get(), "ap-" + seq.get());
    }
}
