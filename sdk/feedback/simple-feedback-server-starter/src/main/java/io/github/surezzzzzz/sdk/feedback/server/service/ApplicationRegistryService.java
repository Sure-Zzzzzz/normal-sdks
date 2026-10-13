package io.github.surezzzzzz.sdk.feedback.server.service;

import io.github.surezzzzzz.sdk.feedback.server.annotation.SimpleFeedbackServerComponent;
import io.github.surezzzzzz.sdk.feedback.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.feedback.server.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.feedback.server.exception.FeedbackStateException;
import io.github.surezzzzzz.sdk.feedback.server.exception.SimpleFeedbackServerException;
import io.github.surezzzzzz.sdk.feedback.server.model.FeedbackApplication;
import io.github.surezzzzzz.sdk.feedback.server.repository.FeedbackApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 应用注册表服务（登记/更新/启停；applicationCode 唯一键天然幂等，创建不收幂等键）。
 *
 * <p>非直观约束：注册项可停用不可物理删除（历史引用完整性，DESIGN §7）；
 * 停用=拦新提交不影响历史（D6）。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleFeedbackServerComponent
@RequiredArgsConstructor
public class ApplicationRegistryService {

    private final FeedbackApplicationRepository applicationRepository;

    /**
     * 登记应用（重复编码=唯一键冲突映射 409）。
     *
     * @param application 注册项（编码/显示名/描述/期望处理组）
     * @param createdBy   创建人 subjectId（认证上下文）
     * @return 落库后的注册项
     */
    @Transactional
    public FeedbackApplication create(FeedbackApplication application, String createdBy) {
        application.validate();
        Instant now = Instant.now();
        application.setCreatedBy(createdBy);
        application.setCreatedTime(now);
        application.setUpdatedTime(now);
        application.setRowVersion(0);
        try {
            applicationRepository.insert(application);
        } catch (DuplicateKeyException exception) {
            throw new FeedbackStateException(ErrorCode.ROW_VERSION_CONFLICT,
                    String.format(ErrorMessage.APPLICATION_ALREADY_REGISTERED, application.getApplicationCode()));
        }
        log.debug("注册表登记终态 code={}", application.getApplicationCode());
        return application;
    }

    /**
     * 更新注册项（行版本 CAS；displayName/description/enabled/ownerGroup 可变，编码不可变）。
     *
     * @param application        目标值
     * @param expectedRowVersion 期望行版本
     * @return 更新后的注册项
     */
    @Transactional
    public FeedbackApplication update(FeedbackApplication application, int expectedRowVersion) {
        application.validate();
        application.setRowVersion(expectedRowVersion + 1);
        int updated = applicationRepository.update(application, expectedRowVersion);
        if (updated == 0) {
            FeedbackApplication current = applicationRepository.findByCode(application.getApplicationCode());
            if (current == null) {
                throw new SimpleFeedbackServerException(ErrorCode.APPLICATION_NOT_FOUND, ErrorMessage.APPLICATION_NOT_FOUND);
            }
            throw new FeedbackStateException(ErrorCode.ROW_VERSION_CONFLICT, ErrorMessage.ROW_VERSION_CONFLICT);
        }
        log.debug("注册表更新终态 code={} 新版本={}", application.getApplicationCode(),
                application.getRowVersion());
        return applicationRepository.findByCode(application.getApplicationCode());
    }

    /**
     * 管理列表（DATA 范围进 SQL：受限时 IN 集，null=全量）。
     *
     * @param applicationCodes DATA application IN 集（null=全量）
     * @return 注册项列表
     */
    public List<FeedbackApplication> listByScope(java.util.Set<String> applicationCodes) {
        return applicationRepository.listAll(applicationCodes);
    }

    /**
     * 启用中列表（提交人选择用）。
     *
     * @return 启用中的注册项列表
     */
    public List<FeedbackApplication> listEnabled() {
        return applicationRepository.listEnabled();
    }

    /**
     * 按编码查询。
     *
     * @param applicationCode 应用编码
     * @return 注册项；不存在返回 null
     */
    public FeedbackApplication findByCode(String applicationCode) {
        return applicationRepository.findByCode(applicationCode);
    }
}
