package io.github.surezzzzzz.sdk.auth.iam.server.dto.manifest.request;

import io.github.surezzzzzz.sdk.auth.iam.server.dto.manifest.DataResourceDeclaration;
import lombok.Data;

import java.util.List;

/**
 * 可信应用权限清单登记 / 全量替换请求
 *
 * <p>manifestVersion 与 manifestDigest 由服务端维护（版本单调递增、摘要按
 * 规范化内容计算），请求不携带。
 *
 * @author surezzzzzz
 */
@Data
public class PutApplicationPermissionManifestRequest {

    /**
     * 应用局部角色编码列表（必填，可为空数组）
     */
    private List<String> roles;

    /**
     * 应用页面权限编码列表（必填，可为空数组）
     */
    private List<String> pagePermissions;

    /**
     * 应用接口权限编码列表（必填，可为空数组）
     */
    private List<String> apiPermissions;

    /**
     * DATA 资源声明列表（必填，可为空数组；角色规则模板的授权范围事实源）
     */
    private List<DataResourceDeclaration> dataResources;
}
