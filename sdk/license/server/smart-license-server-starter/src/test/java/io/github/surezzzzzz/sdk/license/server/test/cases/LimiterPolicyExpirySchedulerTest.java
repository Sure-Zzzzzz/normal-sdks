package io.github.surezzzzzz.sdk.license.server.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.surezzzzzz.sdk.license.server.constant.LimiterPolicyBindingState;
import io.github.surezzzzzz.sdk.license.server.entity.GrantRevisionEntity;
import io.github.surezzzzzz.sdk.license.server.entity.LimiterPolicyBindingEntity;
import io.github.surezzzzzz.sdk.license.server.repository.GrantRepository;
import io.github.surezzzzzz.sdk.license.server.repository.LimiterPolicyBindingRepository;
import io.github.surezzzzzz.sdk.license.server.service.LimiterPolicyExpiryScheduler;
import io.github.surezzzzzz.sdk.license.server.service.LimiterPolicyProjectionService;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/** 限频到期与远端成功后本地未提交场景的补偿收敛测试。 */
@Slf4j
class LimiterPolicyExpirySchedulerTest {

    @Test
    void expiredGrantDisablesLimiterBeforeSuspendingGrant() {
        GrantRepository grants = mock(GrantRepository.class);
        LimiterPolicyProjectionService projection = mock(LimiterPolicyProjectionService.class);
        GrantRevisionEntity grant = grant("ENABLED", Instant.now().minusSeconds(10).getEpochSecond());
        when(grants.findByStateAndServiceUntilLessThanEqual(any(String.class), any(Long.class), any(Pageable.class)))
                .thenReturn(new PageImpl<GrantRevisionEntity>(Collections.singletonList(grant)));
        LimiterPolicyExpiryScheduler scheduler = new LimiterPolicyExpiryScheduler(grants);
        ReflectionTestUtils.setField(scheduler, "projection", projection);

        scheduler.reconcile();

        verify(projection).disable(grant);
        verify(grants).save(grant);
        assertThat(grant.getState()).isEqualTo("SUSPENDED");
    }

    @Test
    void nonEnabledGrantWithUnsettledBindingIsDisabledByCompensation() {
        GrantRepository grants = mock(GrantRepository.class);
        LimiterPolicyBindingRepository bindings = mock(LimiterPolicyBindingRepository.class);
        LimiterPolicyProjectionService projection = mock(LimiterPolicyProjectionService.class);
        GrantRevisionEntity grant = grant("SUSPENDED", Instant.now().plusSeconds(3600).getEpochSecond());
        LimiterPolicyBindingEntity binding = new LimiterPolicyBindingEntity(
                "binding-1", grant.getGrantId(), 1L,
                new io.github.surezzzzzz.sdk.license.core.spi.LimiterPolicyPort.PolicyRequest(
                        new io.github.surezzzzzz.sdk.license.core.spi.LimiterPolicyPort.PolicyKey(
                                "orders", "read", null, grant.getCustomerId()),
                        Collections.singletonList(new io.github.surezzzzzz.sdk.license.core.spi.LimiterPolicyPort.Limit(
                                10L, 1L, "MINUTES")), true, "op-1"), "{}");
        when(grants.findByStateAndServiceUntilLessThanEqual(any(String.class), any(Long.class), any(Pageable.class)))
                .thenReturn(new PageImpl<GrantRevisionEntity>(Collections.emptyList()));
        when(bindings.findByStateIn(anyCollection())).thenReturn(Collections.singletonList(binding));
        when(grants.findById(grant.getGrantId())).thenReturn(Optional.of(grant));
        LimiterPolicyExpiryScheduler scheduler = new LimiterPolicyExpiryScheduler(grants);
        ReflectionTestUtils.setField(scheduler, "bindings", bindings);
        ReflectionTestUtils.setField(scheduler, "projection", projection);

        scheduler.reconcile();

        verify(projection).disable(grant);
        verify(projection, never()).enable(any());
        log.info("非启用授权的未确认绑定进入停用补偿");
    }

    @Test
    void enabledGrantWithLocallyDisabledBindingIsReenabledByCompensation() {
        GrantRepository grants = mock(GrantRepository.class);
        LimiterPolicyBindingRepository bindings = mock(LimiterPolicyBindingRepository.class);
        LimiterPolicyProjectionService projection = mock(LimiterPolicyProjectionService.class);
        GrantRevisionEntity grant = grant("ENABLED", Instant.now().plusSeconds(3600).getEpochSecond());
        LimiterPolicyBindingEntity binding = new LimiterPolicyBindingEntity(
                "binding-1", grant.getGrantId(), 1L,
                new io.github.surezzzzzz.sdk.license.core.spi.LimiterPolicyPort.PolicyRequest(
                        new io.github.surezzzzzz.sdk.license.core.spi.LimiterPolicyPort.PolicyKey(
                                "orders", "read", "service-ns", grant.getCustomerId()),
                        Collections.singletonList(new io.github.surezzzzzz.sdk.license.core.spi.LimiterPolicyPort.Limit(
                                10L, 1L, "MINUTES")), true, "op-1"), "{}");
        binding.failed(LimiterPolicyBindingState.DISABLED, null);
        when(grants.findByStateAndServiceUntilLessThanEqual(any(String.class), any(Long.class), any(Pageable.class)))
                .thenReturn(new PageImpl<GrantRevisionEntity>(Collections.emptyList()));
        when(bindings.findByStateIn(anyCollection())).thenReturn(Collections.singletonList(binding));
        when(grants.findById(grant.getGrantId())).thenReturn(Optional.of(grant));
        LimiterPolicyExpiryScheduler scheduler = new LimiterPolicyExpiryScheduler(grants);
        ReflectionTestUtils.setField(scheduler, "bindings", bindings);
        ReflectionTestUtils.setField(scheduler, "projection", projection);

        scheduler.reconcile();

        verify(projection).enable(grant);
        verify(projection, never()).disable(any());
    }

    private static GrantRevisionEntity grant(String state, long serviceUntil) {
        GrantRevisionEntity grant = new GrantRevisionEntity("grant-1", "customer-1", "product-1",
                "SERVICE", "ASSIGNED_SUBJECTS", null, "ONLINE", null);
        grant.setState(state);
        ReflectionTestUtils.setField(grant, "serviceUntil", serviceUntil);
        ReflectionTestUtils.setField(grant, "limiterPoliciesJson",
                "[{\"serviceCode\":\"orders\",\"resourceCode\":\"read\","
                        + "\"limits\":[{\"count\":10,\"window\":1,\"unit\":\"MINUTES\"}]}]");
        return grant;
    }
}
