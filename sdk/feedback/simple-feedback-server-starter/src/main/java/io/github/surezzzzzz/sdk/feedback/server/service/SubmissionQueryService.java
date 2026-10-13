package io.github.surezzzzzz.sdk.feedback.server.service;

import io.github.surezzzzzz.sdk.feedback.server.annotation.SimpleFeedbackServerComponent;
import io.github.surezzzzzz.sdk.feedback.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.feedback.server.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.feedback.server.exception.SimpleFeedbackServerException;
import io.github.surezzzzzz.sdk.feedback.server.model.FeedbackApplicationAccessScope;
import io.github.surezzzzzz.sdk.feedback.server.model.FeedbackAttachment;
import io.github.surezzzzzz.sdk.feedback.server.model.FeedbackSubmission;
import io.github.surezzzzzz.sdk.feedback.server.repository.FeedbackAttachmentRepository;
import io.github.surezzzzzz.sdk.feedback.server.repository.FeedbackSubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;

import java.util.Collections;
import java.util.List;

/**
 * 反馈查询服务（管理列表+DATA 遮蔽+mine owner 等值；不可见一律安全 404 不泄露存在性）。
 *
 * <p>非直观约束（DESIGN §5.1）：无 DATA 授权（DENY）=403；有授权但范围为空=空列表 200；
 * 受限范围与筛选条件叠加后交集为空=空列表——三分支语义不混。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleFeedbackServerComponent
@RequiredArgsConstructor
public class SubmissionQueryService {

    private final FeedbackSubmissionRepository submissionRepository;
    private final FeedbackAttachmentRepository attachmentRepository;

    private static void requireNotDenied(FeedbackApplicationAccessScope scope) {
        if (scope.isDenied()) {
            throw new AccessDeniedException(ErrorMessage.SUBMISSION_NOT_FOUND);
        }
    }

    private static SimpleFeedbackServerException notFound() {
        return new SimpleFeedbackServerException(ErrorCode.SUBMISSION_NOT_FOUND, ErrorMessage.SUBMISSION_NOT_FOUND);
    }

    /**
     * 管理列表（DATA 范围进 SQL）。
     *
     * @param filter 筛选条件
     * @param scope  应用访问范围
     * @param page   页码（1 起）
     * @param size   页大小
     * @return 分页结果
     */
    public PageResult<FeedbackSubmission> adminList(FeedbackSubmissionRepository.Filter filter,
                                                    FeedbackApplicationAccessScope scope, int page, int size) {
        requireNotDenied(scope);
        java.util.Set<String> codes = scope.restrictedCodesOrNull();
        if (codes != null && codes.isEmpty()) {
            log.debug("管理列表 DATA 受限空集，返回空列表");
            return new PageResult<FeedbackSubmission>(Collections.<FeedbackSubmission>emptyList(), 0L, page, size);
        }
        long total = submissionRepository.countByFilter(filter, codes);
        List<FeedbackSubmission> items = total == 0L
                ? Collections.<FeedbackSubmission>emptyList()
                : submissionRepository.listByFilter(filter, codes, (page - 1) * size, size);
        return new PageResult<FeedbackSubmission>(items, total, page, size);
    }

    /**
     * 管理详情（范围不可见=安全 404）。
     *
     * @param id    反馈 ID
     * @param scope 应用访问范围
     * @return 反馈
     */
    public FeedbackSubmission adminGet(String id, FeedbackApplicationAccessScope scope) {
        requireNotDenied(scope);
        FeedbackSubmission submission = submissionRepository.findById(id);
        if (submission == null || !scope.contains(submission.getApplicationCode())) {
            throw notFound();
        }
        return submission;
    }

    /**
     * 本人列表（owner 等值）。
     *
     * @param owner 归属
     * @param page  页码（1 起）
     * @param size  页大小
     * @return 分页结果
     */
    public PageResult<FeedbackSubmission> mineList(String owner, int page, int size) {
        long total = submissionRepository.countByOwner(owner);
        List<FeedbackSubmission> items = total == 0L
                ? Collections.<FeedbackSubmission>emptyList()
                : submissionRepository.listByOwner(owner, (page - 1) * size, size);
        return new PageResult<FeedbackSubmission>(items, total, page, size);
    }

    /**
     * 本人详情（非本人=安全 404）。
     *
     * @param id    反馈 ID
     * @param owner 归属
     * @return 反馈
     */
    public FeedbackSubmission mineGet(String id, String owner) {
        FeedbackSubmission submission = submissionRepository.findById(id);
        if (submission == null || !submission.getOwner().equals(owner)) {
            throw notFound();
        }
        return submission;
    }

    /**
     * 附件列表（调用方先行完成主体鉴权）。
     *
     * @param submissionId 反馈 ID
     * @return 附件列表
     */
    public List<FeedbackAttachment> attachments(String submissionId) {
        return attachmentRepository.findBySubmissionId(submissionId);
    }

    /**
     * 附件单查（下载鉴权：先主体鉴权再比对附件归属）。
     *
     * @param submissionId 反馈 ID（来自路径）
     * @param attachmentId 附件 ID
     * @return 附件
     */
    public FeedbackAttachment attachment(String submissionId, String attachmentId) {
        FeedbackAttachment attachment = attachmentRepository.findById(attachmentId);
        if (attachment == null || !attachment.getSubmissionId().equals(submissionId)) {
            throw notFound();
        }
        return attachment;
    }

    /**
     * 分页结果值对象。
     */
    @lombok.Value
    public static class PageResult<T> {

        /**
         * 当前页条目
         */
        private final List<T> items;

        /**
         * 总数（同条件）
         */
        private final long total;

        /**
         * 页码（1 起）
         */
        private final int page;

        /**
         * 页大小
         */
        private final int size;
    }
}
