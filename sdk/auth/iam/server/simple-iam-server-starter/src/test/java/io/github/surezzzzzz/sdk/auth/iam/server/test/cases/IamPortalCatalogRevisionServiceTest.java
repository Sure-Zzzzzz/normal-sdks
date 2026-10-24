package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.portal.IamPortalCatalogRevisionEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.portal.IamPortalCatalogRevisionRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.portal.IamPortalCatalogRevisionBumpedEvent;
import io.github.surezzzzzz.sdk.auth.iam.server.service.portal.IamPortalCatalogRevisionService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IamPortalCatalogRevisionServiceTest {

    @Test
    void bumpsOnceAndPublishesOneObservationEventPerTransaction() {
        IamPortalCatalogRevisionEntity entity = new IamPortalCatalogRevisionEntity();
        entity.setId(IamPortalCatalogRevisionEntity.SINGLETON_ID);
        entity.setRevision(7L);

        IamPortalCatalogRevisionRepository repository = mock(IamPortalCatalogRevisionRepository.class);
        when(repository.findByIdForUpdate(IamPortalCatalogRevisionEntity.SINGLETON_ID)).thenReturn(entity);
        when(repository.saveAndFlush(entity)).thenReturn(entity);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        IamPortalCatalogRevisionService service = new IamPortalCatalogRevisionService(repository, eventPublisher);

        TransactionSynchronizationManager.initSynchronization();
        try {
            assertEquals(8L, service.bump("PORTAL_CONFIGURATION_CHANGED", "sample-app"));
            assertEquals(8L, service.bump("PORTAL_APPLICATION_ORDER_CHANGED", "other-app"));

            verify(repository, times(1)).findByIdForUpdate(IamPortalCatalogRevisionEntity.SINGLETON_ID);
            verify(repository, times(1)).saveAndFlush(entity);
            verify(eventPublisher, times(1)).publishEvent(any(IamPortalCatalogRevisionBumpedEvent.class));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void observationDigestDoesNotExposeApplicationCode() {
        IamPortalCatalogRevisionEntity entity = new IamPortalCatalogRevisionEntity();
        entity.setId(IamPortalCatalogRevisionEntity.SINGLETON_ID);
        entity.setRevision(1L);

        IamPortalCatalogRevisionRepository repository = mock(IamPortalCatalogRevisionRepository.class);
        when(repository.findByIdForUpdate(IamPortalCatalogRevisionEntity.SINGLETON_ID)).thenReturn(entity);
        when(repository.saveAndFlush(entity)).thenReturn(entity);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        IamPortalCatalogRevisionService service = new IamPortalCatalogRevisionService(repository, eventPublisher);

        service.bump("PORTAL_CONFIGURATION_CHANGED", "sample-app");

        org.mockito.ArgumentCaptor<IamPortalCatalogRevisionBumpedEvent> captor =
                org.mockito.ArgumentCaptor.forClass(IamPortalCatalogRevisionBumpedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        IamPortalCatalogRevisionBumpedEvent event = captor.getValue();
        assertNotNull(event.getApplicationCodeDigest());
        assertEquals(64, event.getApplicationCodeDigest().length());
    }
}
