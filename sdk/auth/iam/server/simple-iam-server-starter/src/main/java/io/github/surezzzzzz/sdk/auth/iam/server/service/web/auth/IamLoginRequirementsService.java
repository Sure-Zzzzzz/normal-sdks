package io.github.surezzzzzz.sdk.auth.iam.server.service.web.auth;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.configuration.SimpleIamServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.web.auth.response.WebLoginRequirementDocumentResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.web.auth.response.WebLoginRequirementsResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.user.IamLoginRequirementDocumentEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.user.IamUserDocumentConsentEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.user.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.user.IamLoginRequirementDocumentRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.user.IamUserDocumentConsentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.*;

/**
 * 登录条款与隐私政策要求编排服务。
 *
 * <p>优先读取数据库中的不可变文档版本；没有数据库文档时兼容读取部署配置。用户同意记录只引用
 * 文档版本和必要审计元数据，不复制正文或认证材料。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
public class IamLoginRequirementsService {

    private static final int MAX_REVISION_LENGTH = 128;
    private static final int MAX_PROVIDER_LENGTH = 64;
    private static final String TERMS_OF_SERVICE_CODE = "terms-of-service";
    private static final String PRIVACY_POLICY_CODE = "privacy-policy";

    private final SimpleIamServerProperties properties;
    private final IamUserDocumentConsentRepository consentRepository;
    private final IamLoginRequirementDocumentRepository documentRepository;

    /**
     * 保留给纯单元测试和旧宿主的配置回退构造器。
     */
    public IamLoginRequirementsService(SimpleIamServerProperties properties,
                                       IamUserDocumentConsentRepository consentRepository) {
        this(properties, consentRepository, null);
    }

    @Autowired
    public IamLoginRequirementsService(SimpleIamServerProperties properties,
                                       IamUserDocumentConsentRepository consentRepository,
                                       IamLoginRequirementDocumentRepository documentRepository) {
        this.properties = properties;
        this.consentRepository = consentRepository;
        this.documentRepository = documentRepository;
    }

    /**
     * 返回登录页可展示的当前要求集合。
     */
    public WebLoginRequirementsResponse current() {
        if (!isEnabled()) {
            return new WebLoginRequirementsResponse("DISABLED", false, null, Collections.emptyList());
        }
        List<DocumentDefinition> documents = documentDefinitions();
        requireDocuments(documents);
        List<WebLoginRequirementDocumentResponse> responses = new ArrayList<>();
        for (DocumentDefinition document : documents) {
            responses.add(new WebLoginRequirementDocumentResponse(document.versionId, document.code,
                    document.title, document.url, document.content, document.required));
        }
        return new WebLoginRequirementsResponse("REQUIRED", true, revision(documents), responses);
    }

    /**
     * 校验本次登录提交的要求版本及文档，并幂等记录用户同意。
     */
    @Transactional
    public void assertAccepted(String submittedRevision, List<Long> acceptedDocumentVersionIds,
                               IamUserEntity user, String provider) {
        if (!isEnabled()) {
            return;
        }
        List<DocumentDefinition> documents = documentDefinitions();
        requireDocuments(documents);
        String currentRevision = revision(documents);
        if (!StringUtils.hasText(submittedRevision) || !submittedRevision.equals(currentRevision)) {
            throw notAccepted("登录要求已更新，请重新确认服务条款和隐私政策");
        }
        Set<Long> configuredIds = new HashSet<>();
        Set<Long> requiredIds = new HashSet<>();
        for (DocumentDefinition document : documents) {
            configuredIds.add(document.versionId);
            if (document.required) {
                requiredIds.add(document.versionId);
            }
        }
        Set<Long> acceptedIds = acceptedDocumentVersionIds == null
                ? Collections.<Long>emptySet() : new HashSet<>(acceptedDocumentVersionIds);
        if (!configuredIds.containsAll(acceptedIds) || !acceptedIds.containsAll(requiredIds)) {
            throw notAccepted("请先阅读并同意服务条款和隐私政策");
        }
        Instant acceptedAt = Instant.now();
        String safeProvider = StringUtils.hasText(provider) ? provider.trim() : "unknown";
        if (safeProvider.length() > MAX_PROVIDER_LENGTH) {
            throw notAccepted("登录方式编码超过 " + MAX_PROVIDER_LENGTH + " 个字符");
        }
        for (Long documentVersionId : acceptedIds) {
            IamUserDocumentConsentEntity consent = consentRepository
                    .findByUserIdAndDocumentVersionId(user.getId(), documentVersionId)
                    .orElseGet(IamUserDocumentConsentEntity::new);
            consent.setUserId(user.getId());
            consent.setDocumentVersionId(documentVersionId);
            consent.setRequirementsRevision(currentRevision);
            consent.setAcceptedAt(acceptedAt);
            consent.setProvider(safeProvider);
            consentRepository.save(consent);
        }
        log.info("登录要求已确认：subjectId={}, provider={}, revision={}, documentCount={}",
                user.getSubjectId(), safeProvider, currentRevision, acceptedIds.size());
    }

    /**
     * 启动期校验数据库正文，数据库为空时校验兼容配置。
     */
    public void validateConfiguration() {
        if (!isEnabled()) {
            return;
        }
        List<DocumentDefinition> documents = documentDefinitions();
        if (documents.isEmpty()) {
            throw new ConfigurationException("登录要求已启用但未配置文档");
        }
        String currentRevision = revision(documents);
        if (!StringUtils.hasText(currentRevision) || currentRevision.length() > MAX_REVISION_LENGTH) {
            throw new ConfigurationException("login.requirements.revision不能为空");
        }
        Set<Long> ids = new HashSet<>();
        for (DocumentDefinition document : documents) {
            if (document.versionId == null || document.versionId <= 0 || !ids.add(document.versionId)) {
                throw new ConfigurationException("登录要求文档version-id必须为正数且唯一");
            }
            if (!StringUtils.hasText(document.code) || !StringUtils.hasText(document.title)) {
                throw new ConfigurationException("登录要求文档必须配置code和title");
            }
            if (document.fromConfiguration && !StringUtils.hasText(document.url)) {
                throw new ConfigurationException("配置型登录要求文档必须配置url");
            }
            if (!document.fromConfiguration && !StringUtils.hasText(document.content)) {
                throw new ConfigurationException("数据库登录要求文档必须配置content");
            }
        }
        validateDocumentSet(documents);
    }

    private List<DocumentDefinition> documentDefinitions() {
        if (documentRepository != null) {
            List<IamLoginRequirementDocumentEntity> stored = documentRepository.findAllByOrderByVersionIdAsc();
            if (stored != null && !stored.isEmpty()) {
                List<DocumentDefinition> documents = new ArrayList<>();
                for (IamLoginRequirementDocumentEntity document : stored) {
                    documents.add(DocumentDefinition.fromEntity(document));
                }
                return documents;
            }
        }
        SimpleIamServerProperties.LoginRequirementsConfig config = requirementsConfig();
        List<SimpleIamServerProperties.LoginRequirementDocumentConfig> configured = config.getDocuments();
        if (configured == null || configured.isEmpty()) {
            return Collections.emptyList();
        }
        List<DocumentDefinition> documents = new ArrayList<>();
        for (SimpleIamServerProperties.LoginRequirementDocumentConfig document : configured) {
            documents.add(DocumentDefinition.fromConfiguration(config.getRevision(), document));
        }
        return documents;
    }

    private String revision(List<DocumentDefinition> documents) {
        return documents.isEmpty() ? requirementsConfig().getRevision() : documents.get(0).revision;
    }

    private boolean isEnabled() {
        return !Boolean.FALSE.equals(requirementsConfig().getEnabled());
    }

    private void requireDocuments(List<DocumentDefinition> documents) {
        validateDocumentSet(documents);
    }

    /**
     * 登录页的两个法务入口必须成对存在且都必须同意，避免页面和登录接口出现不同门禁。
     */
    private void validateDocumentSet(List<DocumentDefinition> documents) {
        if (documents == null || documents.isEmpty()) {
            throw new ConfigurationException("登录要求已启用但未配置文档");
        }
        Set<String> requiredCodes = new HashSet<>();
        for (DocumentDefinition document : documents) {
            if (document.versionId == null || document.versionId <= 0
                    || !StringUtils.hasText(document.code) || !StringUtils.hasText(document.title)
                    || !document.required) {
                throw new ConfigurationException("服务条款和隐私政策必须配置为必选文档");
            }
            String normalizedCode = document.code.trim().toLowerCase();
            if (TERMS_OF_SERVICE_CODE.equals(normalizedCode)
                    || PRIVACY_POLICY_CODE.equals(normalizedCode)) {
                requiredCodes.add(normalizedCode);
            }
        }
        if (!requiredCodes.contains(TERMS_OF_SERVICE_CODE)
                || !requiredCodes.contains(PRIVACY_POLICY_CODE)) {
            throw new ConfigurationException("登录要求必须同时配置服务条款和隐私政策");
        }
    }

    private SimpleIamServerException notAccepted(String message) {
        return new SimpleIamServerException(ErrorCode.VALIDATION_FAILED, message);
    }

    private SimpleIamServerProperties.LoginRequirementsConfig requirementsConfig() {
        SimpleIamServerProperties.LoginConfig login = properties.getLogin();
        return login.getRequirements() == null
                ? new SimpleIamServerProperties.LoginRequirementsConfig() : login.getRequirements();
    }

    private static final class DocumentDefinition {

        private final Long versionId;
        private final String revision;
        private final String code;
        private final String title;
        private final String content;
        private final String url;
        private final boolean required;
        private final boolean fromConfiguration;

        private DocumentDefinition(Long versionId, String revision, String code, String title,
                                   String content, String url, boolean required, boolean fromConfiguration) {
            this.versionId = versionId;
            this.revision = revision;
            this.code = code;
            this.title = title;
            this.content = content;
            this.url = url;
            this.required = required;
            this.fromConfiguration = fromConfiguration;
        }

        private static DocumentDefinition fromEntity(IamLoginRequirementDocumentEntity document) {
            return new DocumentDefinition(document.getVersionId(), document.getRequirementsRevision(),
                    document.getCode(), document.getTitle(), document.getContent(), document.getUrl(),
                    Boolean.TRUE.equals(document.getRequired()), false);
        }

        private static DocumentDefinition fromConfiguration(String revision,
                                                            SimpleIamServerProperties.LoginRequirementDocumentConfig document) {
            return new DocumentDefinition(document.getVersionId(), revision, document.getCode(), document.getTitle(),
                    document.getContent(), document.getUrl(), !Boolean.FALSE.equals(document.getRequired()), true);
        }
    }
}
