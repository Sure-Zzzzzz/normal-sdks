package io.github.surezzzzzz.sdk.feedback.server.service;

import io.github.surezzzzzz.sdk.feedback.server.annotation.SimpleFeedbackServerComponent;
import io.github.surezzzzzz.sdk.feedback.server.configuration.SimpleFeedbackServerProperties;
import io.github.surezzzzzz.sdk.feedback.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.feedback.server.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.feedback.server.exception.ApplicationNotAvailableException;
import io.github.surezzzzzz.sdk.feedback.server.exception.IdempotencyConflictException;
import io.github.surezzzzzz.sdk.feedback.server.exception.SimpleFeedbackServerException;
import io.github.surezzzzzz.sdk.feedback.server.exception.SubmissionRateLimitException;
import io.github.surezzzzzz.sdk.feedback.server.model.FeedbackAttachment;
import io.github.surezzzzzz.sdk.feedback.server.model.FeedbackSubmission;
import io.github.surezzzzzz.sdk.feedback.server.repository.FeedbackApplicationRepository;
import io.github.surezzzzzz.sdk.feedback.server.repository.FeedbackAttachmentRepository;
import io.github.surezzzzzz.sdk.feedback.server.repository.FeedbackSubmissionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 反馈提交服务（提交链：幂等判定先于频控 → 注册表校验 → 领域校验 → 附件 S3 先传 → 落库）。
 *
 * <p>非直观约束（DESIGN §5.2/修正②）：幂等判定必须在频控之前——首成功后网络丢响应的
 * 合法同键重试先返回首结果、不占频控窗口；仅新键请求进入频控检查。</p>
 *
 * <p>上传时序（DESIGN §7）：S3 先传、库后写；库写失败时已传对象不可寻址（附件行未落库），
 * 由半年生命周期桶兜底回收。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleFeedbackServerComponent
public class SubmissionCreateService {

    private final FeedbackSubmissionRepository submissionRepository;
    private final FeedbackApplicationRepository applicationRepository;
    private final FeedbackAttachmentRepository attachmentRepository;
    private final AttachmentStorageService attachmentStorageService;
    private final SimpleFeedbackServerProperties properties;
    private final FirstResponseStore snapshotStore;

    /**
     * 兼容旧构造（默认 Jackson 快照存储）。
     *
     * @param submissionRepository     提交仓储
     * @param applicationRepository    注册表仓储
     * @param attachmentRepository     附件仓储
     * @param attachmentStorageService 附件存储门面
     * @param properties               配置
     */
    public SubmissionCreateService(FeedbackSubmissionRepository submissionRepository,
                                   FeedbackApplicationRepository applicationRepository,
                                   FeedbackAttachmentRepository attachmentRepository,
                                   AttachmentStorageService attachmentStorageService,
                                   SimpleFeedbackServerProperties properties) {
        this.submissionRepository = submissionRepository;
        this.applicationRepository = applicationRepository;
        this.attachmentRepository = attachmentRepository;
        this.attachmentStorageService = attachmentStorageService;
        this.properties = properties;
        this.snapshotStore = defaultFirstResponseStore();
    }

    private static FirstResponseStore defaultFirstResponseStore() {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new FirstResponseStore() {
            @Override
            public String write(FeedbackSubmission submission) {
                try {
                    return mapper.writeValueAsString(submission);
                } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
                    throw new SimpleFeedbackServerException(ErrorCode.DEPENDENCY_UNAVAILABLE,
                            ErrorMessage.DEPENDENCY_UNAVAILABLE, exception);
                }
            }

            @Override
            public FeedbackSubmission read(String snapshot) {
                try {
                    return mapper.readValue(snapshot, FeedbackSubmission.class);
                } catch (java.io.IOException exception) {
                    throw new SimpleFeedbackServerException(ErrorCode.DEPENDENCY_UNAVAILABLE,
                            ErrorMessage.DEPENDENCY_UNAVAILABLE, exception);
                }
            }
        };
    }

    /**
     * 提交反馈。
     *
     * @param submission  已由控制器构造的提交（owner/快照来自认证上下文）
     * @param attachments 附件上传件集合（可为空）
     * @return 提交结果（含幂等重放标记）
     */
    @Transactional
    public CreateResult create(FeedbackSubmission submission, List<AttachmentInput> attachments) {
        // ① 幂等判定先于频控（修正②）：同键重放返回首成功响应快照（与首结果完全一致，
        // 不受后续处理/状态迁移影响——走查纠错：此前重放读当前行，处理过后内容会变化）
        FeedbackSubmission existing = submissionRepository.findByOwnerAndIdempotencyKey(
                submission.getOwner(), submission.getIdempotencyKey());
        if (existing != null) {
            return replayFromExisting(existing, submission.getIdempotencyDigest());
        }

        // ② 新键进入频控检查（双档计数窗；窗口边界并发轻微超限可接受）
        checkRateLimit(submission.getOwner());

        // ③ 注册表校验：必须命中启用中注册项（D6）
        if (applicationRepository.findByCode(submission.getApplicationCode()) == null
                || !applicationRepository.findByCode(submission.getApplicationCode()).isEnabled()) {
            throw new ApplicationNotAvailableException(ErrorCode.APPLICATION_NOT_AVAILABLE,
                    ErrorMessage.APPLICATION_NOT_AVAILABLE);
        }

        // ④ 领域校验（core）
        submission.validate();
        if (attachments != null && !attachments.isEmpty()) {
            List<FeedbackAttachment.UploadSpec> specs = new ArrayList<FeedbackAttachment.UploadSpec>();
            for (AttachmentInput input : attachments) {
                specs.add(FeedbackAttachment.UploadSpec.builder()
                        .filename(input.getFilename())
                        .contentType(input.getContentType())
                        .sizeBytes(input.getSizeBytes())
                        .build());
            }
            FeedbackAttachment.validateSpecs(specs);
        }

        // ⑤ 附件 S3 先传（库失败时对象不可寻址，孤儿由生命周期回收）
        List<FeedbackAttachment> storedAttachments = new ArrayList<FeedbackAttachment>();
        if (attachments != null && !attachments.isEmpty()) {
            for (AttachmentInput input : attachments) {
                String objectKey = attachmentStorageService.upload(submission.getId(), input.getContentType(),
                        input.getContent(), input.getSizeBytes(), properties.getAttachment().getBucketName());
                storedAttachments.add(FeedbackAttachment.builder()
                        .id(UUID.randomUUID().toString())
                        .submissionId(submission.getId())
                        .storageKey(objectKey)
                        .originalFilename(input.getFilename())
                        .contentType(input.getContentType())
                        .sizeBytes(input.getSizeBytes())
                        .createdTime(Instant.now())
                        .build());
            }
        }

        // ⑥ 落库：首成功响应快照随行持久化（重放依据）；并发同键由唯一键收敛——
        // 冲突侧回读既有行按摘要分流（一致=重放，不一致=409），不再裸抛 500
        submission.setFirstResponse(snapshotStore.write(submission));
        try {
            submissionRepository.insert(submission);
        } catch (org.springframework.dao.DuplicateKeyException conflict) {
            FeedbackSubmission winner = submissionRepository.findByOwnerAndIdempotencyKey(
                    submission.getOwner(), submission.getIdempotencyKey());
            if (winner == null) {
                throw conflict;
            }
            log.info("提交并发同键收敛 id={} 结果=重放既有行", winner.getId());
            return replayFromExisting(winner, submission.getIdempotencyDigest());
        }
        for (FeedbackAttachment attachment : storedAttachments) {
            attachmentRepository.insert(attachment);
        }
        log.debug("提交落库终态 id={} owner前缀=iam 状态={} 附件数={}", submission.getId(),
                submission.getStatus(), storedAttachments.size());
        return new CreateResult(submission, false);
    }

    private CreateResult replayFromExisting(FeedbackSubmission existing, String digest) {
        if (!existing.getIdempotencyDigest().equals(digest)) {
            throw new IdempotencyConflictException(ErrorCode.IDEMPOTENCY_DIGEST_MISMATCH,
                    ErrorMessage.IDEMPOTENCY_DIGEST_MISMATCH);
        }
        FeedbackSubmission replayed = existing.getFirstResponse() == null ? existing
                : snapshotStore.read(existing.getFirstResponse());
        log.debug("提交幂等重放 id={} 重放=true 快照有无={}", existing.getId(),
                existing.getFirstResponse() != null);
        return new CreateResult(replayed, true);
    }

    private void checkRateLimit(String owner) {
        SimpleFeedbackServerProperties.RateLimitConfig config = properties.getRateLimit();
        Instant now = Instant.now();
        int shortCount = submissionRepository.countByOwnerSince(owner,
                now.minusSeconds(config.getShortWindowSeconds()));
        if (shortCount >= config.getShortWindowMax()) {
            log.info("频控拒绝 维度=短窗 owner前缀=iam 计数={}", shortCount);
            throw new SubmissionRateLimitException(ErrorCode.RATE_LIMITED, ErrorMessage.RATE_LIMITED);
        }
        int longCount = submissionRepository.countByOwnerSince(owner,
                now.minusSeconds(config.getLongWindowSeconds()));
        if (longCount >= config.getLongWindowMax()) {
            log.info("频控拒绝 维度=长窗 owner前缀=iam 计数={}", longCount);
            throw new SubmissionRateLimitException(ErrorCode.RATE_LIMITED, ErrorMessage.RATE_LIMITED);
        }
    }

    /**
     * 首成功响应快照的序列化/反序列化策略（构造注入，默认基于 Jackson+JavaTime）。
     */
    public interface FirstResponseStore {
        String write(FeedbackSubmission submission);

        FeedbackSubmission read(String snapshot);
    }

    /**
     * 提交结果（幂等重放标记+落库实体）。
     */
    @lombok.Value
    public static class CreateResult {

        /**
         * 落库（或重放命中的既有）反馈
         */
        private final FeedbackSubmission submission;

        /**
         * true=幂等重放（返回与首成功一致的既有行）
         */
        private final boolean replay;
    }

    /**
     * 附件上传件（提交时与主体一起传入）。
     */
    @lombok.Value
    public static class AttachmentInput {

        /**
         * 用户侧原始文件名
         */
        private final String filename;

        /**
         * 媒体类型
         */
        private final String contentType;

        /**
         * 字节长度
         */
        private final long sizeBytes;

        /**
         * 内容流
         */
        private final InputStream content;
    }
}
