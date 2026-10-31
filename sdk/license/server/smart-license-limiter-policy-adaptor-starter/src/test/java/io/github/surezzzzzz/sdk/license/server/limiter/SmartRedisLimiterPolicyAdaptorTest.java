package io.github.surezzzzzz.sdk.license.server.limiter;

import io.github.surezzzzzz.sdk.license.core.spi.LimiterPolicyPort;
import io.github.surezzzzzz.sdk.license.server.limiter.configuration.SimpleLicenseLimiterPolicyAdaptorProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.SmartRedisLimiterPolicyMutationClient;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.model.PolicyLimit;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.model.PolicyMutationResult;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.model.PolicyRuleView;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.model.PolicyUpsertCommand;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * License 与 limiter mutation core 的字段和失败语义适配测试。
 *
 * @author surezzzzzz
 */
class SmartRedisLimiterPolicyAdaptorTest {

    private static final LimiterPolicyPort.PolicyKey KEY =
            new LimiterPolicyPort.PolicyKey("mock-service", "mock-resource", null, "customer-001");

    @Test
    void upsertExecutesMutationCoreDefaultWorkflow() {
        SmartRedisLimiterPolicyMutationClient client = mock(
                SmartRedisLimiterPolicyMutationClient.class, CALLS_REAL_METHODS);
        when(client.create(any(PolicyUpsertCommand.class)))
                .thenReturn(new PolicyMutationResult(7L, 1L, 2L, true));
        when(client.findByIdentityRow(7L)).thenReturn(view(7L, 1L, true, 100L));
        SmartRedisLimiterPolicyAdaptor adaptor = new SmartRedisLimiterPolicyAdaptor(client, properties());

        LimiterPolicyPort.PolicyResult result = adaptor.upsert(request(true));

        assertEquals("7", result.getRuleId());
        assertEquals(2L, result.getRevision());
        verify(client).findByKey(any(PolicyUpsertCommand.class));
        verify(client).create(any(PolicyUpsertCommand.class));
    }

    @Test
    void upsertUsesCustomerExactAndPreservesNoRevision() {
        SmartRedisLimiterPolicyMutationClient client = mock(SmartRedisLimiterPolicyMutationClient.class);
        when(client.upsert(any(PolicyUpsertCommand.class)))
                .thenReturn(new PolicyMutationResult(7L, 4L, PolicyMutationResult.NO_REVISION, true));
        when(client.findByIdentityRow(7L)).thenReturn(view(7L, 4L, true, 100L));
        SmartRedisLimiterPolicyAdaptor adaptor = new SmartRedisLimiterPolicyAdaptor(client, properties());

        LimiterPolicyPort.PolicyResult result = adaptor.upsert(request(true));

        assertEquals("7", result.getRuleId());
        assertEquals(LimiterPolicyPort.PolicyResult.NO_REVISION, result.getRevision());
        assertEquals("customer", result.getKey().getNamespace());
        assertEquals("customer-001", result.getKey().getCustomerId());
        ArgumentCaptor<PolicyUpsertCommand> command = ArgumentCaptor.forClass(PolicyUpsertCommand.class);
        verify(client).upsert(command.capture());
        assertEquals("CUSTOMER", command.getValue().getDimension());
        assertEquals("EXACT", command.getValue().getSelector());
        assertEquals("customer-001", command.getValue().getObjectId());
        assertEquals("customer", command.getValue().getNamespace());
        assertEquals(100L, command.getValue().getLimits().get(0).getCount());
    }

    @Test
    void findUsesRemoteNamespaceAndNoRevision() {
        SmartRedisLimiterPolicyMutationClient client = mock(SmartRedisLimiterPolicyMutationClient.class);
        when(client.findByKey(any(PolicyUpsertCommand.class))).thenReturn(view(8L, 9L, false));
        SmartRedisLimiterPolicyAdaptor adaptor = new SmartRedisLimiterPolicyAdaptor(client, properties());

        LimiterPolicyPort.PolicyResult result = adaptor.find(KEY);

        assertEquals("customer", result.getKey().getNamespace());
        assertEquals(LimiterPolicyPort.PolicyResult.NO_REVISION, result.getRevision());
        assertTrue(!result.isEnabled());
    }

    @Test
    void findFailurePreservesUpstreamException() {
        SmartRedisLimiterPolicyMutationClient client = mock(SmartRedisLimiterPolicyMutationClient.class);
        IllegalStateException readFailure = new IllegalStateException("read failed");
        when(client.findByKey(any(PolicyUpsertCommand.class))).thenThrow(readFailure);
        SmartRedisLimiterPolicyAdaptor adaptor = new SmartRedisLimiterPolicyAdaptor(client, properties());

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> adaptor.find(KEY));

        assertSame(readFailure, failure);
    }

    @Test
    void conflictIsPropagatedWithoutWrapping() {
        SmartRedisLimiterPolicyMutationClient client = mock(SmartRedisLimiterPolicyMutationClient.class);
        when(client.findByIdentityRow(7L)).thenReturn(view(7L, 4L, true));
        SmartRedisLimiterPolicyMutationClient.PolicyMutationConflictException conflict =
                new SmartRedisLimiterPolicyMutationClient.PolicyMutationConflictException("conflict");
        when(client.updateEnabled(7L, 4L, false))
                .thenThrow(conflict);
        SmartRedisLimiterPolicyAdaptor adaptor = new SmartRedisLimiterPolicyAdaptor(client, properties());

        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> adaptor.setEnabled(KEY, "7", 4L, false, "operation-001"));

        assertSame(conflict, failure);
    }

    @Test
    void disableReadFailurePreservesUpstreamExceptionBeforeMutation() {
        SmartRedisLimiterPolicyMutationClient client = mock(SmartRedisLimiterPolicyMutationClient.class);
        IllegalStateException readFailure = new IllegalStateException("read failed");
        when(client.findByIdentityRow(7L)).thenThrow(readFailure);
        SmartRedisLimiterPolicyAdaptor adaptor = new SmartRedisLimiterPolicyAdaptor(client, properties());

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> adaptor.setEnabled(KEY, "7", 4L, false, "operation-001"));

        assertSame(readFailure, failure);
        verify(client, never()).updateEnabled(7L, 4L, false);
    }

    @Test
    void missingNamespaceIsRejectedBeforeRemoteCall() {
        SmartRedisLimiterPolicyMutationClient client = mock(SmartRedisLimiterPolicyMutationClient.class);
        SimpleLicenseLimiterPolicyAdaptorProperties properties = new SimpleLicenseLimiterPolicyAdaptorProperties();
        SmartRedisLimiterPolicyAdaptor adaptor = new SmartRedisLimiterPolicyAdaptor(client, properties);

        LimiterPolicyPort.Failure failure = assertThrows(LimiterPolicyPort.Failure.class,
                () -> adaptor.upsert(request(true)));

        assertEquals(LimiterPolicyPort.FailureKind.REJECTED, failure.getKind());
    }

    @Test
    void mismatchedReadbackFailsClosed() {
        SmartRedisLimiterPolicyMutationClient client = mock(SmartRedisLimiterPolicyMutationClient.class);
        when(client.upsert(any(PolicyUpsertCommand.class)))
                .thenReturn(new PolicyMutationResult(7L, 4L, 5L, true));
        when(client.findByIdentityRow(7L)).thenReturn(new PolicyRuleView(7L, 4L,
                "mock-service", "mock-resource", "CUSTOMER", "EXACT", "customer", null,
                "another-customer", true, Collections.singletonList(new PolicyLimit(10L, 1L, "MINUTES"))));
        SmartRedisLimiterPolicyAdaptor adaptor = new SmartRedisLimiterPolicyAdaptor(client, properties());

        LimiterPolicyPort.Failure failure = assertThrows(LimiterPolicyPort.Failure.class,
                () -> adaptor.upsert(request(true)));

        assertEquals(LimiterPolicyPort.FailureKind.UNCERTAIN, failure.getKind());
    }

    @Test
    void disableUsesConfirmedRuleAndCasVersion() {
        SmartRedisLimiterPolicyMutationClient client = mock(SmartRedisLimiterPolicyMutationClient.class);
        when(client.updateEnabled(7L, 4L, false)).thenReturn(new PolicyMutationResult(7L, 5L, 6L, false));
        when(client.findByIdentityRow(7L)).thenReturn(view(7L, 4L, true), view(7L, 5L, false));
        SmartRedisLimiterPolicyAdaptor adaptor = new SmartRedisLimiterPolicyAdaptor(client, properties());

        LimiterPolicyPort.PolicyResult result = adaptor.setEnabled(KEY, "7", 4L, false, "operation-001");

        assertEquals(5L, result.getRowVersion());
        assertTrue(!result.isEnabled());
        verify(client).updateEnabled(7L, 4L, false);
        verify(client, times(2)).findByIdentityRow(7L);
    }

    @Test
    void disableRejectsAnotherCustomerBeforeMutation() {
        SmartRedisLimiterPolicyMutationClient client = mock(SmartRedisLimiterPolicyMutationClient.class);
        when(client.findByIdentityRow(7L)).thenReturn(new PolicyRuleView(7L, 4L,
                "mock-service", "mock-resource", "CUSTOMER", "EXACT", "customer", null,
                "another-customer", true, Collections.singletonList(new PolicyLimit(10L, 1L, "MINUTES"))));
        SmartRedisLimiterPolicyAdaptor adaptor = new SmartRedisLimiterPolicyAdaptor(client, properties());

        LimiterPolicyPort.Failure failure = assertThrows(LimiterPolicyPort.Failure.class,
                () -> adaptor.setEnabled(KEY, "7", 4L, false, "operation-001"));

        assertEquals(LimiterPolicyPort.FailureKind.INVALID_RESPONSE, failure.getKind());
        verify(client, never()).updateEnabled(7L, 4L, false);
    }

    @Test
    void upsertReadbackFailurePreservesUpstreamException() {
        SmartRedisLimiterPolicyMutationClient client = mock(SmartRedisLimiterPolicyMutationClient.class);
        when(client.upsert(any(PolicyUpsertCommand.class)))
                .thenReturn(new PolicyMutationResult(7L, 4L, 5L, true));
        IllegalStateException readFailure = new IllegalStateException("read failed");
        when(client.findByIdentityRow(7L)).thenThrow(readFailure);
        SmartRedisLimiterPolicyAdaptor adaptor = new SmartRedisLimiterPolicyAdaptor(client, properties());

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> adaptor.upsert(request(true)));

        assertSame(readFailure, failure);
    }

    @Test
    void upsertRejectsConcurrentWindowChangeAsUncertain() {
        SmartRedisLimiterPolicyMutationClient client = mock(SmartRedisLimiterPolicyMutationClient.class);
        when(client.upsert(any(PolicyUpsertCommand.class)))
                .thenReturn(new PolicyMutationResult(7L, 4L, 5L, true));
        when(client.findByIdentityRow(7L)).thenReturn(view(7L, 5L, true, 100L));
        SmartRedisLimiterPolicyAdaptor adaptor = new SmartRedisLimiterPolicyAdaptor(client, properties());

        LimiterPolicyPort.Failure failure = assertThrows(LimiterPolicyPort.Failure.class,
                () -> adaptor.upsert(request(true)));

        assertEquals(LimiterPolicyPort.FailureKind.UNCERTAIN, failure.getKind());
    }

    @Test
    void upsertRejectsDifferentWindowAsUncertain() {
        SmartRedisLimiterPolicyMutationClient client = mock(SmartRedisLimiterPolicyMutationClient.class);
        when(client.upsert(any(PolicyUpsertCommand.class)))
                .thenReturn(new PolicyMutationResult(7L, 4L, 5L, true));
        when(client.findByIdentityRow(7L)).thenReturn(view(7L, 4L, true, 10L));
        SmartRedisLimiterPolicyAdaptor adaptor = new SmartRedisLimiterPolicyAdaptor(client, properties());

        LimiterPolicyPort.Failure failure = assertThrows(LimiterPolicyPort.Failure.class,
                () -> adaptor.upsert(request(true)));

        assertEquals(LimiterPolicyPort.FailureKind.UNCERTAIN, failure.getKind());
    }

    @Test
    void upsertConflictMayFollowPartialWrite() {
        SmartRedisLimiterPolicyMutationClient client = mock(SmartRedisLimiterPolicyMutationClient.class);
        SmartRedisLimiterPolicyMutationClient.PolicyMutationConflictException conflict =
                new SmartRedisLimiterPolicyMutationClient.PolicyMutationConflictException("conflict");
        when(client.upsert(any(PolicyUpsertCommand.class)))
                .thenThrow(conflict);
        SmartRedisLimiterPolicyAdaptor adaptor = new SmartRedisLimiterPolicyAdaptor(client, properties());

        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> adaptor.upsert(request(true)));

        assertSame(conflict, failure);
    }

    private static PolicyRuleView view(long ruleId, long rowVersion, boolean enabled) {
        return view(ruleId, rowVersion, enabled, 10L);
    }

    private static PolicyRuleView view(long ruleId, long rowVersion, boolean enabled, long count) {
        return new PolicyRuleView(ruleId, rowVersion, "mock-service", "mock-resource",
                "CUSTOMER", "EXACT", "customer", null, "customer-001", enabled,
                Collections.singletonList(new PolicyLimit(count, 1L, "MINUTES")));
    }

    private static SimpleLicenseLimiterPolicyAdaptorProperties properties() {
        SimpleLicenseLimiterPolicyAdaptorProperties properties = new SimpleLicenseLimiterPolicyAdaptorProperties();
        properties.setCustomerNamespace("customer");
        return properties;
    }

    private static LimiterPolicyPort.PolicyRequest request(boolean enabled) {
        return new LimiterPolicyPort.PolicyRequest(KEY,
                Collections.singletonList(new LimiterPolicyPort.Limit(100L, 1L, "MINUTES")), enabled,
                "operation-001");
    }
}
