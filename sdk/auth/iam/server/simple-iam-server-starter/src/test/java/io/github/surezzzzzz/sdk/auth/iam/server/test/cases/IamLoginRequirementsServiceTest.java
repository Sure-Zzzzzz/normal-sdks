package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.configuration.SimpleIamServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.user.IamLoginRequirementDocumentEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.user.IamUserDocumentConsentEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.user.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.user.IamLoginRequirementDocumentRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.user.IamUserDocumentConsentRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.web.auth.IamLoginRequirementsService;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 登录要求的版本校验、失败边界和幂等记录测试。
 *
 * @author surezzzzzz
 */
class IamLoginRequirementsServiceTest {

    @Test
    void shouldKeepExistingLoginBehaviorWhenNoDocumentsConfigured() {
        IamUserDocumentConsentRepository repository = mock(IamUserDocumentConsentRepository.class);
        SimpleIamServerProperties properties = properties();
        properties.getLogin().getRequirements().setEnabled(false);
        IamLoginRequirementsService service = service(properties, repository);

        assertFalse(service.current().isRequired());
        assertEquals("DISABLED", service.current().getMode());
        service.assertAccepted(null, null, user(), "local-password");

        verifyNoInteractions(repository);
    }

    @Test
    void shouldRejectEnabledRequirementsWithoutDocuments() {
        IamLoginRequirementsService service = service(properties(), mock(IamUserDocumentConsentRepository.class));

        assertThrows(ConfigurationException.class, service::validateConfiguration);
        assertThrows(ConfigurationException.class, service::current);
    }

    @Test
    void shouldExposeRequiredModeWhenDocumentsAreConfigured() {
        IamLoginRequirementsService service = service(propertiesWithDocuments(),
                mock(IamUserDocumentConsentRepository.class));

        assertTrue(service.current().isRequired());
        assertEquals("REQUIRED", service.current().getMode());
    }

    @Test
    void shouldReadDocumentContentFromDatabaseVersion() {
        IamUserDocumentConsentRepository consentRepository = mock(IamUserDocumentConsentRepository.class);
        IamLoginRequirementDocumentRepository documentRepository = mock(IamLoginRequirementDocumentRepository.class);
        IamLoginRequirementDocumentEntity document = new IamLoginRequirementDocumentEntity();
        document.setVersionId(2026100101L);
        document.setRequirementsRevision("placeholder-2026-10");
        document.setCode("terms-of-service");
        document.setTitle("服务条款（联调占位）");
        document.setContent("本页面为 IAM 联调占位文本。");
        document.setUrl("/legal/terms");
        document.setRequired(true);
        IamLoginRequirementDocumentEntity privacy = new IamLoginRequirementDocumentEntity();
        privacy.setVersionId(2026100102L);
        privacy.setRequirementsRevision("placeholder-2026-10");
        privacy.setCode("privacy-policy");
        privacy.setTitle("隐私政策（联调占位）");
        privacy.setContent("本页面为 IAM 联调占位文本。");
        privacy.setUrl("/legal/privacy");
        privacy.setRequired(true);
        when(documentRepository.findAllByOrderByVersionIdAsc()).thenReturn(Arrays.asList(document, privacy));

        IamLoginRequirementsService service = new IamLoginRequirementsService(
                propertiesWithDocuments(), consentRepository, documentRepository);

        assertEquals("placeholder-2026-10", service.current().getRevision());
        assertEquals("本页面为 IAM 联调占位文本。", service.current().getDocuments().get(0).getContent());
        verifyNoInteractions(consentRepository);
    }

    @Test
    void shouldRejectOptionalOrIncompleteLegalDocumentSet() {
        SimpleIamServerProperties properties = propertiesWithDocuments();
        properties.getLogin().getRequirements().getDocuments().get(1).setRequired(false);

        IamLoginRequirementsService service = service(properties, mock(IamUserDocumentConsentRepository.class));

        assertThrows(ConfigurationException.class, service::validateConfiguration);
        assertThrows(ConfigurationException.class, service::current);

        SimpleIamServerProperties incomplete = properties();
        SimpleIamServerProperties.LoginRequirementsConfig requirements =
                new SimpleIamServerProperties.LoginRequirementsConfig();
        requirements.setRevision("terms-v1");
        requirements.setDocuments(Collections.singletonList(document(1L, "terms-of-service", true)));
        incomplete.getLogin().setRequirements(requirements);
        IamLoginRequirementsService incompleteService = service(incomplete,
                mock(IamUserDocumentConsentRepository.class));

        assertThrows(ConfigurationException.class, incompleteService::validateConfiguration);
    }

    @Test
    void shouldRejectStaleRevision() {
        IamUserDocumentConsentRepository repository = mock(IamUserDocumentConsentRepository.class);
        IamLoginRequirementsService service = service(propertiesWithDocuments(), repository);

        SimpleIamServerException exception = assertThrows(SimpleIamServerException.class,
                () -> service.assertAccepted("old-revision", Collections.singletonList(1L), user(),
                        "local-password"));

        assertEquals(ErrorCode.VALIDATION_FAILED, exception.getErrorCode());
        verifyNoInteractions(repository);
    }

    @Test
    void shouldRejectMissingRequiredOrUnknownDocuments() {
        IamUserDocumentConsentRepository repository = mock(IamUserDocumentConsentRepository.class);
        IamLoginRequirementsService service = service(propertiesWithDocuments(), repository);

        assertThrows(SimpleIamServerException.class,
                () -> service.assertAccepted("terms-v1", Collections.<Long>emptyList(), user(), "phone-sms"));
        assertThrows(SimpleIamServerException.class,
                () -> service.assertAccepted("terms-v1", Arrays.asList(1L, 99L), user(), "phone-sms"));

        verifyNoInteractions(repository);
    }

    @Test
    void shouldPersistAcceptedVersionsIdempotently() {
        IamUserDocumentConsentRepository repository = mock(IamUserDocumentConsentRepository.class);
        IamUserEntity user = user();
        IamUserDocumentConsentEntity existing = new IamUserDocumentConsentEntity();
        when(repository.findByUserIdAndDocumentVersionId(7L, 1L)).thenReturn(Optional.of(existing));
        when(repository.findByUserIdAndDocumentVersionId(7L, 2L)).thenReturn(Optional.empty());
        when(repository.save(any(IamUserDocumentConsentEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        IamLoginRequirementsService service = service(propertiesWithDocuments(), repository);

        service.assertAccepted("terms-v1", Arrays.asList(1L, 2L), user, "local-password");

        assertEquals(7L, existing.getUserId());
        assertEquals(1L, existing.getDocumentVersionId());
        assertEquals("terms-v1", existing.getRequirementsRevision());
        verify(repository).save(existing);
        verify(repository, times(2)).save(any(IamUserDocumentConsentEntity.class));
    }

    @Test
    void shouldRejectInvalidConfiguredDocument() {
        SimpleIamServerProperties properties = properties();
        SimpleIamServerProperties.LoginRequirementsConfig requirements =
                new SimpleIamServerProperties.LoginRequirementsConfig();
        SimpleIamServerProperties.LoginRequirementDocumentConfig document =
                new SimpleIamServerProperties.LoginRequirementDocumentConfig();
        document.setVersionId(1L);
        document.setCode("terms-of-service");
        document.setTitle("服务条款");
        requirements.setDocuments(Collections.singletonList(document));
        properties.getLogin().setRequirements(requirements);

        IamLoginRequirementsService service = service(properties, mock(IamUserDocumentConsentRepository.class));

        assertThrows(ConfigurationException.class, service::validateConfiguration);
    }

    @Test
    void shouldRejectRevisionLongerThanConsentColumn() {
        SimpleIamServerProperties properties = propertiesWithDocuments();
        properties.getLogin().getRequirements().setRevision(String.join("", Collections.nCopies(129, "r")));

        IamLoginRequirementsService service = service(properties, mock(IamUserDocumentConsentRepository.class));

        assertThrows(ConfigurationException.class, service::validateConfiguration);
    }

    @Test
    void shouldRejectProviderLongerThanConsentColumn() {
        IamUserDocumentConsentRepository repository = mock(IamUserDocumentConsentRepository.class);
        IamLoginRequirementsService service = service(propertiesWithDocuments(), repository);

        assertThrows(SimpleIamServerException.class,
                () -> service.assertAccepted("terms-v1", Collections.singletonList(1L), user(),
                        String.join("", Collections.nCopies(65, "p"))));

        verifyNoInteractions(repository);
    }

    private IamLoginRequirementsService service(SimpleIamServerProperties properties,
                                                IamUserDocumentConsentRepository repository) {
        return new IamLoginRequirementsService(properties, repository);
    }

    private SimpleIamServerProperties properties() {
        return new SimpleIamServerProperties();
    }

    private SimpleIamServerProperties propertiesWithDocuments() {
        SimpleIamServerProperties properties = properties();
        SimpleIamServerProperties.LoginRequirementsConfig requirements =
                new SimpleIamServerProperties.LoginRequirementsConfig();
        requirements.setRevision("terms-v1");
        requirements.setDocuments(Arrays.asList(document(1L, "terms-of-service", true),
                document(2L, "privacy-policy", true)));
        properties.getLogin().setRequirements(requirements);
        return properties;
    }

    private SimpleIamServerProperties.LoginRequirementDocumentConfig document(Long versionId, String code,
                                                                              boolean required) {
        SimpleIamServerProperties.LoginRequirementDocumentConfig document =
                new SimpleIamServerProperties.LoginRequirementDocumentConfig();
        document.setVersionId(versionId);
        document.setCode(code);
        document.setTitle(code);
        document.setUrl("/legal/" + code);
        document.setRequired(required);
        return document;
    }

    private IamUserEntity user() {
        IamUserEntity user = new IamUserEntity();
        user.setId(7L);
        user.setSubjectId("usr_test_subject");
        user.setUsername("test-user");
        return user;
    }
}
