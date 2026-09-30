package io.github.surezzzzzz.sdk.auth.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.server.configuration.SimpleAkskServerProperties;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.AkskSelfServiceClientController;
import io.github.surezzzzzz.sdk.auth.aksk.server.entity.AkskOwnerAuthorizationCursorEntity;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.*;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.*;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.model.OwnerAuthorizationCandidate;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 候选目录降级标记（3.2.1）：同步租约过期时空候选可被前端识别为"读不到"而非"没授权"。
 */
class AkskSelfServiceCandidatesDegradedHeaderTest {

    @Test
    void leaseExpiredOnlyWhenCursorExistsAndOutOfGrace() {
        // 游标不存在（继承链路从未启用）：不算降级，空候选即真实空态
        assertFalse(service(cursor(null)).isSynchronizationLeaseExpired());

        // 租约仍在宽限内：健康
        AkskOwnerAuthorizationCursorEntity valid = new AkskOwnerAuthorizationCursorEntity();
        valid.setSynchronizationLeaseUntil(Instant.now().plusSeconds(30));
        assertFalse(service(cursor(valid)).isSynchronizationLeaseExpired());

        // 游标存在但租约已过期：降级
        AkskOwnerAuthorizationCursorEntity expired = new AkskOwnerAuthorizationCursorEntity();
        expired.setSynchronizationLeaseUntil(Instant.now().minusSeconds(1));
        assertTrue(service(cursor(expired)).isSynchronizationLeaseExpired());
    }

    @Test
    void candidatesResponseCarriesDegradedHeaderOnlyWhenLeaseExpired() {
        OwnerAuthorizationCandidate candidate = new OwnerAuthorizationCandidate(7L, "demo-app", "demo");
        AkskSelfServicePrincipal owner = new AkskSelfServicePrincipal("local-iam", "2388256181993590", "req-1");

        AkskOwnerAuthorizationCursorEntity expired = new AkskOwnerAuthorizationCursorEntity();
        expired.setSynchronizationLeaseUntil(Instant.now().minusSeconds(1));
        AkskOwnerAuthorizationProjectionService degradedService = service(cursor(expired));

        AkskOwnerInheritedBindingService bindingService = mock(AkskOwnerInheritedBindingService.class);
        when(bindingService.listCandidates(owner)).thenReturn(Collections.singletonList(candidate));
        ResponseEntity<List<OwnerAuthorizationCandidate>> degraded =
                controller(owner, bindingService, degradedService).candidates();

        assertEquals(200, degraded.getStatusCodeValue());
        assertEquals("true", degraded.getHeaders().getFirst(AkskSelfServiceClientController.HEADER_PROJECTION_DEGRADED));
        assertEquals(1, degraded.getBody().size());

        AkskOwnerAuthorizationCursorEntity valid = new AkskOwnerAuthorizationCursorEntity();
        valid.setSynchronizationLeaseUntil(Instant.now().plusSeconds(30));
        ResponseEntity<List<OwnerAuthorizationCandidate>> healthy =
                controller(owner, bindingService, service(cursor(valid))).candidates();

        assertEquals(200, healthy.getStatusCodeValue());
        assertNull(healthy.getHeaders().getFirst(AkskSelfServiceClientController.HEADER_PROJECTION_DEGRADED));
    }

    @Test
    void anonymousOwnerKeepsForbiddenWithoutHeader() {
        AkskSelfServicePrincipalResolver resolver = mock(AkskSelfServicePrincipalResolver.class);
        when(resolver.resolve()).thenReturn(null);

        ResponseEntity<List<OwnerAuthorizationCandidate>> response = new AkskSelfServiceClientController(
                resolver, mock(AkskOwnerInheritedBindingService.class), mock(AkskClientOwnerBindingRepository.class),
                mock(ClientManagementService.class), mock(AkskSelfServiceLifecycleService.class),
                service(cursor(null))).candidates();

        assertEquals(403, response.getStatusCodeValue());
        assertNull(response.getHeaders().getFirst(AkskSelfServiceClientController.HEADER_PROJECTION_DEGRADED));
    }

    private AkskOwnerAuthorizationCursorRepository cursor(AkskOwnerAuthorizationCursorEntity entity) {
        AkskOwnerAuthorizationCursorRepository repository = mock(AkskOwnerAuthorizationCursorRepository.class);
        when(repository.findById(AkskOwnerAuthorizationProjectionService.STREAM_KEY))
                .thenReturn(Optional.ofNullable(entity));
        return repository;
    }

    private AkskOwnerAuthorizationProjectionService service(AkskOwnerAuthorizationCursorRepository cursorRepository) {
        return new AkskOwnerAuthorizationProjectionService(new SimpleAkskServerProperties(), cursorRepository,
                mock(AkskOwnerAuthorizationInboxRepository.class),
                mock(AkskOwnerAuthorizationOwnerStateRepository.class),
                mock(AkskOwnerAuthorizationTargetApplicationStateRepository.class),
                mock(AkskOwnerAuthorizationProjectionRepository.class));
    }

    private AkskSelfServiceClientController controller(AkskSelfServicePrincipal owner,
                                                       AkskOwnerInheritedBindingService bindingService,
                                                       AkskOwnerAuthorizationProjectionService projectionService) {
        AkskSelfServicePrincipalResolver resolver = mock(AkskSelfServicePrincipalResolver.class);
        when(resolver.resolve()).thenReturn(owner);
        return new AkskSelfServiceClientController(resolver, bindingService,
                mock(AkskClientOwnerBindingRepository.class), mock(ClientManagementService.class),
                mock(AkskSelfServiceLifecycleService.class), projectionService);
    }
}
