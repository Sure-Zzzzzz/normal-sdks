package io.github.surezzzzzz.sdk.license.server.test.cases;

import io.github.surezzzzzz.sdk.license.server.entity.QuotaBalanceEntity;
import io.github.surezzzzzz.sdk.license.server.entity.QuotaReservationEntity;
import io.github.surezzzzzz.sdk.license.server.model.QuotaReservationCommand;
import io.github.surezzzzzz.sdk.license.server.repository.QuotaReservationRepository;
import io.github.surezzzzzz.sdk.license.server.repository.GrantRepository;
import io.github.surezzzzzz.sdk.license.server.service.JpaQuotaLedgerService;
import io.github.surezzzzzz.sdk.license.server.service.JpaQuotaReservationService;
import io.github.surezzzzzz.sdk.license.server.service.QuotaPolicyService;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 主体幂等、持久围栏、到期及固定消费窗口的业务结果回归。 */
@Slf4j
class QuotaReservationServiceTest {

    private QuotaReservationRepository repository;
    private GrantRepository grants;
    private JpaQuotaLedgerService ledger;
    private QuotaPolicyService policies;
    private QuotaBalanceEntity balance;
    private JpaQuotaReservationService service;
    private final AtomicLong now = new AtomicLong(1000L);
    private final Map<String, QuotaReservationEntity> store = new HashMap<>();

    @BeforeEach
    void setup() {
        repository = mock(QuotaReservationRepository.class);
        grants = mock(GrantRepository.class);
        ledger = mock(JpaQuotaLedgerService.class);
        policies = mock(QuotaPolicyService.class);
        balance = new QuotaBalanceEntity("balance-1", "grant-1", "metric-1", 10L);
        when(ledger.lock("grant-1", "metric-1")).thenReturn(balance);
        when(repository.findByOperationKey(any())).thenAnswer(call -> store.values().stream()
                .filter(row -> row.getOperationKey().equals(call.getArgument(0))).findFirst());
        when(repository.findById(any())).thenAnswer(call -> Optional.ofNullable(store.get(call.getArgument(0))));
        when(repository.findWithLockByReservationId(any())).thenAnswer(call ->
                Optional.ofNullable(store.get(call.getArgument(0))));
        when(repository.saveAndFlush(any())).thenAnswer(call -> {
            QuotaReservationEntity row = call.getArgument(0);
            store.put(row.getReservationId(), row);
            return row;
        });
        when(repository.findByGrantIdAndMetricAndKindAndStateAndValidUntilLessThanEqual(
                any(), any(), any(), any(), anyLong())).thenAnswer(call -> {
            java.util.List<QuotaReservationEntity> expired = new java.util.ArrayList<>();
            for (QuotaReservationEntity row : store.values()) {
                if ("CONCURRENT".equals(row.getKind()) && "ACTIVE".equals(row.getState())
                        && row.getValidUntil() <= (Long) call.getArgument(4)) {
                    expired.add(row);
                }
            }
            return expired;
        });
        policy(1300L, 0L, 1L);
        io.github.surezzzzzz.sdk.license.server.entity.GrantRevisionEntity grant = mock(
                io.github.surezzzzzz.sdk.license.server.entity.GrantRevisionEntity.class);
        when(grant.getProductId()).thenReturn("product-1");
        when(grant.getSubjectKind()).thenReturn("PERSON");
        when(grants.findById("grant-1")).thenReturn(Optional.of(grant));
        service = new JpaQuotaReservationService(repository, grants, ledger, policies, null, now::get);
    }

    @Test
    void replayReturnsFirstRecordWithoutDeductingTwice() {
        QuotaReservationEntity first = service.reserve(command("iam:user-a", "iam", "op-1", 2L, "CONCURRENT"));
        assertThat(service.reserve(command("iam:user-a", "iam", "op-1", 2L, "CONCURRENT"))).isSameAs(first);
        assertThat(balance.getRemaining()).isEqualTo(8L);
        assertThatThrownBy(() -> service.reserve(command("iam:user-a", "iam", "op-1", 3L, "CONCURRENT")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("数量");
    }

    @Test
    void sameOperationIdAcrossDifferentSubjectsAndSourcesRemainsIndependent() {
        QuotaReservationEntity first = service.reserve(command("iam:user-a", "iam", "op-1", 2L, "CONCURRENT"));
        QuotaReservationEntity second = service.reserve(command("iam:user-b", "iam", "op-1", 2L, "CONCURRENT"));
        QuotaReservationEntity third = service.reserve(command("iam:user-a", "inherited", "op-1", 2L, "CONCURRENT"));
        assertThat(first.getOperationKey()).isNotEqualTo(second.getOperationKey()).isNotEqualTo(third.getOperationKey());
        assertThat(balance.getRemaining()).isEqualTo(4L);
        assertThat(third.getFence()).isGreaterThan(second.getFence()).isGreaterThan(first.getFence());
    }

    @Test
    void replayCannotChangeModelAction() {
        service.reserve(command("iam:user-a", "iam", "op-1", 2L, "CONCURRENT"));
        QuotaReservationCommand changed = QuotaReservationCommand.builder().productId("product-1").grantId("grant-1")
                .subjectKind("PERSON").action("other-action").metric("metric-1").quantity(2L).kind("CONCURRENT")
                .businessOperationId("op-1").expectedGrantRevision(1L).principalId("iam:user-a")
                .authenticationSourceId("iam").subjectType("HUMAN").build();
        assertThatThrownBy(() -> service.reserve(changed)).isInstanceOf(IllegalArgumentException.class);
        assertThat(balance.getRemaining()).isEqualTo(8L);
        assertThatThrownBy(() -> service.reserve(command("iam:user-a", "iam", "op-1", 2L, "CONSUMPTION")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(balance.getRemaining()).isEqualTo(8L);
    }

    @Test
    void releaseRequiresBothPrincipalAndAuthenticationSourceAndIsIdempotent() {
        QuotaReservationEntity row = service.reserve(command("iam:user-a", "iam", "op-1", 2L, "CONCURRENT"));
        assertThatThrownBy(() -> service.release(row.getReservationId(), "iam:user-a", "other-source"))
                .isInstanceOf(SecurityException.class);
        assertThat(balance.getRemaining()).isEqualTo(8L);
        assertThat(service.release(row.getReservationId(), "iam:user-a", "iam").getState()).isEqualTo("RELEASED");
        service.release(row.getReservationId(), "iam:user-a", "iam");
        assertThat(balance.getRemaining()).isEqualTo(10L);
    }

    @Test
    void expiryReclaimsExactlyOnceBeforeAnotherReservation() {
        policy(1001L, 0L, 1L);
        QuotaReservationEntity first = service.reserve(command("iam:user-a", "iam", "op-1", 10L, "CONCURRENT"));
        now.set(1001L);
        policy(1301L, 0L, 1L);
        service.reserve(command("iam:user-a", "iam", "op-2", 2L, "CONCURRENT"));
        assertThat(first.getState()).isEqualTo("EXPIRED");
        assertThat(balance.getRemaining()).isEqualTo(8L);
        service.release(first.getReservationId(), "iam:user-a", "iam");
        assertThat(balance.getRemaining()).isEqualTo(8L);
    }

    @Test
    void renewalRequiresExpectedFenceAndOnlyExtendsWithinPolicyCutoff() {
        policy(1100L, 0L, 1L);
        QuotaReservationEntity row = service.reserve(command("iam:user-a", "iam", "op-1", 2L, "CONCURRENT"));
        long originalFence = row.getFence();
        policy(1200L, 0L, 1L);
        service.renew(row.getReservationId(), originalFence, "iam:user-a", "iam", "HUMAN");
        assertThat(row.getValidUntil()).isEqualTo(1200L);
        assertThat(row.getFence()).isGreaterThan(originalFence);
        assertThat(balance.getRemaining()).isEqualTo(8L);
        assertThatThrownBy(() -> service.renew(row.getReservationId(), originalFence,
                "iam:user-a", "iam", "HUMAN")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void consumptionCannotBeReleasedOrRenewedAndTotalNeverAutomaticallyRecovers() {
        policy(2000L, 0L, 1L);
        QuotaReservationEntity row = service.reserve(command("iam:user-a", "iam", "op-1", 2L, "CONSUMPTION"));
        assertThatThrownBy(() -> service.release(row.getReservationId(), "iam:user-a", "iam"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.renew(row.getReservationId(), row.getFence(),
                "iam:user-a", "iam", "HUMAN")).isInstanceOf(IllegalStateException.class);
        now.set(2001L);
        policy(3000L, 0L, 1L);
        service.reserve(command("iam:user-a", "iam", "op-2", 2L, "CONSUMPTION"));
        assertThat(balance.getRemaining()).isEqualTo(6L);
    }

    @Test
    void fixedWindowUsesPermanentAnchorAndReplaysDoNotChargeTheNewWindow() {
        policy(5000L, 100L, 950L);
        QuotaReservationEntity first = service.reserve(command("iam:user-a", "iam", "op-1", 10L, "CONSUMPTION"));
        assertThat(first.getValidUntil()).isEqualTo(1050L);
        now.set(1050L);
        // 模拟商业续期把startsAt改为1000；永久锚仍为首次950。
        policy(6000L, 100L, 1000L);
        assertThat(service.reserve(command("iam:user-a", "iam", "op-1", 10L, "CONSUMPTION"))).isSameAs(first);
        service.reserve(command("iam:user-a", "iam", "op-2", 2L, "CONSUMPTION"));
        assertThat(balance.getWindowAnchorAt()).isEqualTo(950L);
        assertThat(balance.getWindowUntil()).isEqualTo(1150L);
        assertThat(balance.getRemaining()).isEqualTo(8L);
    }

    @Test
    void noNegativeZeroOrOverflowingBalanceMutation() {
        assertThatThrownBy(() -> balance.deduct(0L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> balance.release(Long.MAX_VALUE)).isInstanceOf(IllegalArgumentException.class);
        assertThat(balance.getRemaining()).isEqualTo(10L);
    }

    @Test
    void digestUsesLengthFramesRatherThanAmbiguousSeparators() {
        assertThat(JpaQuotaReservationService.operationKey("ab", "c"))
                .isNotEqualTo(JpaQuotaReservationService.operationKey("a", "bc")).hasSize(64);
    }

    private void policy(long validUntil, long period, long anchor) {
        when(policies.require(any(), any(), any(), anyLong(), any(), any(), any(), any(), any(),
                any(), any(), any(), anyLong())).thenReturn(new QuotaPolicyService.Policy(10L, validUntil, period, anchor));
    }

    static QuotaReservationCommand command(String principal, String source, String operationId, long quantity,
                                            String kind) {
        return QuotaReservationCommand.builder().productId("product-1").grantId("grant-1").subjectKind("PERSON")
                .action("run").metric("metric-1").quantity(quantity).kind(kind).businessOperationId(operationId)
                .expectedGrantRevision(1L).principalId(principal).authenticationSourceId(source).subjectType("HUMAN").build();
    }
}
