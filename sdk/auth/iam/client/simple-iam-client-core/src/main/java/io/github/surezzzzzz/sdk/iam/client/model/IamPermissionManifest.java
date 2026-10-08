package io.github.surezzzzzz.sdk.iam.client.model;

import lombok.Builder;
import lombok.Value;

/**
 * 目标应用权限清单
 *
 * @author surezzzzzz
 */
@Value
@Builder
public class IamPermissionManifest {

    /**
     * 清单版本。
     */
    Long manifestVersion;

    /**
     * 清单摘要。
     */
    String manifestDigest;

    /**
     * 页面权限码。
     */
    java.util.List<String> pagePermissions;

    /**
     * API 权限码。
     */
    java.util.List<String> apiPermissions;
}
