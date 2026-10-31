package io.github.surezzzzzz.sdk.license.server.test.cases;

import io.github.surezzzzzz.sdk.license.core.spi.LimiterPolicyPort;
import io.github.surezzzzzz.sdk.license.server.constant.LimiterPolicyBindingState;
import io.github.surezzzzzz.sdk.license.server.entity.GrantRevisionEntity;
import io.github.surezzzzzz.sdk.license.server.entity.LimiterPolicyBindingEntity;
import io.github.surezzzzzz.sdk.license.server.repository.LimiterPolicyBindingRepository;
import io.github.surezzzzzz.sdk.license.server.service.LimiterPolicyProjectionService;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 客户限频投影的结果完整性、回读和停用语义。 */
class LimiterPolicyProjectionServiceTest {

    @Test
    void idempotentResultKeepsPreviouslyConfirmedServiceRevision() {
        LimiterPolicyPort.PolicyKey key = new LimiterPolicyPort.PolicyKey(
                "mock-service", "mock-resource", "customer", "customer-1");
        LimiterPolicyPort.PolicyRequest request = new LimiterPolicyPort.PolicyRequest(key,
                Collections.singletonList(new LimiterPolicyPort.Limit(10L, 1L, "MINUTES")),
                true, "operation-1");
        LimiterPolicyBindingEntity binding = new LimiterPolicyBindingEntity(
                "binding-1", "grant-1", 1L, request, "{}");

        binding.synced(new LimiterPolicyPort.PolicyResult(key, "7", 2L, 8L, true), true);
        binding.synced(new LimiterPolicyPort.PolicyResult(key, "7", 2L,
                LimiterPolicyPort.PolicyResult.NO_REVISION, true), true);

        assertThat(binding.getServiceRevision()).isEqualTo(8L);
    }

    @Test
    void nullUpsertResultFailsClosedAndLeavesBindingFailure() {
        LimiterPolicyBindingRepository bindings = mock(LimiterPolicyBindingRepository.class);
        LimiterPolicyPort port = mock(LimiterPolicyPort.class);
        when(bindings.save(any(LimiterPolicyBindingEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(port.upsert(any(LimiterPolicyPort.PolicyRequest.class))).thenReturn(null);
        LimiterPolicyProjectionService service = new LimiterPolicyProjectionService(bindings);
        ReflectionTestUtils.setField(service, "port", port);

        GrantRevisionEntity grant = grant("[{\"serviceCode\":\"orders\",\"resourceCode\":\"read\","
                + "\"limits\":[{\"count\":10,\"window\":1,\"unit\":\"MINUTES\"}]}]");
        assertThatThrownBy(() -> service.enable(grant))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(ex -> ((LicenseHttpException) ex).getStatus())
                .isEqualTo(503);
        verify(bindings, times(2)).save(any(LimiterPolicyBindingEntity.class));
    }

    @Test
    void unwrappedRemoteRuntimeFailureIsRecordedAsUncertain() {
        LimiterPolicyBindingRepository bindings = mock(LimiterPolicyBindingRepository.class);
        LimiterPolicyPort port = mock(LimiterPolicyPort.class);
        when(bindings.save(any(LimiterPolicyBindingEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(port.upsert(any(LimiterPolicyPort.PolicyRequest.class)))
                .thenThrow(new IllegalStateException("transport failure"));
        LimiterPolicyProjectionService service = new LimiterPolicyProjectionService(bindings);
        ReflectionTestUtils.setField(service, "port", port);

        GrantRevisionEntity grant = grant("[{\"serviceCode\":\"orders\",\"resourceCode\":\"read\","
                + "\"limits\":[{\"count\":10,\"window\":1,\"unit\":\"MINUTES\"}]}]");
        assertThatThrownBy(() -> service.enable(grant))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(ex -> ((LicenseHttpException) ex).getStatus())
                .isEqualTo(503);
        org.mockito.ArgumentCaptor<LimiterPolicyBindingEntity> captor =
                org.mockito.ArgumentCaptor.forClass(LimiterPolicyBindingEntity.class);
        verify(bindings, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(1).getState())
                .isEqualTo(LimiterPolicyBindingState.UNCERTAIN);
    }

    @Test
    void disableReadsUnknownBindingThenCasDisablesAndStoresDeclaredNamespace() {
        LimiterPolicyBindingRepository bindings = mock(LimiterPolicyBindingRepository.class);
        LimiterPolicyPort port = mock(LimiterPolicyPort.class);
        LimiterPolicyPort.PolicyKey key = new LimiterPolicyPort.PolicyKey(
                "orders", "read", null, "customer-1");
        LimiterPolicyPort.PolicyResult found = new LimiterPolicyPort.PolicyResult(
                new LimiterPolicyPort.PolicyKey("orders", "read", "service-ns", "customer-1"),
                "rule-1", 3L, 7L, true);
        LimiterPolicyPort.PolicyResult disabled = new LimiterPolicyPort.PolicyResult(
                found.getKey(), "rule-1", 4L, 8L, false);
        LimiterPolicyBindingEntity binding = new LimiterPolicyBindingEntity(
                "binding-1", "grant-1", 1L,
                new LimiterPolicyPort.PolicyRequest(key,
                        Collections.singletonList(new LimiterPolicyPort.Limit(10L, 1L, "MINUTES")),
                        true, "op-1"), "{}");
        when(bindings.findByGrantId("grant-1")).thenReturn(Collections.singletonList(binding));
        when(port.find(any(LimiterPolicyPort.PolicyKey.class))).thenReturn(found);
        when(port.setEnabled(any(LimiterPolicyPort.PolicyKey.class), any(String.class), any(Long.TYPE),
                org.mockito.ArgumentMatchers.eq(false), any(String.class))).thenReturn(disabled);
        when(bindings.save(any(LimiterPolicyBindingEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        LimiterPolicyProjectionService service = new LimiterPolicyProjectionService(bindings);
        ReflectionTestUtils.setField(service, "port", port);

        service.disable(grant("[]"));

        assertThat(binding.getState()).isEqualTo(LimiterPolicyBindingState.DISABLED);
        assertThat(binding.getNamespace()).isEqualTo("service-ns");
        verify(port).setEnabled(any(LimiterPolicyPort.PolicyKey.class),
                org.mockito.ArgumentMatchers.eq("rule-1"), org.mockito.ArgumentMatchers.eq(3L),
                org.mockito.ArgumentMatchers.eq(false), any(String.class));
    }

    @Test
    void disableUncertainBindingDoesNotRewriteAlreadyDisabledRule() {
        LimiterPolicyBindingRepository bindings = mock(LimiterPolicyBindingRepository.class);
        LimiterPolicyPort port = mock(LimiterPolicyPort.class);
        LimiterPolicyPort.PolicyKey key = new LimiterPolicyPort.PolicyKey(
                "orders", "read", null, "customer-1");
        LimiterPolicyBindingEntity binding = new LimiterPolicyBindingEntity(
                "binding-1", "grant-1", 1L,
                new LimiterPolicyPort.PolicyRequest(key,
                        Collections.singletonList(new LimiterPolicyPort.Limit(10L, 1L, "MINUTES")),
                        true, "op-1"), "{}");
        binding.failed(LimiterPolicyBindingState.UNCERTAIN, "transport");
        when(bindings.findByGrantId("grant-1")).thenReturn(Collections.singletonList(binding));
        when(bindings.save(any(LimiterPolicyBindingEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(port.find(any(LimiterPolicyPort.PolicyKey.class))).thenReturn(new LimiterPolicyPort.PolicyResult(
                new LimiterPolicyPort.PolicyKey("orders", "read", "service-ns", "customer-1"),
                "rule-1", 4L, LimiterPolicyPort.PolicyResult.NO_REVISION, false));
        LimiterPolicyProjectionService service = new LimiterPolicyProjectionService(bindings);
        ReflectionTestUtils.setField(service, "port", port);

        service.disable(grant("[]"));

        assertThat(binding.getState()).isEqualTo(LimiterPolicyBindingState.DISABLED);
        assertThat(binding.getNamespace()).isEqualTo("service-ns");
        verify(port, org.mockito.Mockito.never()).setEnabled(any(LimiterPolicyPort.PolicyKey.class),
                any(String.class), any(Long.TYPE), org.mockito.ArgumentMatchers.eq(false), any(String.class));
    }

    @Test
    void disableRejectsCasResponseForAnotherRemoteRule() {
        LimiterPolicyBindingRepository bindings = mock(LimiterPolicyBindingRepository.class);
        LimiterPolicyPort port = mock(LimiterPolicyPort.class);
        LimiterPolicyPort.PolicyKey key = new LimiterPolicyPort.PolicyKey(
                "orders", "read", null, "customer-1");
        LimiterPolicyPort.PolicyResult found = new LimiterPolicyPort.PolicyResult(
                new LimiterPolicyPort.PolicyKey("orders", "read", "service-ns", "customer-1"),
                "rule-1", 3L, 7L, true);
        LimiterPolicyPort.PolicyResult wrongRule = new LimiterPolicyPort.PolicyResult(
                found.getKey(), "rule-2", 4L, 8L, false);
        LimiterPolicyBindingEntity binding = new LimiterPolicyBindingEntity(
                "binding-1", "grant-1", 1L,
                new LimiterPolicyPort.PolicyRequest(key,
                        Collections.singletonList(new LimiterPolicyPort.Limit(10L, 1L, "MINUTES")),
                        true, "op-1"), "{}");
        when(bindings.findByGrantId("grant-1")).thenReturn(Collections.singletonList(binding));
        when(port.find(any(LimiterPolicyPort.PolicyKey.class))).thenReturn(found);
        when(port.setEnabled(any(LimiterPolicyPort.PolicyKey.class), any(String.class), any(Long.TYPE),
                org.mockito.ArgumentMatchers.eq(false), any(String.class))).thenReturn(wrongRule);
        when(bindings.save(any(LimiterPolicyBindingEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        LimiterPolicyProjectionService service = new LimiterPolicyProjectionService(bindings);
        ReflectionTestUtils.setField(service, "port", port);

        assertThatThrownBy(() -> service.disable(grant("[]")))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(ex -> ((LicenseHttpException) ex).getStatus())
                .isEqualTo(503);
        assertThat(binding.getState()).isEqualTo(LimiterPolicyBindingState.FAILED);
    }

    @Test
    void enableRejectsSameCustomerResourceOwnedByAnotherGrant() {
        LimiterPolicyBindingRepository bindings = mock(LimiterPolicyBindingRepository.class);
        LimiterPolicyPort port = mock(LimiterPolicyPort.class);
        when(bindings.findByCustomerIdAndServiceCodeAndResourceCode("customer-1", "orders", "read"))
                .thenReturn(Collections.singletonList(new LimiterPolicyBindingEntity(
                        "binding-other", "grant-other", 1L,
                        new LimiterPolicyPort.PolicyRequest(
                                new LimiterPolicyPort.PolicyKey("orders", "read", null, "customer-1"),
                                Collections.singletonList(new LimiterPolicyPort.Limit(10L, 1L, "MINUTES")),
                                true, "op-other"), "{}")));
        LimiterPolicyProjectionService service = new LimiterPolicyProjectionService(bindings);
        ReflectionTestUtils.setField(service, "port", port);

        assertThatThrownBy(() -> service.enable(grant("[{\"serviceCode\":\"orders\",\"resourceCode\":\"read\","
                + "\"limits\":[{\"count\":10,\"window\":1,\"unit\":\"MINUTES\"}]}]")))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(ex -> ((LicenseHttpException) ex).getStatus())
                .isEqualTo(409);
        verify(port, org.mockito.Mockito.never()).upsert(any(LimiterPolicyPort.PolicyRequest.class));
    }

    @Test
    void enableRejectsDuplicateResourceInStoredSnapshot() {
        LimiterPolicyBindingRepository bindings = mock(LimiterPolicyBindingRepository.class);
        LimiterPolicyProjectionService service = new LimiterPolicyProjectionService(bindings);
        ReflectionTestUtils.setField(service, "port", mock(LimiterPolicyPort.class));

        assertThatThrownBy(() -> service.enable(grant("[{\"serviceCode\":\"orders\",\"resourceCode\":\"read\","
                + "\"limits\":[{\"count\":10,\"window\":1,\"unit\":\"MINUTES\"}]},"
                + "{\"serviceCode\":\"orders\",\"resourceCode\":\"read\","
                + "\"limits\":[{\"count\":20,\"window\":1,\"unit\":\"MINUTES\"}]}]")))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(ex -> ((LicenseHttpException) ex).getStatus())
                .isEqualTo(422);
    }

    @Test
    void disableSkipsPortWhenAllBindingsAreAlreadyDisabled() {
        LimiterPolicyBindingRepository bindings = mock(LimiterPolicyBindingRepository.class);
        LimiterPolicyBindingEntity binding = new LimiterPolicyBindingEntity(
                "binding-1", "grant-1", 1L,
                new LimiterPolicyPort.PolicyRequest(
                        new LimiterPolicyPort.PolicyKey("orders", "read", null, "customer-1"),
                        Collections.singletonList(new LimiterPolicyPort.Limit(10L, 1L, "MINUTES")),
                        true, "op-1"), "{}");
        binding.failed(LimiterPolicyBindingState.DISABLED, null);
        when(bindings.findByGrantId("grant-1")).thenReturn(Collections.singletonList(binding));
        LimiterPolicyProjectionService service = new LimiterPolicyProjectionService(bindings);

        service.disable(grant("[]"));
        assertThat(binding.getState()).isEqualTo(LimiterPolicyBindingState.DISABLED);
    }

    private static GrantRevisionEntity grant(String policies) {
        return new GrantRevisionEntity("grant-1", "customer-1", "product-1", "SERVICE",
                "ASSIGNED_SUBJECTS", null, "ONLINE", null, "model-1", "{}", policies);
    }
}
