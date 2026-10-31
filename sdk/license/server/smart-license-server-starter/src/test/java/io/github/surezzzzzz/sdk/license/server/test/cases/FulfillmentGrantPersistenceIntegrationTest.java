package io.github.surezzzzzz.sdk.license.server.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;

import io.github.surezzzzzz.sdk.license.server.constant.FulfillmentConstant;
import io.github.surezzzzzz.sdk.license.server.entity.GrantRevisionEntity;
import io.github.surezzzzzz.sdk.license.server.integration.FulfillmentGrantCreationService;
import io.github.surezzzzzz.sdk.license.server.repository.FulfillmentGrantRepository;
import io.github.surezzzzzz.sdk.license.server.repository.GrantRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 真实 MySQL 履约并发断言：两实例同读空状态后同时建立，必须只提交一份草稿与台账。
 * 使用既有持久化测试上下文与数据库；每例仅清理自己的随机业务编号。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = PersistenceTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class FulfillmentGrantPersistenceIntegrationTest {

    @Autowired
    private GrantRepository grants;
    @Autowired
    private FulfillmentGrantRepository associations;
    @Autowired
    private PlatformTransactionManager transactions;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void twoIndependentCreatorsCommitExactlyOneDraftAndNoOrphan() throws Exception {
        String fulfillmentId = "ff-test-" + UUID.randomUUID();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch bothReadEmpty = new CountDownLatch(2);
        GrantRepository racing = Mockito.mock(GrantRepository.class, AdditionalAnswers.delegatesTo(grants));
        Mockito.doAnswer(call -> {
            List<GrantRevisionEntity> result = grants.findByBusinessOrderRef(call.getArgument(0));
            // 强制两个独立事务都看到空集，确保真实触发台账唯一冲突而不是串行命中已有授权。
            bothReadEmpty.countDown();
            if (!bothReadEmpty.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("并发事务未同时到达创建前置状态");
            }
            return result;
        }).when(racing).findByBusinessOrderRef(anyString());
        FulfillmentGrantCreationService first = new FulfillmentGrantCreationService(racing, associations, transactions);
        FulfillmentGrantCreationService second = new FulfillmentGrantCreationService(racing, associations, transactions);
        try {
            Future<FulfillmentGrantCreationService.CreationResult> firstResult = workers.submit(
                    () -> first.createOrRecover(fulfillmentId, "cu-concurrent", "prod-concurrent"));
            Future<FulfillmentGrantCreationService.CreationResult> secondResult = workers.submit(
                    () -> second.createOrRecover(fulfillmentId, "cu-concurrent", "prod-concurrent"));
            FulfillmentGrantCreationService.CreationResult a = firstResult.get(20, TimeUnit.SECONDS);
            FulfillmentGrantCreationService.CreationResult b = secondResult.get(20, TimeUnit.SECONDS);
            log.info("真实 MySQL 并发履约：同授权={}，新建结果={}/{}",
                    a.getGrant().getGrantId().equals(b.getGrant().getGrantId()), a.isCreated(), b.isCreated());
            assertThat(a.getGrant().getGrantId()).isEqualTo(b.getGrant().getGrantId());
            assertThat(a.isCreated() ^ b.isCreated()).isTrue();
            assertThat(grants.findByBusinessOrderRef(fulfillmentId)).hasSize(1);
            assertThat(associations.findById(fulfillmentId)).isPresent();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM license_grant WHERE business_order_ref = ?",
                    Long.class, fulfillmentId)).isEqualTo(1L);
            FulfillmentGrantCreationService resumed = new FulfillmentGrantCreationService(grants, associations, transactions);
            FulfillmentGrantCreationService.CreationResult replay = resumed.createOrRecover(
                    fulfillmentId, "cu-concurrent", "prod-concurrent");
            assertThat(replay.isCreated()).isFalse();
            assertThat(replay.getGrant().getGrantId()).isEqualTo(a.getGrant().getGrantId());
        } finally {
            workers.shutdownNow();
            try {
                assertThat(workers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                cleanup(fulfillmentId);
            }
        }
    }

    @Test
    void uniqueLegacyDraftIsAdoptedAndKeepsGrantIdAcrossRestarts() {
        String fulfillmentId = "ff-test-" + UUID.randomUUID();
        String grantId = "gr-test-" + UUID.randomUUID();
        try {
            grants.saveAndFlush(grant(grantId, fulfillmentId));
            FulfillmentGrantCreationService service = new FulfillmentGrantCreationService(grants, associations, transactions);
            assertThat(service.findExisting(fulfillmentId)).isPresent().get()
                    .extracting(GrantRevisionEntity::getGrantId).isEqualTo(grantId);
            log.info("真实 MySQL 存量履约采用：grantId={}", grantId);
            assertThat(associations.findById(fulfillmentId)).isPresent();
            FulfillmentGrantCreationService restarted = new FulfillmentGrantCreationService(grants, associations, transactions);
            assertThat(restarted.createOrRecover(fulfillmentId, "cu-test", "prod-test").getGrant().getGrantId())
                    .isEqualTo(grantId);
            assertThat(grants.findByBusinessOrderRef(fulfillmentId)).hasSize(1);
        } finally {
            cleanup(fulfillmentId);
        }
    }

    @Test
    void deletedAssociatedGrantDoesNotCreateReplacement() {
        String fulfillmentId = "ff-test-" + UUID.randomUUID();
        try {
            FulfillmentGrantCreationService service = new FulfillmentGrantCreationService(grants, associations, transactions);
            String grantId = service.createOrRecover(fulfillmentId, "cu-test", "prod-test").getGrant().getGrantId();
            jdbc.update("DELETE FROM license_grant WHERE grant_id = ?", grantId);
            log.info("真实 MySQL 关联损坏：保留台账并拒绝新建");
            assertThatThrownBy(() -> service.createOrRecover(fulfillmentId, "cu-test", "prod-test"))
                    .hasMessage(FulfillmentConstant.BROKEN_ASSOCIATION);
            assertThat(grants.findByBusinessOrderRef(fulfillmentId)).isEmpty();
            assertThat(associations.findById(fulfillmentId)).isPresent();
        } finally {
            cleanup(fulfillmentId);
        }
    }

    @Test
    void ambiguousLegacyGrantsNeverGetAnArbitraryAssociation() {
        String fulfillmentId = "ff-test-" + UUID.randomUUID();
        try {
            grants.saveAndFlush(grant("gr-test-" + UUID.randomUUID(), fulfillmentId));
            grants.saveAndFlush(grant("gr-test-" + UUID.randomUUID(), fulfillmentId));
            FulfillmentGrantCreationService service = new FulfillmentGrantCreationService(grants, associations, transactions);
            log.info("真实 MySQL 存量歧义：两个授权，拒绝任选");
            assertThatThrownBy(() -> service.findExisting(fulfillmentId))
                    .hasMessage(FulfillmentConstant.AMBIGUOUS_GRANT);
            assertThat(associations.findById(fulfillmentId)).isEmpty();
            assertThat(grants.findByBusinessOrderRef(fulfillmentId)).hasSize(2);
        } finally {
            cleanup(fulfillmentId);
        }
    }

    private GrantRevisionEntity grant(String grantId, String fulfillmentId) {
        return new GrantRevisionEntity(grantId, "cu-test", "prod-test", "PERSON", "CUSTOMER_MEMBERS",
                null, "ONLINE", fulfillmentId);
    }

    private void cleanup(String fulfillmentId) {
        jdbc.update("DELETE FROM license_fulfillment_grant WHERE fulfillment_id = ?", fulfillmentId);
        jdbc.update("DELETE FROM license_grant WHERE business_order_ref = ?", fulfillmentId);
    }
}
