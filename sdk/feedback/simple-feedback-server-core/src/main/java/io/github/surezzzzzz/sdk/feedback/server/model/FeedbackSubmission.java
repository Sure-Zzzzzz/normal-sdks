package io.github.surezzzzzz.sdk.feedback.server.model;

import io.github.surezzzzzz.sdk.feedback.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.feedback.server.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.feedback.server.constant.FeedbackKind;
import io.github.surezzzzzz.sdk.feedback.server.constant.FeedbackStatus;
import io.github.surezzzzzz.sdk.feedback.server.constant.FeedbackType;
import io.github.surezzzzzz.sdk.feedback.server.constant.SimpleFeedbackServerConstant;
import io.github.surezzzzzz.sdk.feedback.server.exception.FeedbackStateException;
import io.github.surezzzzzz.sdk.feedback.server.exception.SubmissionValidationException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * 反馈提交聚合（feedback_submission）。
 *
 * <p>提交人快照与鉴权分离：subjectId/displayName 仅作回访展示，鉴权只看 owner 等值；
 * owner 只从认证上下文派生（人员为 iam:{subjectId}，服务主体为 aksk:{clientId}），永不出现在请求体。</p>
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackSubmission {

    /**
     * 反馈 ID（服务端生成 UUID，CHAR(36)）
     */
    private String id;

    /**
     * 目标应用编码（必须命中启用中注册项）
     */
    private String applicationCode;

    /**
     * 发起形态稳定 code（proactive/intercepted；统计提示字段，不做安全语义）
     */
    private String kind;

    /**
     * 反馈类型稳定 code（bug/feature/experience/other）
     */
    private String type;

    /**
     * 满意度打分（1-10）
     */
    private int score;

    /**
     * 反馈文本（1-2000 字符，原文存储）
     */
    private String content;

    /**
     * 联系邮箱（选填，自由输入不关联账号；RFC 格式校验）
     */
    private String contactEmail;

    /**
     * 提交人 subjectId（快照，不作鉴权依据）
     */
    private String submitterSubjectId;

    /**
     * 提交人显示名快照（可空：认证上下文有则取、adaptor 可补、均无则空，展示回退 subjectId）
     */
    private String submitterDisplayName;

    /**
     * 归属（人员为 iam:{subjectId}，服务主体为 aksk:{clientId}；只从认证上下文派生）
     */
    private String owner;

    /**
     * 发起页面路径（深链预填，提交人可改可清，≤500）
     */
    private String contextPath;

    /**
     * 环境快照 JSON 原文（≤2000，键白名单 ua/viewport/theme/locale）
     */
    private String environment;

    /**
     * 状态稳定 code（submitted/resolved/dismissed）
     */
    private String status;

    /**
     * 处理人 subjectId（最近一次处理覆盖）
     */
    private String handledBy;

    /**
     * 处理时间（UTC 可逆）
     */
    private Instant handledAt;

    /**
     * 内部处理备注（≤1000，每次处理整体覆盖）
     */
    private String handlerNote;

    /**
     * 给提交人的答复（≤1000，区别于内部 note；提交人在「我的反馈」可见）
     */
    private String reply;

    /**
     * 答复人 subjectId
     */
    private String repliedBy;

    /**
     * 答复时间（UTC 可逆）
     */
    private Instant repliedAt;

    /**
     * 行版本（乐观锁）
     */
    private int rowVersion;

    /**
     * 幂等键（浏览器表单会话 UUID；(owner, idempotency_key) 唯一）
     */
    private String idempotencyKey;

    /**
     * 幂等摘要（命令内容摘要；重放校验 owner+摘要一致，不一致 409）
     */
    private String idempotencyDigest;

    /**
     * 首成功响应快照（创建时序列化持久；重放据此返回与首结果完全一致的响应，
     * 不受后续处理/状态迁移影响——走查纠错新增）
     */
    private String firstResponse;

    /**
     * 创建时间（UTC 可逆）
     */
    private Instant createdTime;

    /**
     * 更新时间（UTC 可逆）
     */
    private Instant updatedTime;

    /**
     * 校验环境快照键集合是否全部命中白名单（JSON 解析在 server 层完成，解析出的键集传入本方法）。
     *
     * @param keys 环境快照包含的全部键
     * @throws SubmissionValidationException 含白名单外的键
     */
    public static void validateEnvironmentKeys(Iterable<String> keys) {
        for (String key : keys) {
            boolean allowed = false;
            for (String candidate : SimpleFeedbackServerConstant.ENVIRONMENT_ALLOWED_KEYS) {
                if (candidate.equals(key)) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) {
                throw new SubmissionValidationException(ErrorCode.ENVIRONMENT_KEY_REJECTED,
                        String.format(ErrorMessage.ENVIRONMENT_KEY_REJECTED,
                                Arrays.toString(SimpleFeedbackServerConstant.ENVIRONMENT_ALLOWED_KEYS)));
            }
        }
    }

    /**
     * 校验提交字段全集（分数/文本/邮箱/上下文/环境原文长度；枚举合法性）。
     *
     * @throws SubmissionValidationException 任一字段非法
     */
    public void validate() {
        if (score < SimpleFeedbackServerConstant.SCORE_MIN || score > SimpleFeedbackServerConstant.SCORE_MAX) {
            throw new SubmissionValidationException(ErrorCode.SCORE_OUT_OF_RANGE,
                    String.format(ErrorMessage.SCORE_OUT_OF_RANGE,
                            SimpleFeedbackServerConstant.SCORE_MIN, SimpleFeedbackServerConstant.SCORE_MAX));
        }
        if (content == null || content.isEmpty() || content.length() > SimpleFeedbackServerConstant.CONTENT_MAX_LENGTH) {
            throw new SubmissionValidationException(ErrorCode.CONTENT_INVALID,
                    String.format(ErrorMessage.CONTENT_INVALID, SimpleFeedbackServerConstant.CONTENT_MAX_LENGTH));
        }
        if (contactEmail != null && !contactEmail.isEmpty()) {
            if (contactEmail.length() > SimpleFeedbackServerConstant.EMAIL_MAX_LENGTH
                    || !Pattern.matches(SimpleFeedbackServerConstant.EMAIL_PATTERN, contactEmail)) {
                throw new SubmissionValidationException(ErrorCode.EMAIL_INVALID, ErrorMessage.EMAIL_INVALID);
            }
        }
        if (contextPath != null
                && contextPath.length() > SimpleFeedbackServerConstant.CONTEXT_PATH_MAX_LENGTH) {
            throw new SubmissionValidationException(ErrorCode.FIELD_TOO_LONG,
                    String.format(ErrorMessage.FIELD_TOO_LONG, "contextPath",
                            SimpleFeedbackServerConstant.CONTEXT_PATH_MAX_LENGTH));
        }
        if (environment != null
                && environment.length() > SimpleFeedbackServerConstant.ENVIRONMENT_MAX_LENGTH) {
            throw new SubmissionValidationException(ErrorCode.FIELD_TOO_LONG,
                    String.format(ErrorMessage.FIELD_TOO_LONG, "environment",
                            SimpleFeedbackServerConstant.ENVIRONMENT_MAX_LENGTH));
        }
        if (!FeedbackKind.isValid(kind)) {
            throw new SubmissionValidationException(ErrorCode.ENUM_VALUE_INVALID,
                    String.format(ErrorMessage.ENUM_VALUE_INVALID, "kind",
                            Arrays.toString(FeedbackKind.getAllCodes())));
        }
        if (!FeedbackType.isValid(type)) {
            throw new SubmissionValidationException(ErrorCode.ENUM_VALUE_INVALID,
                    String.format(ErrorMessage.ENUM_VALUE_INVALID, "type",
                            Arrays.toString(FeedbackType.getAllCodes())));
        }
        if (handlerNote != null
                && handlerNote.length() > SimpleFeedbackServerConstant.HANDLER_NOTE_MAX_LENGTH) {
            throw new SubmissionValidationException(ErrorCode.FIELD_TOO_LONG,
                    String.format(ErrorMessage.FIELD_TOO_LONG, "handlerNote",
                            SimpleFeedbackServerConstant.HANDLER_NOTE_MAX_LENGTH));
        }
        if (reply != null && reply.length() > SimpleFeedbackServerConstant.REPLY_MAX_LENGTH) {
            throw new SubmissionValidationException(ErrorCode.FIELD_TOO_LONG,
                    String.format(ErrorMessage.FIELD_TOO_LONG, "reply", SimpleFeedbackServerConstant.REPLY_MAX_LENGTH));
        }
        if (submitterDisplayName != null
                && submitterDisplayName.length() > SimpleFeedbackServerConstant.SUBMITTER_DISPLAY_NAME_MAX_LENGTH) {
            throw new SubmissionValidationException(ErrorCode.FIELD_TOO_LONG,
                    String.format(ErrorMessage.FIELD_TOO_LONG, "submitterDisplayName",
                            SimpleFeedbackServerConstant.SUBMITTER_DISPLAY_NAME_MAX_LENGTH));
        }
    }

    /**
     * 应用一次处理（状态迁移 + 处理留痕整体覆盖）。
     *
     * <p>非直观约束：note 与 reply 为覆盖式留痕（每次处理整体覆盖，非追加历史）；reply 非空时
     * 记录 repliedBy/repliedAt，供提交人「我的反馈」查看。</p>
     *
     * @param target           目标状态
     * @param handlerSubjectId 处理人 subjectId
     * @param note             内部备注（可空）
     * @param replyText        给提交人的答复（可空）
     * @param now              处理时间
     * @throws FeedbackStateException 非法状态迁移（含终态间直迁、自迁移）
     */
    public void applyHandling(FeedbackStatus target, String handlerSubjectId, String note, String replyText,
                              Instant now) {
        FeedbackStatus current = FeedbackStatus.fromCode(status);
        if (current == null || !current.canTransitionTo(target)) {
            throw new FeedbackStateException(ErrorCode.ILLEGAL_STATUS_TRANSITION,
                    String.format(ErrorMessage.ILLEGAL_STATUS_TRANSITION, status, target));
        }
        this.status = target.getCode();
        this.handledBy = handlerSubjectId;
        this.handledAt = now;
        this.handlerNote = note;
        if (replyText != null && !replyText.isEmpty()) {
            this.reply = replyText;
            this.repliedBy = handlerSubjectId;
            this.repliedAt = now;
        }
    }
}
