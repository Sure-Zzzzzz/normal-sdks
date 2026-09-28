package io.github.surezzzzzz.sdk.auth.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.server.configuration.SimpleAkskServerProperties;
import io.github.surezzzzzz.sdk.auth.aksk.server.entity.*;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.*;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.AkskOwnerAuthorizationProjectionService;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.model.OwnerAuthorizationChange;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * IAM 授权 journal 的 AKSK 本地投影边界测试。
 */
@Slf4j
class AkskOwnerAuthorizationProjectionServiceTest {

    @Test
    void consumedSequenceMustOnlyReplayOriginalEvent() {
        AkskOwnerAuthorizationCursorRepository cursorRepository = mock(AkskOwnerAuthorizationCursorRepository.class);
        AkskOwnerAuthorizationInboxRepository inboxRepository = mock(AkskOwnerAuthorizationInboxRepository.class);
        AkskOwnerAuthorizationCursorEntity cursor = new AkskOwnerAuthorizationCursorEntity();
        cursor.setLastSourceSequence(5L);
        AkskOwnerAuthorizationInboxEntity inbox = new AkskOwnerAuthorizationInboxEntity();
        inbox.setEventId("event-5");
        inbox.setSourceSequence(5L);
        when(cursorRepository.findById(AkskOwnerAuthorizationProjectionService.STREAM_KEY))
                .thenReturn(Optional.of(cursor));
        when(inboxRepository.findById("event-5")).thenReturn(Optional.of(inbox));

        AkskOwnerAuthorizationProjectionService service = service(cursorRepository, inboxRepository,
                mock(AkskOwnerAuthorizationOwnerStateRepository.class),
                mock(AkskOwnerAuthorizationTargetApplicationStateRepository.class),
                mock(AkskOwnerAuthorizationProjectionRepository.class));

        assertTrue(service.apply(new OwnerAuthorizationChange(5L, "event-5", "OWNER_STATE",
                Collections.<String, Object>emptyMap())));
        assertFalse(service.apply(new OwnerAuthorizationChange(5L, "unexpected-event", "OWNER_STATE",
                Collections.<String, Object>emptyMap())));
        log.info("已消费序号仅接受原 eventId 重放：sequence={}", cursor.getLastSourceSequence());
    }

    @Test
    void activeProjectionMustUseTargetCurrentApplicationEpochWithoutRewritingAuthorizationContent() {
        AkskOwnerAuthorizationProjectionRepository projectionRepository = mock(AkskOwnerAuthorizationProjectionRepository.class);
        AkskOwnerAuthorizationOwnerStateRepository ownerStateRepository = mock(AkskOwnerAuthorizationOwnerStateRepository.class);
        AkskOwnerAuthorizationTargetApplicationStateRepository targetStateRepository = mock(AkskOwnerAuthorizationTargetApplicationStateRepository.class);
        AkskOwnerAuthorizationProjectionEntity projection = projection(5L, 2L, 3L);
        AkskOwnerAuthorizationOwnerStateEntity owner = new AkskOwnerAuthorizationOwnerStateEntity();
        owner.setActive(1);
        owner.setOwnerSecurityEpoch(5L);
        AkskOwnerAuthorizationTargetApplicationStateEntity target = new AkskOwnerAuthorizationTargetApplicationStateEntity();
        target.setActive(1);
        target.setOwnerInheritedAccessEpoch(2L);
        target.setApplicationAuthorizationEpoch(9L);
        when(projectionRepository.findById("local-iam:1001:7")).thenReturn(Optional.of(projection));
        when(ownerStateRepository.findById("local-iam:1001")).thenReturn(Optional.of(owner));
        when(targetStateRepository.findById(7L)).thenReturn(Optional.of(target));

        AkskOwnerAuthorizationProjectionService service = service(mock(AkskOwnerAuthorizationCursorRepository.class),
                mock(AkskOwnerAuthorizationInboxRepository.class), ownerStateRepository, targetStateRepository,
                projectionRepository);

        assertSame(projection, service.findActiveProjection("local-iam", "1001", 7L));
        assertEquals(Long.valueOf(9L), service.currentApplicationAuthorizationEpoch(7L));
    }

    @Test
    void concurrentWorkerLeaseLossMustBeTreatedAsNormalCompetition() {
        AkskOwnerAuthorizationCursorRepository cursorRepository = mock(AkskOwnerAuthorizationCursorRepository.class);
        AkskOwnerAuthorizationCursorEntity cursor = new AkskOwnerAuthorizationCursorEntity();
        cursor.setLastSourceSequence(0L);
        when(cursorRepository.findById(AkskOwnerAuthorizationProjectionService.STREAM_KEY))
                .thenReturn(Optional.of(cursor));
        when(cursorRepository.tryClaimWorkerLease(
                org.mockito.ArgumentMatchers.eq(AkskOwnerAuthorizationProjectionService.STREAM_KEY),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(0);

        AkskOwnerAuthorizationProjectionService service = service(cursorRepository,
                mock(AkskOwnerAuthorizationInboxRepository.class),
                mock(AkskOwnerAuthorizationOwnerStateRepository.class),
                mock(AkskOwnerAuthorizationTargetApplicationStateRepository.class),
                mock(AkskOwnerAuthorizationProjectionRepository.class));

        assertEquals(null, service.claimWorkerLease());
        verify(cursorRepository).tryClaimWorkerLease(
                org.mockito.ArgumentMatchers.eq(AkskOwnerAuthorizationProjectionService.STREAM_KEY),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private AkskOwnerAuthorizationProjectionService service(AkskOwnerAuthorizationCursorRepository cursorRepository,
                                                            AkskOwnerAuthorizationInboxRepository inboxRepository,
                                                            AkskOwnerAuthorizationOwnerStateRepository ownerStateRepository,
                                                            AkskOwnerAuthorizationTargetApplicationStateRepository targetStateRepository,
                                                            AkskOwnerAuthorizationProjectionRepository projectionRepository) {
        return new AkskOwnerAuthorizationProjectionService(new SimpleAkskServerProperties(), cursorRepository,
                inboxRepository, ownerStateRepository, targetStateRepository, projectionRepository);
    }

    private AkskOwnerAuthorizationProjectionEntity projection(Long ownerEpoch, Long inheritedEpoch,
                                                              Long projectionEpoch) {
        AkskOwnerAuthorizationProjectionEntity projection = new AkskOwnerAuthorizationProjectionEntity();
        projection.setProjectionKey("local-iam:1001:7");
        projection.setActive(1);
        projection.setOwnerSecurityEpoch(ownerEpoch);
        projection.setApplicationAuthorizationEpoch(3L);
        projection.setOwnerInheritedAccessEpoch(inheritedEpoch);
        projection.setProjectionAccessEpoch(projectionEpoch);
        return projection;
    }
}
