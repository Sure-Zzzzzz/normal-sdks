package io.github.surezzzzzz.sdk.auth.iam.server.service.bootstrap;

import io.github.surezzzzzz.sdk.auth.iam.core.constant.SimpleIamCoreConstant;
import io.github.surezzzzzz.sdk.auth.iam.core.spi.SubjectIdGenerator;
import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.user.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.user.IamUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

/**
 * 存量主体 ID 回填：启动幂等，subject_id IS NULL 的行逐行乐观式分配
 * （UPDATE ... WHERE subject_id IS NULL，受影响行数 0 即已被并发实例处理跳过——双实例并发启动安全）；
 * 覆盖全部行含 INACTIVE 归档行（占位永不复用的前提是归档行也有号）；
 * 二次启动零变更。回填事实走 DEBUG/INFO 日志，不设独立审计事件（subject_id 属用户生命周期）。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class IamSubjectIdBackfillService implements ApplicationRunner {

    private final IamUserRepository userRepository;
    private final SubjectIdGenerator subjectIdGenerator;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<IamUserEntity> pending = userRepository.findBySubjectIdIsNull();
        if (pending.isEmpty()) {
            log.debug("主体ID存量回填：无待处理行");
            return;
        }
        long startAt = System.currentTimeMillis();
        int updated = 0;
        for (IamUserEntity user : pending) {
            updated += backfillOne(user.getId());
        }
        log.info("主体ID存量回填完成：pending={}, updated={}, costMs={}",
                pending.size(), updated, System.currentTimeMillis() - startAt);
    }

    /**
     * 单行回填：生成器契约校验 + 唯一冲突重试；乐观式写入，行已被并发实例处理时返回 0。
     *
     * @param userId 用户 ID
     * @return 受影响行数（0=已被并发实例处理）
     */
    private int backfillOne(Long userId) {
        for (int attempt = 0; attempt <= SimpleIamServerConstant.SUBJECT_ID_RETRY_LIMIT; attempt++) {
            String candidate = subjectIdGenerator.generate();
            if (!StringUtils.hasText(candidate) || candidate.length() > SimpleIamCoreConstant.SUBJECT_ID_MAX_LENGTH) {
                throw new SimpleIamServerException(ErrorCode.SUBJECT_ID_GENERATE_INVALID, String.format(
                        ServerErrorMessage.SUBJECT_ID_GENERATE_INVALID, SimpleIamCoreConstant.SUBJECT_ID_MAX_LENGTH));
            }
            if (userRepository.existsBySubjectId(candidate)) {
                log.debug("回填主体ID唯一冲突，重试：userId={}, attempt={}", userId, attempt);
                continue;
            }
            int updated = userRepository.updateSubjectIdIfAbsent(userId, candidate, Instant.now());
            if (updated == 0) {
                log.debug("回填行已被并发实例处理，跳过：userId={}", userId);
            }
            return updated;
        }
        throw new SimpleIamServerException(ErrorCode.SUBJECT_ID_ASSIGN_EXHAUSTED, String.format(
                ServerErrorMessage.SUBJECT_ID_ASSIGN_EXHAUSTED, SimpleIamServerConstant.SUBJECT_ID_RETRY_LIMIT));
    }
}
