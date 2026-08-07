package io.github.surezzzzzz.sdk.auth.resource.core.model;

import io.github.surezzzzzz.sdk.auth.resource.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.SimpleResourceServerConstant;
import io.github.surezzzzzz.sdk.auth.resource.core.exception.ResourceAuthenticationException;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 资源认证来源标识。
 *
 * @author surezzzzzz
 */
@Getter
@ToString
@EqualsAndHashCode
public final class ResourceAuthenticationSourceId {

    /**
     * 稳定来源标识。
     */
    private final String value;

    /**
     * 创建资源认证来源标识。
     *
     * @param value 稳定来源标识
     */
    public ResourceAuthenticationSourceId(String value) {
        if (value == null) {
            throw invalid(String.format(SimpleResourceServerConstant.DETAIL_CANNOT_BE_NULL, "sourceId"));
        }
        if (value.isEmpty() || Character.isWhitespace(value.charAt(0))
                || Character.isWhitespace(value.charAt(value.length() - 1))) {
            throw invalid(String.format(SimpleResourceServerConstant.DETAIL_CANNOT_BE_BLANK_OR_OUTER_WHITESPACE,
                    "sourceId"));
        }
        if (value.codePointCount(0, value.length()) > SimpleResourceServerConstant.MAX_SOURCE_ID_CODE_POINT_COUNT
                || !value.matches(SimpleResourceServerConstant.SOURCE_ID_ALLOWED_CHARACTER_PATTERN)) {
            throw invalid(String.format(SimpleResourceServerConstant.DETAIL_SOURCE_ID_INVALID, value));
        }
        this.value = value;
    }

    private static ResourceAuthenticationException invalid(String detail) {
        return new ResourceAuthenticationException(ErrorCode.INVALID_RESOURCE_AUTHENTICATION_MODEL,
                String.format(ErrorMessage.INVALID_RESOURCE_AUTHENTICATION_MODEL, detail));
    }
}
