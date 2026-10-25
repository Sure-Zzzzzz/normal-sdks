package io.github.surezzzzzz.sdk.feedback.server.model;

import io.github.surezzzzzz.sdk.feedback.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.feedback.server.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.feedback.server.constant.SimpleFeedbackServerConstant;
import io.github.surezzzzzz.sdk.feedback.server.exception.SubmissionValidationException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.regex.Pattern;

/**
 * 应用注册表聚合（feedback_application）。
 *
 * <p>应用注册表是反馈服务自有一等实体：反馈必须命中启用中的注册项才受理；
 * 不共库 IAM、不做运行时强校验依赖，编码与部署环境可信应用编码的一致性由部署管理负责。
 * 注册项可停用不可物理删除（历史引用完整性）。</p>
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackApplication {

    /**
     * 应用编码（全局唯一，创建后不可变；小写字母数字与连字符，2-64 位，首尾为字母数字）
     */
    private String applicationCode;

    /**
     * 门户展示名
     */
    private String displayName;

    /**
     * 应用说明（可选，给提交人看）
     */
    private String description;

    /**
     * 是否启用：停用=不出现在选择列表、新提交拒绝（历史不受影响）
     */
    private boolean enabled;

    /**
     * 期望处理组名（可选备注，纯提示信息，不做权限计算）
     */
    private String ownerGroup;

    /**
     * 行版本（乐观锁）
     */
    private int rowVersion;

    /**
     * 创建人 subjectId
     */
    private String createdBy;

    /**
     * 创建时间（UTC 可逆）
     */
    private Instant createdTime;

    /**
     * 更新时间（UTC 可逆）
     */
    private Instant updatedTime;

    /**
     * 校验应用编码格式。
     *
     * @param code 应用编码
     * @throws SubmissionValidationException 格式非法
     */
    public static void validateCode(String code) {
        if (code == null || !Pattern.matches(SimpleFeedbackServerConstant.APPLICATION_CODE_PATTERN, code)) {
            throw new SubmissionValidationException(ErrorCode.APPLICATION_CODE_INVALID,
                    ErrorMessage.APPLICATION_CODE_INVALID);
        }
    }

    private static void validateFieldLength(String value, String field, int maxLength) {
        if (value == null || value.isEmpty() || value.length() > maxLength) {
            throw new SubmissionValidationException(ErrorCode.FIELD_TOO_LONG,
                    String.format(ErrorMessage.FIELD_TOO_LONG, field, maxLength));
        }
    }

    /**
     * 校验登记字段全集（编码/显示名/描述）。
     *
     * @throws SubmissionValidationException 任一字段非法
     */
    public void validate() {
        validateCode(applicationCode);
        validateFieldLength(displayName, "displayName", SimpleFeedbackServerConstant.APPLICATION_DISPLAY_NAME_MAX_LENGTH);
        if (description != null) {
            validateFieldLength(description, "description", SimpleFeedbackServerConstant.APPLICATION_DESCRIPTION_MAX_LENGTH);
        }
        if (ownerGroup != null) {
            validateFieldLength(ownerGroup, "ownerGroup", SimpleFeedbackServerConstant.SUBMITTER_DISPLAY_NAME_MAX_LENGTH);
        }
    }
}
