package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.SimpleDataPermissionConstant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataPermissionRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.IamPortalMachineDirectoryConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.response.PortalMachineApplicationPageResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.portal.IamTrustedApplicationPortalEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.trustedapplication.IamTrustedApplicationEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.portal.IamTrustedApplicationPortalRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.trustedapplication.IamTrustedApplicationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.portal.IamPortalCatalogRevisionService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.portal.IamPortalMachineApplicationDirectoryService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.portal.IamPortalMachineDirectoryUnavailableException;
import io.github.surezzzzzz.sdk.auth.iam.server.service.trustedapplication.TrustedApplicationBuiltInResolver;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Portal 机器路由目录的顺序、revision 和页码边界测试。
 *
 * @author surezzzzzz
 */
class IamPortalMachineApplicationDirectoryServiceTest {

    @Test
    void keepsConfiguredPortalOrderInsteadOfSortingByApplicationCode() {
        IamTrustedApplicationPortalRepository portalRepository = mock(IamTrustedApplicationPortalRepository.class);
        IamTrustedApplicationRepository applicationRepository = mock(IamTrustedApplicationRepository.class);
        IamPortalCatalogRevisionService revisionService = mock(IamPortalCatalogRevisionService.class);
        TrustedApplicationBuiltInResolver builtInResolver = mock(TrustedApplicationBuiltInResolver.class);
        IamPortalMachineApplicationDirectoryService service = new IamPortalMachineApplicationDirectoryService(
                portalRepository, applicationRepository, revisionService, builtInResolver);

        IamTrustedApplicationPortalEntity firstPortal = portal(1L, 1L, "/z");
        IamTrustedApplicationPortalEntity secondPortal = portal(2L, 2L, "/a");
        IamTrustedApplicationEntity firstApplication = application(1L, "z-app");
        IamTrustedApplicationEntity secondApplication = application(2L, "a-app");
        when(portalRepository.findByEnabledOrderBySortOrderAscApplicationIdAsc(SimpleIamServerConstant.STATUS_ACTIVE))
                .thenReturn(Arrays.asList(firstPortal, secondPortal));
        when(applicationRepository.findAllById(anyList()))
                .thenReturn(Arrays.asList(firstApplication, secondApplication));
        when(builtInResolver.isBuiltIn(firstApplication)).thenReturn(false);
        when(builtInResolver.isBuiltIn(secondApplication)).thenReturn(false);
        when(revisionService.current()).thenReturn(7L, 7L);

        PortalMachineApplicationPageResponse response = service.read(0, 20, 7L, allowAll()).getPage();

        assertEquals(Arrays.asList("z-app", "a-app"), Arrays.asList(
                response.getItems().get(0).getApplicationCode(),
                response.getItems().get(1).getApplicationCode()));
        assertEquals(IamPortalMachineDirectoryConstant.FORWARD_AUTHORIZATION_REQUEST,
                response.getItems().get(0).getForwardAuthorization());
        assertEquals(Collections.emptyList(), response.getItems().get(0).getForwardCookieNames());
    }

    @Test
    void rejectsStaleCatalogRevisionBeforeReadingDirectory() {
        IamTrustedApplicationPortalRepository portalRepository = mock(IamTrustedApplicationPortalRepository.class);
        IamTrustedApplicationRepository applicationRepository = mock(IamTrustedApplicationRepository.class);
        IamPortalCatalogRevisionService revisionService = mock(IamPortalCatalogRevisionService.class);
        TrustedApplicationBuiltInResolver builtInResolver = mock(TrustedApplicationBuiltInResolver.class);
        IamPortalMachineApplicationDirectoryService service = new IamPortalMachineApplicationDirectoryService(
                portalRepository, applicationRepository, revisionService, builtInResolver);
        when(revisionService.current()).thenReturn(8L);

        IamPortalMachineApplicationDirectoryService.ReadResult result = service.read(0, 20, 7L, allowAll());
        assertFalse(result.isSuccess());
        assertEquals("before-read", result.getStage());
        assertEquals(Long.valueOf(7L), result.getExpectedRevision());
        assertEquals(8L, result.getBeforeRevision());
        assertEquals(8L, result.getAfterRevision());
        verifyNoInteractions(portalRepository, applicationRepository);
    }

    @Test
    void rejectsSnapshotWhenRevisionChangesDuringRead() {
        IamTrustedApplicationPortalRepository portalRepository = mock(IamTrustedApplicationPortalRepository.class);
        IamTrustedApplicationRepository applicationRepository = mock(IamTrustedApplicationRepository.class);
        IamPortalCatalogRevisionService revisionService = mock(IamPortalCatalogRevisionService.class);
        TrustedApplicationBuiltInResolver builtInResolver = mock(TrustedApplicationBuiltInResolver.class);
        IamPortalMachineApplicationDirectoryService service = new IamPortalMachineApplicationDirectoryService(
                portalRepository, applicationRepository, revisionService, builtInResolver);
        when(portalRepository.findByEnabledOrderBySortOrderAscApplicationIdAsc(SimpleIamServerConstant.STATUS_ACTIVE))
                .thenReturn(Collections.emptyList());
        when(applicationRepository.findAllById(anyList())).thenReturn(Collections.emptyList());
        when(revisionService.current()).thenReturn(9L, 10L);

        IamPortalMachineApplicationDirectoryService.ReadResult result = service.read(0, 20, 9L, allowAll());
        assertFalse(result.isSuccess());
        assertEquals("after-read", result.getStage());
        assertEquals(9L, result.getBeforeRevision());
        assertEquals(10L, result.getAfterRevision());
    }

    @Test
    void failsTheWholeSnapshotWhenEnabledPortalReferencesMissingApplication() {
        IamTrustedApplicationPortalRepository portalRepository = mock(IamTrustedApplicationPortalRepository.class);
        IamTrustedApplicationRepository applicationRepository = mock(IamTrustedApplicationRepository.class);
        IamPortalCatalogRevisionService revisionService = mock(IamPortalCatalogRevisionService.class);
        TrustedApplicationBuiltInResolver builtInResolver = mock(TrustedApplicationBuiltInResolver.class);
        IamPortalMachineApplicationDirectoryService service = new IamPortalMachineApplicationDirectoryService(
                portalRepository, applicationRepository, revisionService, builtInResolver);
        when(portalRepository.findByEnabledOrderBySortOrderAscApplicationIdAsc(SimpleIamServerConstant.STATUS_ACTIVE))
                .thenReturn(Collections.singletonList(portal(5L, 1L, "/app-five")));
        when(applicationRepository.findAllById(anyList())).thenReturn(Collections.emptyList());
        when(revisionService.current()).thenReturn(9L);

        IamPortalMachineDirectoryUnavailableException exception = assertThrows(IamPortalMachineDirectoryUnavailableException.class,
                () -> service.read(0, 20, 9L, allowAll()));
        assertEquals("Portal 可信应用路由投影暂不可用", exception.getMessage());
    }

    @Test
    void rejectsInvalidRouteProjectionInsteadOfReturningPartialSnapshot() {
        IamTrustedApplicationPortalRepository portalRepository = mock(IamTrustedApplicationPortalRepository.class);
        IamTrustedApplicationRepository applicationRepository = mock(IamTrustedApplicationRepository.class);
        IamPortalCatalogRevisionService revisionService = mock(IamPortalCatalogRevisionService.class);
        TrustedApplicationBuiltInResolver builtInResolver = mock(TrustedApplicationBuiltInResolver.class);
        IamPortalMachineApplicationDirectoryService service = new IamPortalMachineApplicationDirectoryService(
                portalRepository, applicationRepository, revisionService, builtInResolver);
        when(portalRepository.findByEnabledOrderBySortOrderAscApplicationIdAsc(SimpleIamServerConstant.STATUS_ACTIVE))
                .thenReturn(Collections.singletonList(portal(5L, 1L, "//untrusted")));
        when(applicationRepository.findAllById(anyList())).thenReturn(Collections.singletonList(application(5L, "app-five")));
        when(revisionService.current()).thenReturn(9L);

        assertThrows(IamPortalMachineDirectoryUnavailableException.class,
                () -> service.read(0, 20, 9L, allowAll()));
    }

    @Test
    void usesLongOffsetForVeryLargePageNumber() {
        IamTrustedApplicationPortalRepository portalRepository = mock(IamTrustedApplicationPortalRepository.class);
        IamTrustedApplicationRepository applicationRepository = mock(IamTrustedApplicationRepository.class);
        IamPortalCatalogRevisionService revisionService = mock(IamPortalCatalogRevisionService.class);
        TrustedApplicationBuiltInResolver builtInResolver = mock(TrustedApplicationBuiltInResolver.class);
        IamPortalMachineApplicationDirectoryService service = new IamPortalMachineApplicationDirectoryService(
                portalRepository, applicationRepository, revisionService, builtInResolver);
        when(portalRepository.findByEnabledOrderBySortOrderAscApplicationIdAsc(SimpleIamServerConstant.STATUS_ACTIVE))
                .thenReturn(Collections.emptyList());
        when(applicationRepository.findAllById(anyList())).thenReturn(Collections.emptyList());
        when(revisionService.current()).thenReturn(9L, 9L);

        PortalMachineApplicationPageResponse response = service.read(Integer.MAX_VALUE, 100, 9L, allowAll()).getPage();

        assertEquals(Integer.MAX_VALUE, response.getPage());
        assertEquals(Collections.emptyList(), response.getItems());
    }

    @Test
    void excludesConfiguredBuiltInApplicationBeforeRouteValidation() {
        IamTrustedApplicationPortalRepository portalRepository = mock(IamTrustedApplicationPortalRepository.class);
        IamTrustedApplicationRepository applicationRepository = mock(IamTrustedApplicationRepository.class);
        IamPortalCatalogRevisionService revisionService = mock(IamPortalCatalogRevisionService.class);
        TrustedApplicationBuiltInResolver builtInResolver = mock(TrustedApplicationBuiltInResolver.class);
        IamPortalMachineApplicationDirectoryService service = new IamPortalMachineApplicationDirectoryService(
                portalRepository, applicationRepository, revisionService, builtInResolver);
        IamTrustedApplicationEntity application = application(8L, "aksk");
        when(portalRepository.findByEnabledOrderBySortOrderAscApplicationIdAsc(SimpleIamServerConstant.STATUS_ACTIVE))
                .thenReturn(Collections.singletonList(portal(8L, 1L, "//fixed-entry")));
        when(applicationRepository.findAllById(anyList())).thenReturn(Collections.singletonList(application));
        when(builtInResolver.isBuiltIn(application)).thenReturn(true);
        when(revisionService.current()).thenReturn(9L, 9L);

        PortalMachineApplicationPageResponse response = service.read(0, 20, 9L, allowAll()).getPage();

        assertEquals(Collections.emptyList(), response.getItems());
    }

    private DataAccessPlan allowAll() {
        DataGrant grant = new DataGrant(IamPortalMachineDirectoryConstant.DATA_RESOURCE,
                Collections.singletonList(SimpleIamServerConstant.DATA_RESOURCE_ACTION_READ), true,
                Collections.emptyList());
        return DataAccessPlan.evaluate(new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL,
                        SimpleDataPermissionConstant.VERSION, Collections.singletonList(grant)),
                new DataPermissionRequest(IamPortalMachineDirectoryConstant.DATA_RESOURCE,
                        SimpleIamServerConstant.DATA_RESOURCE_ACTION_READ));
    }

    private IamTrustedApplicationPortalEntity portal(Long applicationId, Long sortOrder, String routePrefix) {
        IamTrustedApplicationPortalEntity portal = new IamTrustedApplicationPortalEntity();
        portal.setApplicationId(applicationId);
        portal.setEnabled(SimpleIamServerConstant.STATUS_ACTIVE);
        portal.setSortOrder(sortOrder);
        portal.setRoutePrefix(routePrefix);
        portal.setEntry(routePrefix + "/index.html");
        portal.setApiBase("/api" + routePrefix);
        portal.setTargetOrigin("http://example.com");
        portal.setConfigVersion(1L);
        return portal;
    }

    private IamTrustedApplicationEntity application(Long id, String code) {
        IamTrustedApplicationEntity application = new IamTrustedApplicationEntity();
        application.setId(id);
        application.setApplicationCode(code);
        application.setApplicationName(code);
        application.setStatus(SimpleIamServerConstant.STATUS_ACTIVE);
        return application;
    }
}
