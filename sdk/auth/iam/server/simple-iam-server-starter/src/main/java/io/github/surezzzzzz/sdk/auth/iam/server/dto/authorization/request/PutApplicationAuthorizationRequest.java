package io.github.surezzzzzz.sdk.auth.iam.server.dto.authorization.request;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 创建 / 全量替换用户应用授权请求
 *
 * <p>authorizationVersion 由服务端单调递增管理，请求不携带；dataGrantDocument
 * 为 null 表示清除数据授权（应用仅有 API 级准入），非 null 时由服务端按
 * 数据授权协议校验结构合法性。三类码须为应用权限清单子集，manifestVersion /
 * manifestDigest 由服务端按清单当前版本落库，请求不携带。
 *
 * @author surezzzzzz
 */
@Data
public class PutApplicationAuthorizationRequest {

    /**
     * 是否通过应用准入（true=准入）
     */
    private Boolean admitted;

    /**
     * 应用局部角色编码列表（可为空数组，须在应用权限清单内）
     */
    private List<String> roles;

    /**
     * 应用页面权限编码列表（可为空数组，须在应用权限清单内）
     */
    private List<String> pagePermissions;

    /**
     * 应用精确 API 权限编码列表（可为空数组，须在应用权限清单内）
     */
    private List<String> apiPermissions;

    /**
     * 数据授权文档原始结构（null 表示清除）
     */
    private Map<String, Object> dataGrantDocument;
}
