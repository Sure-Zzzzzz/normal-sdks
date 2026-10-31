package io.github.surezzzzzz.sdk.license.server.test.cases;

import io.github.surezzzzzz.sdk.license.server.entity.QuotaBalanceEntity;
import io.github.surezzzzzz.sdk.license.server.entity.QuotaReservationEntity;
import io.github.surezzzzzz.sdk.license.server.model.QuotaReservationCommand;
import io.github.surezzzzzz.sdk.license.server.repository.QuotaBalanceRepository;
import io.github.surezzzzzz.sdk.license.server.repository.QuotaReservationRepository;
import io.github.surezzzzzz.sdk.license.server.repository.GrantRepository;
import io.github.surezzzzzz.sdk.license.server.entity.GrantRevisionEntity;
import io.github.surezzzzzz.sdk.license.server.service.JpaQuotaLedgerService;
import io.github.surezzzzzz.sdk.license.server.service.JpaQuotaReservationService;
import io.github.surezzzzzz.sdk.license.server.service.QuotaPolicyService;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 真实MySQL事务证明：最后单位、同号重放、并发释放及唯一键失败后余额回滚。 */
@Slf4j
@SpringBootTest(classes = PersistenceTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class QuotaPersistenceTest {

    @Autowired
    private QuotaBalanceRepository balances;
    @Autowired
    private QuotaReservationRepository reservations;
    @Autowired
    private GrantRepository grants;
    @Autowired
    private PlatformTransactionManager transactions;
    private QuotaPolicyService policies;
    private JpaQuotaLedgerService ledger;
    private String grantId;
    private String balanceId;

    @BeforeEach
    void setup() {
        grantId = "quota-grant-" + UUID.randomUUID();
        balanceId = "quota-balance-" + UUID.randomUUID();
        policies = mock(QuotaPolicyService.class);
        when(policies.require(any(), any(), any(), anyLong(), any(), any(), any(), any(), any(),
                any(), any(), any(), anyLong())).thenReturn(new QuotaPolicyService.Policy(10L, 1300L, 0L, 1L));
        ledger = new JpaQuotaLedgerService(balances);
        balances.saveAndFlush(new QuotaBalanceEntity(balanceId, grantId, "metric-1", 10L));
        grants.saveAndFlush(new GrantRevisionEntity(grantId, "customer-1", "product-1", "PERSON",
                "DIRECT", null, "ONLINE", "order-" + grantId));
    }

    @AfterEach
    void cleanup() {
        for (QuotaReservationEntity row : reservations.findAll()) {
            if (grantId.equals(row.getGrantId())) {
                reservations.deleteById(row.getReservationId());
            }
        }
        balances.deleteById(balanceId);
        grants.deleteById(grantId);
    }

    @Test
    void twoIndependentServicesCannotBothReserveTheFinalUnit() throws Exception {
        balances.deleteById(balanceId);
        balances.saveAndFlush(new QuotaBalanceEntity(balanceId, grantId, "metric-1", 1L));
        when(policies.require(any(), any(), any(), anyLong(), any(), any(), any(), any(), any(),
                any(), any(), any(), anyLong())).thenReturn(new QuotaPolicyService.Policy(1L, 1300L, 0L, 1L));
        List<Boolean> outcomes = race(() -> attempt("op-a", 1L), () -> attempt("op-b", 1L));
        assertThat(outcomes).containsExactlyInAnyOrder(true, false);
        assertThat(remaining()).isZero();
    }

    @Test
    void concurrentReplayReturnsOneRecordAndOnlyOneDeduction() throws Exception {
        List<String> ids = race(() -> service(reservations).reserve(command("same-operation", 2L)).getReservationId(),
                () -> service(reservations).reserve(command("same-operation", 2L)).getReservationId());
        assertThat(ids.get(0)).isEqualTo(ids.get(1));
        assertThat(remaining()).isEqualTo(8L);
    }

    @Test
    void concurrentReleaseReturnsCapacityExactlyOnce() throws Exception {
        QuotaReservationEntity row = service(reservations).reserve(command("op-1", 4L));
        QuotaReservationRepository synchronizedObservation = mock(QuotaReservationRepository.class,
                AdditionalAnswers.delegatesTo(reservations));
        CountDownLatch observed = new CountDownLatch(2);
        doAnswer(call -> {
            Optional<QuotaReservationEntity> value = reservations.findById(call.getArgument(0));
            observed.countDown();
            if (!observed.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("两个释放者未完成相同旧版本观察");
            }
            return value;
        }).when(synchronizedObservation).findById(row.getReservationId());
        // 两个调用者都先看同一旧版本；写事务仍须串行回读终态，不能靠概率避开持久上下文冲突。
        List<String> states = race(() -> service(synchronizedObservation).release(row.getReservationId(), "iam:user-a", "iam").getState(),
                () -> service(synchronizedObservation).release(row.getReservationId(), "iam:user-a", "iam").getState());
        assertThat(states).containsOnly("RELEASED");
        assertThat(remaining()).isEqualTo(10L);
    }

    @Test
    void uniqueViolationRollsBackDeductionBeforeReadingWinnerInNewTransaction() {
        QuotaReservationEntity winner = service(reservations).reserve(command("op-1", 2L));
        long fence = balances.findById(balanceId).get().getFenceSequence();
        QuotaReservationRepository staleRead = mock(QuotaReservationRepository.class,
                AdditionalAnswers.delegatesTo(reservations));
        // 仅第一次查重制造旧观察；真实saveAndFlush触发MySQL唯一键冲突和真实事务回滚。
        doReturn(Optional.empty(), Optional.of(winner)).when(staleRead).findByOperationKey(anyString());
        assertThat(service(staleRead).reserve(command("op-1", 2L)).getReservationId())
                .isEqualTo(winner.getReservationId());
        assertThat(remaining()).isEqualTo(8L);
        assertThat(balances.findById(balanceId).get().getFenceSequence()).isEqualTo(fence);
    }

    @Test
    void newServiceInstanceKeepsDatabaseFenceAcrossRenewal() {
        QuotaReservationEntity first = service(reservations).reserve(command("op-1", 2L));
        when(policies.require(any(), any(), any(), anyLong(), any(), any(), any(), any(), any(),
                any(), any(), any(), anyLong())).thenReturn(new QuotaPolicyService.Policy(10L, 1400L, 0L, 1L));
        QuotaReservationEntity renewed = service(reservations).renew(first.getReservationId(), first.getFence(),
                "iam:user-a", "iam", "HUMAN");
        assertThat(renewed.getFence()).isGreaterThan(first.getFence());
        assertThat(renewed.getFence()).isEqualTo(balances.findById(balanceId).get().getFenceSequence());
        assertThat(remaining()).isEqualTo(8L);
    }

    private boolean attempt(String operation, long quantity) {
        try {
            service(reservations).reserve(command(operation, quantity));
            return true;
        } catch (JpaQuotaReservationService.QuotaUnavailableException insufficient) {
            return false;
        }
    }

    private long remaining() {
        return balances.findById(balanceId).get().getRemaining();
    }

    private JpaQuotaReservationService service(QuotaReservationRepository repository) {
        return new JpaQuotaReservationService(repository, grants, ledger, policies, transactions, () -> 1000L);
    }

    private QuotaReservationCommand command(String operation, long quantity) {
        return QuotaReservationCommand.builder().productId("product-1").grantId(grantId).subjectKind("PERSON")
                .action("run").metric("metric-1").quantity(quantity).kind("CONCURRENT").businessOperationId(operation)
                .expectedGrantRevision(1L).principalId("iam:user-a").authenticationSourceId("iam").subjectType("HUMAN").build();
    }

    private static <T> List<T> race(Callable<T> first, Callable<T> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<T> left = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("并发测试未同时就绪");
                }
                return first.call();
            });
            Future<T> right = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("并发测试未同时就绪");
                }
                return second.call();
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return Arrays.asList(left.get(15, TimeUnit.SECONDS), right.get(15, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }
}
