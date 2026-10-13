package io.github.surezzzzzz.sdk.feedback.server.service;

import io.github.surezzzzzz.sdk.feedback.server.annotation.SimpleFeedbackServerComponent;
import io.github.surezzzzzz.sdk.feedback.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.feedback.server.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.feedback.server.constant.FeedbackStatus;
import io.github.surezzzzzz.sdk.feedback.server.constant.SimpleFeedbackServerConstant;
import io.github.surezzzzzz.sdk.feedback.server.exception.FeedbackStateException;
import io.github.surezzzzzz.sdk.feedback.server.exception.SimpleFeedbackServerException;
import io.github.surezzzzzz.sdk.feedback.server.model.FeedbackApplicationAccessScope;
import io.github.surezzzzzz.sdk.feedback.server.model.FeedbackSubmission;
import io.github.surezzzzzz.sdk.feedback.server.repository.FeedbackSubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 反馈处理服务（状态迁移+覆盖式留痕+行版本 CAS+DATA 范围进 UPDATE）。
 *
 * <p>非直观约束（DESIGN §6）：expectedRowVersion 不匹配=409；行不可见=安全 404——
 * 两者以 CAS 影响行数 0 后重查区分，不泄露存在性。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleFeedbackServerComponent
@RequiredArgsConstructor
public class SubmissionHandlingService {

    private final FeedbackSubmissionRepository submissionRepository;

    private static void validateNoteAndReply(String note, String replyText) {
        int noteLimit = SimpleFeedbackServerConstant.HANDLER_NOTE_MAX_LENGTH;
        int replyLimit = SimpleFeedbackServerConstant.REPLY_MAX_LENGTH;
        if (note != null && note.length() > noteLimit) {
            throw new FeedbackStateException(ErrorCode.FIELD_TOO_LONG,
                    String.format(ErrorMessage.FIELD_TOO_LONG, "handlerNote", noteLimit));
        }
        if (replyText != null && replyText.length() > replyLimit) {
            throw new FeedbackStateException(ErrorCode.FIELD_TOO_LONG,
                    String.format(ErrorMessage.FIELD_TOO_LONG, "reply", replyLimit));
        }
    }

    /**
     * 处理反馈（RESOLVED/DISMISSED/重开 SUBMITTED）。
     *
     * @param id                 反馈 ID
     * @param target             目标状态
     * @param handlerSubjectId   处理人 subjectId
     * @param note               内部备注（可空，整体覆盖）
     * @param replyText          给提交人的答复（可空；非空时记录答复人/时间）
     * @param expectedRowVersion 期望行版本
     * @param scope              应用访问范围（DATA 进 UPDATE WHERE）
     * @return 处理后的反馈（含新行版本）
     */
    @Transactional
    public FeedbackSubmission handle(String id, FeedbackStatus target, String handlerSubjectId, String note,
                                     String replyText, int expectedRowVersion,
                                     FeedbackApplicationAccessScope scope) {
        FeedbackSubmission submission = submissionRepository.findById(id);
        if (submission == null || !scope.contains(submission.getApplicationCode())) {
            throw new SimpleFeedbackServerException(ErrorCode.SUBMISSION_NOT_FOUND, ErrorMessage.SUBMISSION_NOT_FOUND);
        }
        validateNoteAndReply(note, replyText);
        submission.applyHandling(target, handlerSubjectId, note, replyText, Instant.now());
        submission.setRowVersion(expectedRowVersion + 1);
        int updated = submissionRepository.updateHandling(submission, expectedRowVersion,
                scope.restrictedCodesOrNull());
        if (updated == 0) {
            FeedbackSubmission current = submissionRepository.findById(id);
            if (current == null || !scope.contains(current.getApplicationCode())) {
                throw new SimpleFeedbackServerException(ErrorCode.SUBMISSION_NOT_FOUND,
                        ErrorMessage.SUBMISSION_NOT_FOUND);
            }
            log.debug("处理版本冲突 id={} 期望版本={} 实际版本={}", id, expectedRowVersion, current.getRowVersion());
            throw new FeedbackStateException(ErrorCode.ROW_VERSION_CONFLICT, ErrorMessage.ROW_VERSION_CONFLICT);
        }
        log.debug("处理终态 id={} 目标状态={} 新版本={}", id, target, submission.getRowVersion());
        return submission;
    }
}
