package io.github.surezzzzzz.sdk.auth.resource.core.model;

import io.github.surezzzzzz.sdk.auth.resource.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceSubjectType;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.SimpleResourceServerConstant;
import io.github.surezzzzzz.sdk.auth.resource.core.exception.ResourceAuthenticationException;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 已验证资源主体。
 *
 * @author surezzzzzz
 */
@Getter
@ToString
@EqualsAndHashCode
public final class VerifiedResourcePrincipal {

    private final ResourceAuthenticationSourceId sourceId;
    private final ResourceSubjectType subjectType;
    private final String subjectId;

    /**
     * 创建已验证资源主体。
     *
     * @param sourceId    认证来源
     * @param subjectType 主体类型
     * @param subjectId   Provider内稳定主体标识
     */
    public VerifiedResourcePrincipal(ResourceAuthenticationSourceId sourceId, ResourceSubjectType subjectType,
                                     String subjectId) {
        if (sourceId == null || subjectType == null || subjectId == null || subjectId.isEmpty()
                || Character.isWhitespace(subjectId.charAt(0))
                || Character.isWhitespace(subjectId.charAt(subjectId.length() - 1))
                || subjectId.codePointCount(0, subjectId.length())
                > SimpleResourceServerConstant.MAX_IDENTIFIER_CODE_POINT_COUNT) {
            throw new ResourceAuthenticationException(ErrorCode.INVALID_RESOURCE_AUTHENTICATION_MODEL,
                    String.format(ErrorMessage.INVALID_RESOURCE_AUTHENTICATION_MODEL,
                            SimpleResourceServerConstant.DETAIL_CANNOT_BE_NULL));
        }
        this.sourceId = sourceId;
        this.subjectType = subjectType;
        this.subjectId = subjectId;
    }
}
