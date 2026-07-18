package io.github.surezzzzzz.sdk.auth.data.permission.core.model;

import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.SimpleDataPermissionConstant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.support.DataPermissionValidationHelper;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 数据权限评估请求。
 *
 * @author surezzzzzz
 */
@Getter
@ToString
@EqualsAndHashCode
public final class DataPermissionRequest {

    /**
     * 资源标识。
     */
    private final String resource;
    /**
     * 动作标识。
     */
    private final String action;

    /**
     * 创建数据权限评估请求。
     *
     * @param resource 资源标识
     * @param action   动作标识
     */
    public DataPermissionRequest(String resource, String action) {
        this.resource = DataPermissionValidationHelper.requireIdentifier(resource,
                SimpleDataPermissionConstant.FIELD_RESOURCE, ErrorCode.INVALID_DOCUMENT, ErrorMessage.INVALID_DOCUMENT);
        this.action = DataPermissionValidationHelper.requireIdentifier(action,
                SimpleDataPermissionConstant.FIELD_ACTIONS, ErrorCode.INVALID_DOCUMENT, ErrorMessage.INVALID_DOCUMENT);
    }
}
