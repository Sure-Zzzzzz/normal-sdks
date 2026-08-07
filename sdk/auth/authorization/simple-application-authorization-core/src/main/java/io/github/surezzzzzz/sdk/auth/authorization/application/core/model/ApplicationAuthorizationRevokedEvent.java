package io.github.surezzzzzz.sdk.auth.authorization.application.core.model;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.SimpleApplicationAuthorizationConstant;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.exception.ApplicationAuthorizationException;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.support.ApplicationAuthorizationValidationHelper;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;

/**
 * 应用授权撤销事件。
 *
 * @author surezzzzzz
 */
@Getter
@ToString
@EqualsAndHashCode
public final class ApplicationAuthorizationRevokedEvent {

    /**
     * 事件标识。
     */
    private final String eventId;
    /**
     * 来源标识。
     */
    private final String sourceId;
    /**
     * 主体类型。
     */
    private final ApplicationAuthorizationSubjectType subjectType;
    /**
     * 主体标识。
     */
    private final String subjectId;
    /**
     * 应用标识。
     */
    private final String applicationCode;
    /**
     * 撤销前授权版本。
     */
    private final long previousAuthorizationVersion;
    /**
     * 发生时间。
     */
    private final Instant occurredAt;
    /**
     * 安全原因分类。
     */
    private final String reasonCategory;

    /**
     * 创建应用授权撤销事件。
     *
     * @param eventId                      事件标识
     * @param sourceId                     来源标识
     * @param subjectType                  主体类型
     * @param subjectId                    主体标识
     * @param applicationCode              应用标识
     * @param previousAuthorizationVersion 撤销前授权版本
     * @param occurredAt                   发生时间
     * @param reasonCategory               原因分类
     */
    public ApplicationAuthorizationRevokedEvent(String eventId, String sourceId,
                                                ApplicationAuthorizationSubjectType subjectType, String subjectId,
                                                String applicationCode, long previousAuthorizationVersion,
                                                Instant occurredAt, String reasonCategory) {
        if (subjectType == null || occurredAt == null) {
            throw invalid(String.format(SimpleApplicationAuthorizationConstant.DETAIL_CANNOT_BE_NULL,
                    subjectType == null ? SimpleApplicationAuthorizationConstant.FIELD_SUBJECT_TYPE
                            : SimpleApplicationAuthorizationConstant.FIELD_OCCURRED_AT));
        }
        if (previousAuthorizationVersion <= 0L) {
            throw invalid(SimpleApplicationAuthorizationConstant.DETAIL_PREVIOUS_AUTHORIZATION_VERSION_MUST_BE_POSITIVE);
        }
        this.eventId = ApplicationAuthorizationValidationHelper.requireIdentifier(eventId,
                SimpleApplicationAuthorizationConstant.FIELD_EVENT_ID);
        this.sourceId = ApplicationAuthorizationValidationHelper.requireIdentifier(sourceId,
                SimpleApplicationAuthorizationConstant.FIELD_SOURCE_ID);
        this.subjectType = subjectType;
        this.subjectId = ApplicationAuthorizationValidationHelper.requireIdentifier(subjectId,
                SimpleApplicationAuthorizationConstant.FIELD_SUBJECT_ID);
        this.applicationCode = ApplicationAuthorizationValidationHelper.requireApplicationCode(applicationCode);
        this.previousAuthorizationVersion = previousAuthorizationVersion;
        this.occurredAt = occurredAt;
        this.reasonCategory = ApplicationAuthorizationValidationHelper.requireIdentifier(reasonCategory,
                SimpleApplicationAuthorizationConstant.FIELD_REASON_CATEGORY);
    }

    private static ApplicationAuthorizationException invalid(String detail) {
        return new ApplicationAuthorizationException(ErrorCode.INVALID_REVOCATION_EVENT,
                String.format(ErrorMessage.INVALID_REVOCATION_EVENT, detail));
    }
}
