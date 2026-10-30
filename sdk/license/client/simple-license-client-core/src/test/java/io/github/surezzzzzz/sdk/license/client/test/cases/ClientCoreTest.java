package io.github.surezzzzzz.sdk.license.client.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.surezzzzzz.sdk.license.client.model.ClientDecision;
import io.github.surezzzzzz.sdk.license.client.model.ClientDecisionBinding;
import io.github.surezzzzzz.sdk.license.client.model.ClientLease;
import io.github.surezzzzzz.sdk.license.client.model.ClientReservation;
import io.github.surezzzzzz.sdk.license.client.model.AuthorizationRequest;
import io.github.surezzzzzz.sdk.license.client.model.GrantCandidate;
import io.github.surezzzzzz.sdk.license.client.model.TrustMaterialSnapshot;
import io.github.surezzzzzz.sdk.license.client.support.ClientResponseHelper;
import io.github.surezzzzzz.sdk.license.client.spi.ClientResponseValidator;
import io.github.surezzzzzz.sdk.license.client.spi.LicenseServerClient;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * client-core 契约断言：判定响应关联校验、防御复制、候选/租约/占用/信任资料值对象。
 *
 * @author surezzzzzz
 */
class ClientCoreTest {

    @Test
    void validatorRejectsMismatchedAndMalformedResponses() {
        ClientDecision missingMetadata = ClientDecision.allow("data.export", 1L, "ACTIVE", null, null);
        // 兼容入口也不能消费无授权关联的 ALLOW；完整请求入口另行校验产品/授权/对象一致性。
        assertThatThrownBy(() -> ClientResponseValidator.validateDecision(missingMetadata, "data.export"))
                .isInstanceOf(ClientResponseValidator.ProtocolViolationException.class);
        assertThatThrownBy(() -> ClientResponseValidator.validateDecision(missingMetadata,
                AuthorizationRequest.of("product-1", "grant-1", "PERSON", "data.export", null)))
                .isInstanceOf(ClientResponseValidator.ProtocolViolationException.class);
        ClientDecision ok = ClientDecision.allow("data.export", 1L, "ACTIVE", null, 0L, "request-1",
                null, null, "product-1", "grant-1",
                ClientDecisionBinding.personService("PERSON", "iam", "user-1", null, null), 1L, 1L);
        AuthorizationRequest request = AuthorizationRequest.of("product-1", "grant-1", "PERSON",
                "data.export", null);
        assertThat(ClientResponseValidator.validateDecision(ok, request)).isSameAs(ok);
        ClientDecision wrongAction = ClientDecision.allow("other.action", 1L, "ACTIVE", null, 0L, "request-1",
                null, null, "product-1", "grant-1",
                ClientDecisionBinding.personService("PERSON", "iam", "user-1", null, null), 1L, 1L);
        assertThatThrownBy(() -> ClientResponseValidator.validateDecision(wrongAction, "data.export"))
                .isInstanceOf(ClientResponseValidator.ProtocolViolationException.class);
        assertThatThrownBy(() -> ClientResponseValidator.validateDecision(null, "x"))
                .isInstanceOf(ClientResponseValidator.ProtocolViolationException.class);
    }

    @Test
    void validatorBindsKnownAllowToTheRequestedGrantAndObjectShape() {
        AuthorizationRequest request = AuthorizationRequest.assigned("product-1", "grant-1", "SERVICE",
                "run", "assignment-1", null);
        ClientDecision valid = ClientDecision.allow("run", 1L, "ACTIVE", null, 0L, "request-1",
                null, null, "product-1", "grant-1",
                ClientDecisionBinding.personService("SERVICE", "iam", "svc-1", "assignment-1", 1L),
                1L, 1L);
        assertThat(ClientResponseValidator.validateDecision(valid, request)).isSameAs(valid);

        ClientDecision wrongProduct = ClientDecision.allow("run", 1L, "ACTIVE", null, 0L, "request-1",
                null, null, "other-product", "grant-1",
                ClientDecisionBinding.personService("SERVICE", "iam", "svc-1", "assignment-1", 1L),
                1L, 1L);
        assertThatThrownBy(() -> ClientResponseValidator.validateDecision(wrongProduct, request))
                .isInstanceOf(ClientResponseValidator.ProtocolViolationException.class);

        ClientDecision wrongAssignment = ClientDecision.allow("run", 1L, "ACTIVE", null, 0L, "request-1",
                null, null, "product-1", "grant-1",
                ClientDecisionBinding.personService("SERVICE", "iam", "svc-1", "other-assignment", 1L),
                1L, 1L);
        assertThatThrownBy(() -> ClientResponseValidator.validateDecision(wrongAssignment, request))
                .isInstanceOf(ClientResponseValidator.ProtocolViolationException.class);

        AuthorizationRequest memberRequest = AuthorizationRequest.of("product-1", "grant-1", "SERVICE",
                "run", null);
        ClientDecision assignmentBoundResponse = ClientDecision.allow("run", 1L, "ACTIVE", null, 0L,
                "request-2", null, null, "product-1", "grant-1",
                ClientDecisionBinding.personService("SERVICE", "iam", "svc-1", "assignment-1", 1L),
                1L, 1L);
        assertThatThrownBy(() -> ClientResponseValidator.validateDecision(assignmentBoundResponse, memberRequest))
                .isInstanceOf(ClientResponseValidator.ProtocolViolationException.class);
    }

    @Test
    void fenceReferencesAndSnapshotDefensivelyCopied() {
        List<String> fences = new ArrayList<String>(Arrays.asList("f1"));
        ClientDecision decision = ClientDecision.allow("a", 1L, "ACTIVE", null, fences);
        fences.add("f2");
        assertThat(decision.getReservationFenceReferences()).hasSize(1);

        List<String> parts = new ArrayList<String>(Arrays.asList("part-1"));
        TrustMaterialSnapshot snapshot = new TrustMaterialSnapshot(2L, 1780000000L, "snap", parts, null);
        parts.add("part-2");
        assertThat(snapshot.getRevocationPartJws()).hasSize(1);
        assertThatThrownBy(() -> snapshot.getRevocationPartJws().add("part-3"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(snapshot.getRevocationPartJws()).hasSize(1);
    }

    @Test
    void authorizationFactsAreRecursivelyDefensivelyCopied() {
        Map<String, Object> nested = new HashMap<String, Object>();
        List<Object> values = new ArrayList<Object>(Arrays.<Object>asList("before"));
        nested.put("values", values);
        Map<String, Object> facts = new HashMap<String, Object>();
        facts.put("nested", nested);

        AuthorizationRequest request = AuthorizationRequest.of("product-1", "grant-1", "PERSON",
                "data.export", facts);
        values.set(0, "after");
        nested.put("added", true);

        Map<?, ?> copiedNested = (Map<?, ?>) request.getFacts().get("nested");
        assertThat(copiedNested.containsKey("added")).isFalse();
        assertThat(((List<?>) copiedNested.get("values")).get(0)).isEqualTo("before");
        assertThatThrownBy(() -> ((Map<Object, Object>) copiedNested).put("blocked", true))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> ((List<Object>) copiedNested.get("values")).add("blocked"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void trustMaterialRejectsUnknownAndInvalidPartCounts() {
        final Map<String, Object> unknownField = trustMaterialResponse();
        unknownField.put("unexpected", true);
        assertThatThrownBy(() -> ClientResponseHelper.trustMaterial(unknownField, "issuer-1", "product-1"))
                .isInstanceOf(ClientResponseValidator.ProtocolViolationException.class);

        final Map<String, Object> emptyParts = trustMaterialResponse();
        emptyParts.put("revocationPartJws", new ArrayList<String>());
        assertThatThrownBy(() -> ClientResponseHelper.trustMaterial(emptyParts, "issuer-1", "product-1"))
                .isInstanceOf(ClientResponseValidator.ProtocolViolationException.class);

        List<String> tooMany = new ArrayList<String>();
        for (int i = 0; i < 257; i++) {
            tooMany.add("part-" + i);
        }
        final Map<String, Object> tooManyParts = trustMaterialResponse();
        tooManyParts.put("revocationPartJws", tooMany);
        assertThatThrownBy(() -> ClientResponseHelper.trustMaterial(tooManyParts, "issuer-1", "product-1"))
                .isInstanceOf(ClientResponseValidator.ProtocolViolationException.class);

        assertThat(ClientResponseHelper.trustMaterial(trustMaterialResponse(), "issuer-1", "product-1")
                .getRevocationPartJws()).hasSize(1);
    }

    private Map<String, Object> trustMaterialResponse() {
        Map<String, Object> response = new HashMap<String, Object>();
        response.put("issuerId", "issuer-1");
        response.put("productId", "product-1");
        response.put("sequence", 1L);
        response.put("issuedAt", 1780000000L);
        response.put("snapshotId", "snapshot-1");
        response.put("manifestJws", "manifest-jws");
        response.put("revocationPartJws", Arrays.asList("part-1"));
        response.put("trustedPublicKeysJson", Arrays.asList("key-1"));
        return response;
    }

    @Test
    void clientInterfaceOperationsCallableWithBothForms() {
        AtomicInteger calls = new AtomicInteger();
        LicenseServerClient client = new LicenseServerClient() {
            @Override
            public ClientDecision authorize(
                    io.github.surezzzzzz.sdk.license.client.model.AuthorizationRequest request) {
                calls.incrementAndGet();
                return authorize(request.getProductId(), request.getGrantId(), request.getSubjectKind(),
                        request.getAction(), request.getActivationId(), request.getAssignmentId(),
                        request.getBusinessOperationId(), request.getFacts());
            }

            @Override
            public ClientDecision authorize(String productId, String grantId, String subjectKind,
                                            String action, String activationId, String assignmentId,
                                            String businessOperationId, Map<String, Object> facts) {
                calls.incrementAndGet();
                return ClientDecision.allow(action, 1L, "ACTIVE", null, null);
            }

            @Override
            public List<GrantCandidate> discoverGrants(String productId, String subjectKind, String action) {
                calls.incrementAndGet();
                return Arrays.asList(new GrantCandidate("g1", "ONLINE", 1L, "ACTIVE"));
            }

            @Override
            public ClientLease leaseRenew(String grantId, String subjectKind) {
                calls.incrementAndGet();
                return new ClientLease("l1", "jws", 1780000300L, 1780000600L, 1780000900L);
            }

            @Override
            public ClientReservation reserve(
                    io.github.surezzzzzz.sdk.license.client.model.QuotaRequest request) {
                calls.incrementAndGet();
                return reserve(request.getGrantId(), request.getMetric(), request.getQuantity(),
                        request.getBusinessOperationId());
            }

            @Override
            public ClientReservation reserve(String grantId, String metric, long quantity,
                                             String businessOperationId) {
                calls.incrementAndGet();
                return new ClientReservation("r1", metric, quantity, 1L, 1780000900L);
            }

            @Override
            public void release(String reservationId) {
                calls.incrementAndGet();
            }

            @Override
            public ClientReservation consume(
                    io.github.surezzzzzz.sdk.license.client.model.QuotaRequest request) {
                calls.incrementAndGet();
                return consume(request.getGrantId(), request.getMetric(), request.getQuantity(),
                        request.getBusinessOperationId());
            }

            @Override
            public ClientReservation consume(String grantId, String metric, long quantity,
                                             String businessOperationId) {
                calls.incrementAndGet();
                return new ClientReservation("c1", metric, quantity, 1L, 1780000900L);
            }

            @Override
            public TrustMaterialSnapshot fetchTrustMaterial(String issuerId, String productId,
                                                            long knownSequence) {
                calls.incrementAndGet();
                return new TrustMaterialSnapshot(knownSequence + 1, 1780000000L, "snap", null, null);
            }
        };
        Map<String, Object> facts = new HashMap<String, Object>();
        // 两种形态等价可达：位置参数与值对象各自调用一遍判定/预占/扣减
        assertThat(client.authorize("p", "g", "PERSON", "act", null, null, null, facts).isAllowed()).isTrue();
        assertThat(client.authorize(io.github.surezzzzzz.sdk.license.client.model.AuthorizationRequest
                .of("p", "g", "PERSON", "act", facts)).isAllowed()).isTrue();
        assertThat(client.discoverGrants("p", "PERSON", "act")).hasSize(1);
        assertThat(client.leaseRenew("g", "PERSON").getLeaseUntil()).isEqualTo(1780000600L);
        assertThat(client.reserve("g", "m", 1L, "op").getReservationId()).isEqualTo("r1");
        assertThat(client.reserve(new io.github.surezzzzzz.sdk.license.client.model.QuotaRequest(
                "g", "m", 2L, "op-2")).getQuantity()).isEqualTo(2L);
        client.release("r1");
        assertThat(client.consume("g", "m", 1L, "op").getQuantity()).isEqualTo(1L);
        assertThat(client.consume(new io.github.surezzzzzz.sdk.license.client.model.QuotaRequest(
                "g", "m", 3L, "op-3")).getQuantity()).isEqualTo(3L);
        assertThat(client.fetchTrustMaterial("i", "p", 5L).getSequence()).isEqualTo(6L);
        // 值对象形态经转发到位置形态：authorize/reserve/consume 值对象各计 2 次（自身+转发）
        assertThat(calls.get()).isEqualTo(13);
    }
}
